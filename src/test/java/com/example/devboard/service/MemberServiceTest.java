package com.example.devboard.service;

import com.example.devboard.dto.request.MemberSignupRequest;
import com.example.devboard.dto.request.MemberLoginRequest;
import com.example.devboard.entity.Member;
import com.example.devboard.repository.CommentRepository;
import com.example.devboard.repository.MemberRepository;
import com.example.devboard.repository.PostRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.crypto.password.PasswordEncoder;

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

    @Test
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

    @Test
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

    @Test
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

    @Test
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

    @Test
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
}
