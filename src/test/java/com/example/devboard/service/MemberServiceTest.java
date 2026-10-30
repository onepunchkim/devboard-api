package com.example.devboard.service;

import com.example.devboard.dto.request.MemberSignupRequest;
import com.example.devboard.dto.request.MemberLoginRequest;
import com.example.devboard.dto.request.MemberWithdrawalRequest;
import com.example.devboard.entity.Member;
import com.example.devboard.repository.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class MemberServiceTest {

    @Mock
    private MemberRepository memberRepository;
    @Mock
    private PasswordEncoder passwordEncoder;
    @Mock
    private JwtService jwtService;
    @Mock
    private PostRepository postRepository;
    @Mock
    private CommentRepository commentRepository;
    // 좋아요 Repository Mock
    @Mock
    private PostLikeRepository postLikeRepository;
    // 게시글 이미지 Repository Mock
    @Mock
    private PostImageRepository postImageRepository;
    // 이미지 파일 관리 서비스 Mock
    @Mock
    private ImageStorageService imageStorageService;

    @InjectMocks
    private MemberService memberService;

    private Member member;

    private final String email = "test@test.com";
    private final String password = "password123";
    private final String encodedPassword = "encodedPassword";

    @BeforeEach
    void setUp() {
        // 테스트에 사용할 회원 객체 생성
        member = new Member(email, encodedPassword, "테스트회원");
    }

    @Test //회원 가입 및 비밀번호 암호화 테스트
    void signup_success() {
        // given
        MemberSignupRequest request =
                new MemberSignupRequest(email, password, "테스트회원");

        when(memberRepository.existsByEmail(email)).thenReturn(false);
        when(passwordEncoder.encode(password)).thenReturn(encodedPassword);
        when(memberRepository.save(any(Member.class))).thenReturn(member);

        // when
        Member result = memberService.signup(request);

        // then
        assertNotNull(result);
        assertEquals(email, result.getEmail());

        // 비밀번호 암호화 및 회원 저장 확인
        verify(passwordEncoder).encode(password);
        verify(memberRepository).save(any(Member.class));
    }

    @Test //중복 이메일 차단 테스트
    void signup_duplicateEmail_throwsException() {
        // given
        MemberSignupRequest request =
                new MemberSignupRequest(email, password, "테스트회원");

        when(memberRepository.existsByEmail(email)).thenReturn(true);

        // when & then
        assertThrows(
                IllegalArgumentException.class,
                () -> memberService.signup(request)
        );

        // 중복 이메일이면 비밀번호 암호화와 저장을 수행하지 않아야 함
        verify(passwordEncoder, never()).encode(anyString());
        verify(memberRepository, never()).save(any(Member.class));
    }

    @Test //로그인 및 JWT 발급 테스트
    void login_success() {
        // given
        MemberLoginRequest request =
                new MemberLoginRequest(email, password);

        when(memberRepository.findByEmail(email)).thenReturn(Optional.of(member));
        when(passwordEncoder.matches(password, encodedPassword)).thenReturn(true);
        when(jwtService.generateToken(member.getId(), member.getEmail()))
                .thenReturn("test-access-token");

        // when
        var result = memberService.login(request);

        // then
        assertNotNull(result);
        assertEquals(email, result.email());
        assertEquals("테스트회원", result.nickname());
        assertEquals("test-access-token", result.accessToken());
        assertEquals("Bearer", result.tokenType());

        verify(jwtService).generateToken(member.getId(), email);
    }

    @Test //존재하지 않는 회원 로그인 차단 테스트
    void login_memberNotFound_throwsException() {
        // given
        MemberLoginRequest request =
                new MemberLoginRequest(email, password);

        when(memberRepository.findByEmail(email)).thenReturn(Optional.empty());

        // when & then
        assertThrows(
                BadCredentialsException.class,
                () -> memberService.login(request)
        );

        verify(passwordEncoder, never()).matches(anyString(), anyString());
        verify(jwtService, never()).generateToken(anyLong(), anyString());
    }

    @Test //잘못된 비밀번호 차단 테스트
    void login_wrongPassword_throwsException() {
        // given
        MemberLoginRequest request =
                new MemberLoginRequest(email, "wrongPassword");

        when(memberRepository.findByEmail(email)).thenReturn(Optional.of(member));
        when(passwordEncoder.matches("wrongPassword", encodedPassword))
                .thenReturn(false);

        // when & then
        assertThrows(
                BadCredentialsException.class,
                () -> memberService.login(request)
        );

        // 비밀번호가 틀리면 JWT를 발급하지 않아야 함
        verify(jwtService, never()).generateToken(anyLong(), anyString());
    }

    @Test //회원 탈퇴 테스트
    void withdraw_success() {
        // given
        Long memberId = 1L;

        // 회원 객체를 Mock으로 생성
        Member withdrawalMember = mock(Member.class);

        when(withdrawalMember.getId()).thenReturn(memberId);
        when(withdrawalMember.getPassword()).thenReturn(encodedPassword);

        // 회원 조회 및 비밀번호 검증 설정
        when(memberRepository.findByEmail(email))
                .thenReturn(Optional.of(withdrawalMember));

        when(passwordEncoder.matches(password, encodedPassword))
                .thenReturn(true);

        // 회원이 작성한 게시글이 없는 상황
        when(postRepository.findAll()).thenReturn(List.of());

        MemberWithdrawalRequest request =
                new MemberWithdrawalRequest(password);

        // when
        memberService.withdraw(email, request);

        // then

        // 회원이 작성한 댓글 삭제 확인
        verify(commentRepository).deleteByMemberId(memberId);

        // 회원이 누른 좋아요 삭제 확인
        verify(postLikeRepository).deleteByMemberId(memberId);

        // 게시글 삭제 확인
        verify(postRepository).deleteAll(List.of());

        // 회원 삭제 확인
        verify(memberRepository).delete(withdrawalMember);
    }

    @Test //잘못된 비밀번호 테스트
    void withdraw_wrongPassword_throwsException() {
        // given
        Member withdrawalMember = mock(Member.class);

        when(withdrawalMember.getPassword()).thenReturn(encodedPassword);

        when(memberRepository.findByEmail(email))
                .thenReturn(Optional.of(withdrawalMember));

        when(passwordEncoder.matches("wrongPassword", encodedPassword))
                .thenReturn(false);

        MemberWithdrawalRequest request =
                new MemberWithdrawalRequest("wrongPassword");

        // when & then
        assertThrows(
                BadCredentialsException.class,
                () -> memberService.withdraw(email, request)
        );

        // 비밀번호가 틀리면 데이터 삭제가 실행되면 안 됨
        verify(commentRepository, never()).deleteByMemberId(anyLong());
        verify(postLikeRepository, never()).deleteByMemberId(anyLong());
        verify(postRepository, never()).deleteAll(anyList());
        verify(memberRepository, never()).delete(any(Member.class));
    }

    @Test //존재하지 않는 회원 테스트
    void withdraw_memberNotFound_throwsException() {
        // given
        when(memberRepository.findByEmail(email))
                .thenReturn(Optional.empty());

        MemberWithdrawalRequest request =
                new MemberWithdrawalRequest(password);

        // when & then
        assertThrows(
                IllegalArgumentException.class,
                () -> memberService.withdraw(email, request)
        );

        // 회원이 없으면 비밀번호 확인이나 삭제가 실행되지 않아야 함
        verify(passwordEncoder, never()).matches(anyString(), anyString());
        verify(memberRepository, never()).delete(any(Member.class));
    }
}
