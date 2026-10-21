package com.example.devboard.service;

import com.example.devboard.dto.request.MemberSignupRequest;
import com.example.devboard.dto.request.MemberWithdrawalRequest;
import com.example.devboard.entity.Member;
import com.example.devboard.entity.Post;
import com.example.devboard.repository.CommentRepository;
import com.example.devboard.repository.MemberRepository;
import com.example.devboard.repository.PostRepository;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import com.example.devboard.dto.request.MemberLoginRequest;
import com.example.devboard.dto.response.MemberLoginResponse;
import org.springframework.security.authentication.BadCredentialsException;

import java.util.Optional;

@Service
public class MemberService {

    private final MemberRepository memberRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtService jwtService;
    private final PostRepository postRepository;
    private final CommentRepository commentRepository;

    public MemberService(MemberRepository memberRepository, PasswordEncoder passwordEncoder, JwtService jwtService,
                         PostRepository postRepository, CommentRepository commentRepository) {
        this.memberRepository = memberRepository;
        this.passwordEncoder = passwordEncoder;
        this.jwtService = jwtService;
        this.postRepository = postRepository;
        this.commentRepository = commentRepository;
    }

    @Transactional
    public Member signup(MemberSignupRequest request) {

        //이메일 중복 확인
        if (memberRepository.existsByEmail(request.email())) {
            throw new IllegalArgumentException("이미 사용 중인 이메일 입니다.");
        }

        //비밀번호 암호화
        String encodedPassword = passwordEncoder.encode(request.password());

        //회원 엔티티 생성
        Member member = new Member(
                request.email(),
                encodedPassword, //암호화한 값을 생성하여 저장
                request.nickname()
        );

        return memberRepository.save(member);
    }

    @Transactional
    public MemberLoginResponse login(MemberLoginRequest request) {
        Member member = memberRepository.findByEmail(request.email())
                .orElseThrow(() -> new BadCredentialsException("이메일 또는 비밀번호가 올바르지 않습니다"));

        if (!passwordEncoder.matches(
                request.password(),
                member.getPassword()
        )) {
            throw new BadCredentialsException("이메일 또는 비밀번호가 올바르지 않습니다.");
        }

        String accessToken = jwtService.generateToken(
                member.getId(),
                member.getEmail()
        );

        return new MemberLoginResponse(
                member.getId(),
                member.getEmail(),
                member.getNickname(),
                accessToken,
                "Bearer"
        );
    }

    @Transactional
    public void withdraw(String email, MemberWithdrawalRequest request) {

        //로그인한 회원 조회
        Member member = memberRepository.findByEmail(email)
                .orElseThrow(() -> new IllegalArgumentException("회원을 찾을 수 없습니다."));

        //현재 비밀번호 확인
        if (!passwordEncoder.matches(request.password(), member.getPassword())) {
            throw new org.springframework.security.authentication.BadCredentialsException("비밀번호가 올바르지 않습니다.");
        }

        Long memberId = member.getId();

        //회원이 작성한 댓글 삭제
        commentRepository.deleteByMemberId(memberId);

        //회원의 게시글에 달린 댓글을 삭제한 후 게시글 삭제
        var posts = postRepository.findAll().stream()
                .filter(post -> post.getMember().getId().equals(memberId)).toList();

        for (var post : posts) {
            commentRepository.deleteByMemberId(post.getId());
        }

        postRepository.deleteAll(postRepository.findAll().stream()
                .filter(post -> post.getMember().getId().equals(memberId)).toList());

        //회원 삭제
        memberRepository.delete(member);
    }
}
