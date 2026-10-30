package com.example.devboard.service;

import com.example.devboard.dto.request.MemberSignupRequest;
import com.example.devboard.dto.request.MemberWithdrawalRequest;
import com.example.devboard.entity.Member;
import com.example.devboard.entity.Post;
import com.example.devboard.repository.*;
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
    private final PostImageRepository postImageRepository;
    private final ImageStorageService imageStorageService;
    private final PostLikeRepository postLikeRepository;

    public MemberService(MemberRepository memberRepository, PasswordEncoder passwordEncoder, JwtService jwtService,
                         PostRepository postRepository, CommentRepository commentRepository,
                         PostImageRepository postImageRepository, ImageStorageService imageStorageService, PostLikeRepository postLikeRepository) {
        this.memberRepository = memberRepository;
        this.passwordEncoder = passwordEncoder;
        this.jwtService = jwtService;
        this.postRepository = postRepository;
        this.commentRepository = commentRepository;
        this.postImageRepository = postImageRepository;
        this.imageStorageService = imageStorageService;
        this.postLikeRepository = postLikeRepository;
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

        // 로그인한 회원 조회
        Member member = memberRepository.findByEmail(email)
                .orElseThrow(() -> new IllegalArgumentException("회원을 찾을 수 없습니다."));

        // 현재 비밀번호 확인
        if (!passwordEncoder.matches(request.password(), member.getPassword())) {
            throw new BadCredentialsException("비밀번호가 올바르지 않습니다.");
        }

        Long memberId = member.getId();

        // 회원이 작성한 댓글 삭제
        commentRepository.deleteByMemberId(memberId);

        // 회원이 작성한 게시글 조회
        var posts = postRepository.findAll().stream()
                .filter(post -> post.getMember().getId().equals(memberId))
                .toList();

        // 회원이 작성한 게시글별 연관 데이터 삭제
        for (var post : posts) {

            Long postId = post.getId();

            // 게시글에 작성된 댓글 삭제
            commentRepository.deleteByPostId(postId);

            // 게시글에 등록된 좋아요 삭제
            postLikeRepository.deleteByPostId(postId);

            // 게시글 이미지 조회
            var images = postImageRepository.findByPostIdOrderByIdAsc(postId);

            // 실제 이미지 파일 삭제
            for (var image : images) {
                imageStorageService.deleteImage(image.getImagePath());
            }

            // 게시글 이미지 DB 정보 삭제
            postImageRepository.deleteByPostId(postId);
        }

        // 회원이 다른 게시글에 등록한 좋아요 삭제
        postLikeRepository.deleteByMemberId(memberId);

        // 회원이 작성한 게시글 삭제
        postRepository.deleteAll(posts);

        // 회원 삭제
        memberRepository.delete(member);
    }
}
