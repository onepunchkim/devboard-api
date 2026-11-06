package com.example.devboard.config;

import com.example.devboard.entity.Member;
import com.example.devboard.repository.MemberRepository;
import com.example.devboard.service.JwtService;
import io.jsonwebtoken.Claims;
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

import jakarta.servlet.FilterChain;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class JwtAuthenticationFilterTest {

    @Mock
    private JwtService jwtService;

    @Mock
    private MemberRepository memberRepository;

    @Mock
    private FilterChain filterChain;

    @InjectMocks
    private JwtAuthenticationFilter jwtAuthenticationFilter;

    private MockHttpServletRequest request;
    private MockHttpServletResponse response;

    @BeforeEach
    void setUp() {
        // 테스트 간 인증 정보가 공유되지 않도록 초기화
        SecurityContextHolder.clearContext();

        request = new MockHttpServletRequest();
        response = new MockHttpServletResponse();
    }

    @AfterEach
    void tearDown() {
        // 다음 테스트에 인증 정보가 남지 않도록 정리
        SecurityContextHolder.clearContext();
    }

    // JWT Claims 생성
    private Claims claims(String memberId, String email) {
        Claims claims = mock(Claims.class);

        when(claims.getSubject()).thenReturn(memberId);
        when(claims.get("email", String.class)).thenReturn(email);

        return claims;
    }

    // 1. Authorization 헤더가 없으면 다음 필터로 전달
    @Test
    void noAuthorizationHeader_shouldContinueFilterChain() throws Exception {

        jwtAuthenticationFilter.doFilter(
                request, response, filterChain
        );

        verify(filterChain).doFilter(request, response);
        verifyNoInteractions(jwtService, memberRepository);

        assertNull(SecurityContextHolder.getContext().getAuthentication());
    }

    // 2. Bearer 형식이 아니면 다음 필터로 전달
    @Test
    void invalidAuthorizationPrefix_shouldContinueFilterChain()
            throws Exception {

        request.addHeader("Authorization", "Basic test-token");

        jwtAuthenticationFilter.doFilter(
                request, response, filterChain
        );

        verify(filterChain).doFilter(request, response);
        verifyNoInteractions(jwtService, memberRepository);
    }

    // 3. 정상 JWT 인증 성공
    @Test
    void validToken_shouldAuthenticateMember() throws Exception {

        request.addHeader("Authorization", "Bearer valid-token");

        Claims claims = claims("1", "test@test.com");

        Member member = mock(Member.class);

        when(jwtService.extractClaims("valid-token"))
                .thenReturn(claims);

        when(memberRepository.findById(1L))
                .thenReturn(Optional.of(member));

        when(member.getId()).thenReturn(1L);
        when(member.getEmail()).thenReturn("test@test.com");

        jwtAuthenticationFilter.doFilter(
                request, response, filterChain
        );

        Authentication authentication =
                SecurityContextHolder.getContext().getAuthentication();

        assertNotNull(authentication);
        assertEquals("test@test.com", authentication.getName());
        assertEquals(1L, authentication.getDetails());

        // ROLE_USER 권한 확인
        assertTrue(authentication.getAuthorities().stream()
                .anyMatch(authority ->
                        authority.getAuthority().equals("ROLE_USER")));

        verify(filterChain).doFilter(request, response);
        assertEquals(200, response.getStatus());
    }

    // 4. 만료되었거나 잘못된 JWT는 401
    @Test
    void invalidToken_shouldReturn401() throws Exception {

        request.addHeader("Authorization", "Bearer invalid-token");

        when(jwtService.extractClaims("invalid-token"))
                .thenThrow(new IllegalArgumentException("유효하지 않은 토큰"));

        jwtAuthenticationFilter.doFilter(
                request, response, filterChain
        );

        assertUnauthorized();
    }

    // 5. 회원 ID가 없는 토큰은 401
    @Test
    void missingMemberId_shouldReturn401() throws Exception {

        request.addHeader("Authorization", "Bearer missing-id");

        Claims claims = claims(null, "test@test.com");

        when(jwtService.extractClaims("missing-id"))
                .thenReturn(claims);

        jwtAuthenticationFilter.doFilter(
                request, response, filterChain
        );

        assertUnauthorized();
    }

    // 6. 이메일이 없는 토큰은 401
    @Test
    void missingEmail_shouldReturn401() throws Exception {

        request.addHeader("Authorization", "Bearer missing-email");

        Claims claims = claims("1", null);

        when(jwtService.extractClaims("missing-email"))
                .thenReturn(claims);

        jwtAuthenticationFilter.doFilter(
                request, response, filterChain
        );

        assertUnauthorized();
    }

    // 7. 회원 ID가 숫자가 아니면 401
    @Test
    void invalidMemberId_shouldReturn401() throws Exception {

        request.addHeader("Authorization", "Bearer invalid-id");

        Claims claims = claims("abc", "test@test.com");

        when(jwtService.extractClaims("invalid-id"))
                .thenReturn(claims);

        jwtAuthenticationFilter.doFilter(
                request, response, filterChain
        );

        assertUnauthorized();
    }

    // 8. 탈퇴한 회원의 JWT는 401
    @Test
    void deletedMember_shouldReturn401() throws Exception {

        request.addHeader("Authorization", "Bearer deleted-member");

        Claims claims = claims("1", "test@test.com");

        when(jwtService.extractClaims("deleted-member"))
                .thenReturn(claims);

        when(memberRepository.findById(1L))
                .thenReturn(Optional.empty());

        jwtAuthenticationFilter.doFilter(
                request, response, filterChain
        );

        assertUnauthorized();
    }

    // 9. JWT 이메일과 DB 이메일이 다르면 401
    @Test
    void emailMismatch_shouldReturn401() throws Exception {

        request.addHeader("Authorization", "Bearer mismatch-token");

        Claims claims = claims("1", "wrong@test.com");

        Member member = mock(Member.class);

        when(jwtService.extractClaims("mismatch-token"))
                .thenReturn(claims);

        when(memberRepository.findById(1L))
                .thenReturn(Optional.of(member));

        when(member.getEmail()).thenReturn("real@test.com");

        jwtAuthenticationFilter.doFilter(
                request, response, filterChain
        );

        assertUnauthorized();
    }

    // 인증 실패 시 공통 검증
    private void assertUnauthorized() throws Exception {

        assertEquals(401, response.getStatus());

        assertTrue(response.getContentAsString()
                .contains("유효하지 않거나 만료된 토큰입니다."));

        // 실패한 요청은 인증 정보를 저장하지 않아야 함
        assertNull(SecurityContextHolder.getContext().getAuthentication());

        // 인증 실패 시 다음 필터로 진행하지 않아야 함
        verifyNoInteractions(filterChain);
    }
}
