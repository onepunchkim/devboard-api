
package com.example.devboard.config;

import com.example.devboard.entity.Member;
import com.example.devboard.repository.MemberRepository;
import com.example.devboard.service.JwtService;
import io.jsonwebtoken.Claims;
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

    public JwtAuthenticationFilter(
            JwtService jwtService,
            MemberRepository memberRepository
    ) {
        this.jwtService = jwtService;
        this.memberRepository = memberRepository;
    }

    @Override
    protected void doFilterInternal(
            HttpServletRequest request,
            HttpServletResponse response,
            FilterChain filterChain
    ) throws ServletException, IOException {

        String authorization = request.getHeader("Authorization");

        if (authorization != null && authorization.startsWith("Bearer ")) {
            String token = authorization.substring(7);

            try {
                // 1. JWT 서명과 만료 시간 검증
                Claims claims = jwtService.extractClaims(token);

                // 2. 토큰에서 회원 ID와 이메일 추출
                String memberId = claims.getSubject();
                String email = claims.get("email", String.class);

                if (memberId == null || email == null) {
                    throw new IllegalArgumentException("토큰에 회원 정보가 없습니다.");
                }

                // 3. DB에서 실제 회원 조회
                Long id = Long.valueOf(memberId);

                Member member = memberRepository.findById(id)
                        .orElseThrow(() ->
                                new IllegalArgumentException("탈퇴했거나 존재하지 않는 회원입니다.")
                        );

                // 4. 토큰의 이메일과 DB의 이메일 비교
                if (!member.getEmail().equals(email)) {
                    throw new IllegalArgumentException("토큰의 회원 정보가 일치하지 않습니다.");
                }

                // 5. 검증을 모두 통과한 경우에만 인증 설정
                UsernamePasswordAuthenticationToken authentication =
                        new UsernamePasswordAuthenticationToken(
                                member.getEmail(),
                                null,
                                List.of(new SimpleGrantedAuthority("ROLE_USER"))
                        );

                authentication.setDetails(member.getId());

                SecurityContextHolder.getContext()
                        .setAuthentication(authentication);

            } catch (Exception e) {
                SecurityContextHolder.clearContext();

                response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
                response.setContentType("application/json;charset=UTF-8");
                response.getWriter().write(
                        "{\"status\":401,\"message\":\"유효하지 않거나 만료된 토큰입니다.\"}"
                );
                return;
            }
        }

        filterChain.doFilter(request, response);
    }
}
