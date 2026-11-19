package com.example.devboard.config;

import com.example.devboard.entity.Member;
import com.example.devboard.repository.MemberRepository;
import com.example.devboard.service.JwtService;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.JwtException;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.List;

public class JwtAuthenticationFilter extends OncePerRequestFilter {

    private final JwtService jwtService;
    private final MemberRepository memberRepository;

    // =====================================================
    // 1. 생성자 주입
    // =====================================================

    public JwtAuthenticationFilter(
            JwtService jwtService,
            MemberRepository memberRepository
    ) {
        this.jwtService = jwtService;
        this.memberRepository = memberRepository;
    }

    // =====================================================
    // 2. JWT 인증 필터
    // =====================================================

    @Override
    protected void doFilterInternal(
            HttpServletRequest request,
            HttpServletResponse response,
            FilterChain filterChain
    ) throws ServletException, IOException {

        // Authorization 요청 헤더 조회
        String authorization = request.getHeader("Authorization");

        // Authorization 헤더가 없는 경우:
        // 인증을 설정하지 않고 다음 필터로 전달
        // 보호된 API라면 Spring Security가 401을 반환
        if (authorization == null || authorization.isBlank()) {
            filterChain.doFilter(request, response);
            return;
        }

        // Bearer 형식이 아닌 인증 헤더는 거부
        if (!authorization.startsWith("Bearer ")) {
            sendUnauthorizedResponse(response);
            return;
        }

        // Bearer 접두사를 제외한 실제 JWT 추출
        String token = authorization.substring(7).trim();

        // 토큰 문자열이 비어 있으면 인증 실패
        if (token.isEmpty()) {
            sendUnauthorizedResponse(response);
            return;
        }

        try {

            // =====================================================
            // 3. JWT 서명 및 만료 시간 검증
            // =====================================================

            Claims claims = jwtService.extractClaims(token);

            // JWT subject: 회원 ID
            String memberId = claims.getSubject();

            // JWT email claim: 회원 이메일
            String email = claims.get("email", String.class);

            // 회원 정보가 누락된 토큰은 거부
            if (memberId == null || memberId.isBlank()
                    || email == null || email.isBlank()) {

                throw new IllegalArgumentException(
                        "JWT에 회원 정보가 없습니다."
                );
            }

            // =====================================================
            // 4. 회원 ID 변환
            // =====================================================

            // JWT subject를 Long 타입 회원 ID로 변환
            Long id = Long.valueOf(memberId);

            // =====================================================
            // 5. 실제 회원 존재 여부 확인
            // =====================================================

            // DB에서 회원을 조회하여 탈퇴한 회원의 JWT 차단
            Member member = memberRepository.findById(id)
                    .orElse(null);

            if (member == null) {
                throw new IllegalArgumentException(
                        "존재하지 않거나 탈퇴한 회원입니다."
                );
            }

            // =====================================================
            // 6. JWT 이메일과 DB 이메일 비교
            // =====================================================

            // JWT에 저장된 이메일과 실제 회원 이메일이 다르면 거부
            if (!member.getEmail().equals(email)) {
                throw new IllegalArgumentException(
                        "JWT 회원 정보가 일치하지 않습니다."
                );
            }

            // =====================================================
            // 7. 인증 객체 생성
            // =====================================================

            UsernamePasswordAuthenticationToken authentication =
                    new UsernamePasswordAuthenticationToken(
                            member.getEmail(),
                            null,
                            List.of(
                                    new SimpleGrantedAuthority("ROLE_USER")
                            )
                    );

            // 인증 객체에 회원 ID 저장
            authentication.setDetails(member.getId());

            // Spring Security 인증 정보 등록
            SecurityContextHolder.getContext()
                    .setAuthentication(authentication);

        } catch (JwtException | IllegalArgumentException e) {

            // JWT 검증 실패 또는 회원 정보 불일치 시
            // 기존 인증 정보를 제거
            SecurityContextHolder.clearContext();

            // 인증 실패 응답 반환
            sendUnauthorizedResponse(response);
            return;
        }

        // 인증 성공 시 다음 필터로 전달
        filterChain.doFilter(request, response);
    }

    // =====================================================
    // 8. JWT 인증 실패 응답
    // =====================================================

    private void sendUnauthorizedResponse(
            HttpServletResponse response
    ) throws IOException {

        // 인증 정보 제거
        SecurityContextHolder.clearContext();

        // HTTP 401 Unauthorized
        response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);

        // JSON 응답 설정
        response.setContentType("application/json;charset=UTF-8");

        // 클라이언트에 공통 인증 실패 메시지 전달
        response.getWriter().write(
                """
                {
                    "status": 401,
                    "message": "유효하지 않거나 만료된 토큰입니다."
                }
                """
        );
    }
}
