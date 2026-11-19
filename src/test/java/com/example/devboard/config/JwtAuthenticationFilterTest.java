package com.example.devboard.config;

import com.example.devboard.entity.Member;
import com.example.devboard.repository.MemberRepository;
import com.example.devboard.service.JwtService;

import io.jsonwebtoken.Claims;

import jakarta.servlet.FilterChain;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;

import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class JwtAuthenticationFilterTest {

    // JWT 서비스 Mock
    @Mock
    private JwtService jwtService;

    // 회원 Repository Mock
    @Mock
    private MemberRepository memberRepository;

    // 다음 필터 Mock
    @Mock
    private FilterChain filterChain;

    // 테스트 대상 JWT 인증 필터
    @InjectMocks
    private JwtAuthenticationFilter jwtAuthenticationFilter;

    private MockHttpServletRequest request;
    private MockHttpServletResponse response;

    // =====================================================
    // 테스트 실행 전 초기화
    // =====================================================

    @BeforeEach
    void setUp() {

        // 테스트 간 인증 정보 공유 방지
        SecurityContextHolder.clearContext();

        request = new MockHttpServletRequest();
        response = new MockHttpServletResponse();
    }

    @AfterEach
    void tearDown() {

        // 테스트 종료 후 인증 정보 제거
        SecurityContextHolder.clearContext();
    }

    // =====================================================
    // 공통 Claims 생성 메서드
    // =====================================================

    private Claims claims(String memberId, String email) {

        Claims claims = mock(Claims.class);

        when(claims.getSubject()).thenReturn(memberId);

        // email이 null인 테스트에서는 불필요한 스텁을 피함
        if (email != null) {
            when(claims.get("email", String.class))
                    .thenReturn(email);
        }

        return claims;
    }

    // =====================================================
    // 1. Authorization 헤더가 없으면 다음 필터로 전달
    // =====================================================

    @Test
    void noAuthorizationHeader_shouldContinueFilterChain()
            throws Exception {

        // when
        jwtAuthenticationFilter.doFilter(
                request,
                response,
                filterChain
        );

        // then
        verify(filterChain).doFilter(request, response);

        verifyNoInteractions(jwtService, memberRepository);

        assertNull(
                SecurityContextHolder.getContext()
                        .getAuthentication()
        );
    }

    // =====================================================
    // 2. Bearer 형식이 아니면 401 반환
    // =====================================================

    @Test
    void invalidAuthorizationPrefix_shouldReturn401()
            throws Exception {

        // given: 잘못된 인증 헤더
        request.addHeader(
                "Authorization",
                "Basic test-token"
        );

        // when
        jwtAuthenticationFilter.doFilter(
                request,
                response,
                filterChain
        );

        // then
        assertUnauthorized();

        // JWT 검증 서비스는 호출되지 않아야 함
        verifyNoInteractions(jwtService, memberRepository);
    }

    // =====================================================
    // 3. 정상 JWT 인증 성공
    // =====================================================

    @Test
    void validToken_shouldAuthenticateMember()
            throws Exception {

        // given
        request.addHeader(
                "Authorization",
                "Bearer valid-token"
        );

        Claims claims = claims("1", "test@test.com");

        Member member = mock(Member.class);

        when(jwtService.extractClaims("valid-token"))
                .thenReturn(claims);

        when(memberRepository.findById(1L))
                .thenReturn(Optional.of(member));

        when(member.getId()).thenReturn(1L);

        when(member.getEmail())
                .thenReturn("test@test.com");

        // when
        jwtAuthenticationFilter.doFilter(
                request,
                response,
                filterChain
        );

        // then: 인증 정보 확인
        Authentication authentication =
                SecurityContextHolder.getContext()
                        .getAuthentication();

        assertNotNull(authentication);

        assertEquals(
                "test@test.com",
                authentication.getName()
        );

        assertEquals(
                1L,
                authentication.getDetails()
        );

        // ROLE_USER 권한 확인
        assertTrue(
                authentication.getAuthorities()
                        .stream()
                        .anyMatch(authority ->
                                authority.getAuthority()
                                        .equals("ROLE_USER"))
        );

        // 인증 성공 시 다음 필터 실행
        verify(filterChain).doFilter(request, response);

        // Mock 응답의 기본 상태는 200
        assertEquals(200, response.getStatus());
    }

    // =====================================================
    // 4. 잘못된 JWT는 401
    // =====================================================

    @Test
    void invalidToken_shouldReturn401()
            throws Exception {

        // given
        request.addHeader(
                "Authorization",
                "Bearer invalid-token"
        );

        when(jwtService.extractClaims("invalid-token"))
                .thenThrow(
                        new IllegalArgumentException("유효하지 않은 토큰")
                );

        // when
        jwtAuthenticationFilter.doFilter(
                request,
                response,
                filterChain
        );

        // then
        assertUnauthorized();
    }

    // =====================================================
    // 5. 회원 ID가 없는 토큰은 401
    // =====================================================

    @Test
    void missingMemberId_shouldReturn401()
            throws Exception {

        // given
        request.addHeader(
                "Authorization",
                "Bearer missing-id"
        );

        Claims claims = claims(null, "test@test.com");

        when(jwtService.extractClaims("missing-id"))
                .thenReturn(claims);

        // when
        jwtAuthenticationFilter.doFilter(
                request,
                response,
                filterChain
        );

        // then
        assertUnauthorized();
    }

    // =====================================================
    // 6. 이메일이 없는 토큰은 401
    // =====================================================

    @Test
    void missingEmail_shouldReturn401()
            throws Exception {

        // given
        request.addHeader(
                "Authorization",
                "Bearer missing-email"
        );

        Claims claims = claims("1", null);

        when(jwtService.extractClaims("missing-email"))
                .thenReturn(claims);

        // when
        jwtAuthenticationFilter.doFilter(
                request,
                response,
                filterChain
        );

        // then
        assertUnauthorized();
    }

    // =====================================================
    // 7. 회원 ID가 숫자가 아니면 401
    // =====================================================

    @Test
    void invalidMemberId_shouldReturn401()
            throws Exception {

        // given
        request.addHeader(
                "Authorization",
                "Bearer invalid-id"
        );

        Claims claims = claims("abc", "test@test.com");

        when(jwtService.extractClaims("invalid-id"))
                .thenReturn(claims);

        // when
        jwtAuthenticationFilter.doFilter(
                request,
                response,
                filterChain
        );

        // then
        assertUnauthorized();
    }

    // =====================================================
    // 8. 탈퇴한 회원의 JWT는 401
    // =====================================================

    @Test
    void deletedMember_shouldReturn401()
            throws Exception {

        // given
        request.addHeader(
                "Authorization",
                "Bearer deleted-member"
        );

        Claims claims = claims("1", "test@test.com");

        when(jwtService.extractClaims("deleted-member"))
                .thenReturn(claims);

        // DB에 회원이 존재하지 않음
        when(memberRepository.findById(1L))
                .thenReturn(Optional.empty());

        // when
        jwtAuthenticationFilter.doFilter(
                request,
                response,
                filterChain
        );

        // then
        assertUnauthorized();

        verify(memberRepository).findById(1L);
    }

    // =====================================================
    // 9. JWT 이메일과 DB 이메일이 다르면 401
    // =====================================================

    @Test
    void emailMismatch_shouldReturn401()
            throws Exception {

        // given
        request.addHeader(
                "Authorization",
                "Bearer mismatch-token"
        );

        Claims claims = claims("1", "wrong@test.com");

        Member member = mock(Member.class);

        when(jwtService.extractClaims("mismatch-token"))
                .thenReturn(claims);

        when(memberRepository.findById(1L))
                .thenReturn(Optional.of(member));

        when(member.getEmail())
                .thenReturn("real@test.com");

        // when
        jwtAuthenticationFilter.doFilter(
                request,
                response,
                filterChain
        );

        // then
        assertUnauthorized();
    }

    // =====================================================
    // 10. 빈 Bearer 토큰은 401
    // =====================================================

    @Test
    void emptyBearerToken_shouldReturn401()
            throws Exception {

        // given
        request.addHeader(
                "Authorization",
                "Bearer "
        );

        // when
        jwtAuthenticationFilter.doFilter(
                request,
                response,
                filterChain
        );

        // then
        assertUnauthorized();

        verifyNoInteractions(jwtService, memberRepository);
    }

    // =====================================================
    // 11. 빈 Authorization 헤더는 다음 필터로 전달
    // =====================================================

    @Test
    void blankAuthorizationHeader_shouldContinueFilterChain()
            throws Exception {

        // given
        request.addHeader(
                "Authorization",
                "   "
        );

        // when
        jwtAuthenticationFilter.doFilter(
                request,
                response,
                filterChain
        );

        // then
        verify(filterChain).doFilter(request, response);

        verifyNoInteractions(jwtService, memberRepository);
    }

    // =====================================================
    // 인증 실패 공통 검증
    // =====================================================

    private void assertUnauthorized() throws Exception {

        // HTTP 401 확인
        assertEquals(401, response.getStatus());

        // 공통 오류 메시지 확인
        assertTrue(
                response.getContentAsString()
                        .contains("유효하지 않거나 만료된 토큰입니다.")
        );

        // 인증 정보가 저장되지 않았는지 확인
        assertNull(
                SecurityContextHolder.getContext()
                        .getAuthentication()
        );

        // 인증 실패 시 다음 필터로 진행하지 않아야 함
        verifyNoInteractions(filterChain);
    }
}
