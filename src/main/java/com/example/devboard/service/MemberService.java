package com.example.devboard.service;

import com.example.devboard.dto.request.MemberLoginRequest;
import com.example.devboard.dto.request.MemberSignupRequest;
import com.example.devboard.dto.request.MemberWithdrawalRequest;
import com.example.devboard.dto.response.MemberLoginResponse;

import com.example.devboard.entity.Member;
import com.example.devboard.entity.Post;

import com.example.devboard.exception.BusinessException;
import com.example.devboard.exception.ErrorCode;

import com.example.devboard.repository.CommentRepository;
import com.example.devboard.repository.MemberRepository;
import com.example.devboard.repository.PostImageRepository;
import com.example.devboard.repository.PostLikeRepository;
import com.example.devboard.repository.PostRepository;

import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
public class MemberService {

    // =====================================================
    // 1. 의존성
    // =====================================================

    private final MemberRepository memberRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtService jwtService;

    private final PostRepository postRepository;
    private final CommentRepository commentRepository;
    private final PostImageRepository postImageRepository;
    private final ImageStorageService imageStorageService;
    private final PostLikeRepository postLikeRepository;

    // =====================================================
    // 2. 생성자 주입
    // =====================================================

    public MemberService(
            MemberRepository memberRepository,
            PasswordEncoder passwordEncoder,
            JwtService jwtService,
            PostRepository postRepository,
            CommentRepository commentRepository,
            PostImageRepository postImageRepository,
            ImageStorageService imageStorageService,
            PostLikeRepository postLikeRepository
    ) {
        this.memberRepository = memberRepository;
        this.passwordEncoder = passwordEncoder;
        this.jwtService = jwtService;
        this.postRepository = postRepository;
        this.commentRepository = commentRepository;
        this.postImageRepository = postImageRepository;
        this.imageStorageService = imageStorageService;
        this.postLikeRepository = postLikeRepository;
    }

    // =====================================================
    // 3. 회원가입
    // =====================================================

    @Transactional
    public Member signup(MemberSignupRequest request) {

        // 이미 사용 중인 이메일인지 확인
        if (memberRepository.existsByEmail(request.email())) {

            // 중복 이메일은 409 Conflict로 처리
            throw new BusinessException(
                    ErrorCode.DUPLICATE_EMAIL
            );
        }

        // 회원 비밀번호를 BCrypt 등 설정된 인코더로 암호화
        String encodedPassword = passwordEncoder.encode(
                request.password()
        );

        // 회원 엔티티 생성
        Member member = new Member(
                request.email(),
                encodedPassword,
                request.nickname()
        );

        // 회원 정보를 DB에 저장하고 결과 반환
        return memberRepository.save(member);
    }

    // =====================================================
    // 4. 로그인
    // =====================================================

    @Transactional(readOnly = true)
    public MemberLoginResponse login(MemberLoginRequest request) {

        // 이메일로 회원 조회
        // 회원이 없더라도 구체적인 존재 여부는 노출하지 않음
        Member member = memberRepository.findByEmail(request.email())
                .orElseThrow(() ->
                        new BusinessException(
                                ErrorCode.INVALID_CREDENTIALS
                        )
                );

        // 입력한 비밀번호와 암호화된 비밀번호 비교
        if (!passwordEncoder.matches(
                request.password(),
                member.getPassword()
        )) {

            // 비밀번호가 올바르지 않으면 401 Unauthorized
            throw new BusinessException(
                    ErrorCode.INVALID_CREDENTIALS
            );
        }

        // 회원 ID와 이메일을 이용해 JWT 생성
        String accessToken = jwtService.generateToken(
                member.getId(),
                member.getEmail()
        );

        // 로그인 성공 응답 반환
        return new MemberLoginResponse(
                member.getId(),
                member.getEmail(),
                member.getNickname(),
                accessToken,
                "Bearer"
        );
    }

    // =====================================================
    // 5. 회원 탈퇴
    // =====================================================

    @Transactional
    public void withdraw(
            String email,
            MemberWithdrawalRequest request
    ) {

        // 현재 로그인한 회원 조회
        Member member = memberRepository.findByEmail(email)
                .orElseThrow(() ->
                        new BusinessException(
                                ErrorCode.MEMBER_NOT_FOUND
                        )
                );

        // 현재 비밀번호 검증
        if (!passwordEncoder.matches(
                request.password(),
                member.getPassword()
        )) {

            // 비밀번호 불일치는 401 Unauthorized
            throw new BusinessException(
                    ErrorCode.INVALID_CREDENTIALS
            );
        }

        // 탈퇴할 회원의 ID
        Long memberId = member.getId();

        // -------------------------------------------------
        // 5-1. 회원이 작성한 댓글 삭제
        // -------------------------------------------------

        commentRepository.deleteByMemberId(memberId);

        // -------------------------------------------------
        // 5-2. 회원이 작성한 게시글 조회
        // -------------------------------------------------

        List<Post> posts = postRepository.findAll()
                .stream()
                .filter(post ->
                        post.getMember().getId().equals(memberId)
                )
                .toList();

        // -------------------------------------------------
        // 5-3. 게시글별 연관 데이터 삭제
        // -------------------------------------------------

        for (Post post : posts) {

            Long postId = post.getId();

            // 해당 게시글에 작성된 댓글 삭제
            commentRepository.deleteByPostId(postId);

            // 해당 게시글에 등록된 좋아요 삭제
            postLikeRepository.deleteByPostId(postId);

            // 게시글 이미지 조회
            var images = postImageRepository
                    .findByPostIdOrderByIdAsc(postId);

            // 실제 저장소의 이미지 파일 삭제
            for (var image : images) {
                imageStorageService.deleteImage(
                        image.getImagePath()
                );
            }

            // 게시글 이미지 DB 정보 삭제
            postImageRepository.deleteByPostId(postId);
        }

        // -------------------------------------------------
        // 5-4. 다른 게시글에 남긴 좋아요 삭제
        // -------------------------------------------------

        postLikeRepository.deleteByMemberId(memberId);

        // -------------------------------------------------
        // 5-5. 회원이 작성한 게시글 삭제
        // -------------------------------------------------

        postRepository.deleteAll(posts);

        // -------------------------------------------------
        // 5-6. 회원 계정 삭제
        // -------------------------------------------------

        memberRepository.delete(member);
    }
}
