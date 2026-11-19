package com.example.devboard.config;

import com.example.devboard.repository.MemberRepository;
import com.example.devboard.service.JwtService;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;

import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;

import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.HttpStatusEntryPoint;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;

import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;

import java.util.List;

@Configuration
public class SecurityConfig {

    // =====================================================
    // 1. 비밀번호 암호화 설정
    // =====================================================

    @Bean
    public PasswordEncoder passwordEncoder() {

        // 회원 비밀번호를 BCrypt 방식으로 암호화
        return new BCryptPasswordEncoder();
    }

    // =====================================================
    // 2. CORS 설정
    // =====================================================

    @Bean
    public CorsConfigurationSource corsConfigurationSource() {

        CorsConfiguration configuration = new CorsConfiguration();

        // 프론트엔드 개발 서버 주소
        configuration.setAllowedOrigins(
                List.of("http://localhost:5173")
        );

        // 허용할 HTTP 메서드
        configuration.setAllowedMethods(
                List.of("GET", "POST", "PUT", "PATCH", "DELETE", "OPTIONS")
        );

        // 허용할 요청 헤더
        configuration.setAllowedHeaders(
                List.of("Authorization", "Content-Type", "Accept")
        );

        // JWT를 Authorization 헤더로 전달하므로
        // 쿠키 기반 인증 정보는 사용하지 않음
        configuration.setAllowCredentials(false);

        // 모든 경로에 CORS 설정 적용
        UrlBasedCorsConfigurationSource source =
                new UrlBasedCorsConfigurationSource();

        source.registerCorsConfiguration("/**", configuration);

        return source;
    }

    // =====================================================
    // 3. JWT 인증 필터 등록
    // =====================================================

    @Bean
    public JwtAuthenticationFilter jwtAuthenticationFilter(
            JwtService jwtService,
            MemberRepository memberRepository
    ) {

        // JWT 검증과 회원 존재 여부를 확인하는 필터
        return new JwtAuthenticationFilter(
                jwtService,
                memberRepository
        );
    }

    // =====================================================
    // 4. Spring Security 설정
    // =====================================================

    @Bean
    public SecurityFilterChain securityFilterChain(
            HttpSecurity http,
            JwtAuthenticationFilter jwtAuthenticationFilter
    ) throws Exception {

        http
                // CORS 설정 활성화
                .cors(cors -> {})

                // JWT 기반 REST API이므로 CSRF 비활성화
                .csrf(csrf -> csrf.disable())

                // 세션 기반 로그인 비활성화
                .formLogin(form -> form.disable())

                // HTTP Basic 인증 비활성화
                .httpBasic(basic -> basic.disable())

                // =====================================================
                // 인증 및 접근 거부 처리
                // =====================================================

                .exceptionHandling(exception -> exception

                        // 인증되지 않은 사용자의 요청은 401
                        .authenticationEntryPoint(
                                new HttpStatusEntryPoint(
                                        HttpStatus.UNAUTHORIZED
                                )
                        )

                        // 인증된 사용자에게 권한이 없으면 403
                        .accessDeniedHandler(
                                (request, response, accessDeniedException) ->
                                        response.sendError(
                                                HttpStatus.FORBIDDEN.value()
                                        )
                        )
                )

                // =====================================================
                // 세션 정책
                // =====================================================

                // 서버 세션을 생성하지 않고 JWT로 인증
                .sessionManagement(session ->
                        session.sessionCreationPolicy(
                                SessionCreationPolicy.STATELESS
                        )
                )

                // =====================================================
                // API 접근 권한 설정
                // =====================================================

                .authorizeHttpRequests(auth -> auth

                        // -------------------------------------------------
                        // 정적 리소스 및 Swagger 공개
                        // -------------------------------------------------

                        .requestMatchers(
                                "/",
                                "/index.html",
                                "/login.html",
                                "/signup.html",
                                "/post-detail.html",
                                "/write.html",
                                "/css/**",
                                "/js/**",
                                "/favicon.ico",
                                "/favicon_ico/**",
                                "/swagger-ui.html",
                                "/swagger-ui/**",
                                "/v3/api-docs",
                                "/v3/api-docs/**"
                        ).permitAll()

                        // -------------------------------------------------
                        // 회원가입 및 로그인 공개
                        // -------------------------------------------------

                        .requestMatchers(
                                "/api/members/signup",
                                "/api/members/login"
                        ).permitAll()

                        // -------------------------------------------------
                        // 기존 게시글·댓글·좋아요·채팅 API 보호
                        // -------------------------------------------------

                        // 게시글 조회도 기존 설정대로 인증 필요
                        .requestMatchers("/api/posts/**")
                        .authenticated()

                        // 댓글 수정 및 삭제 API 보호
                        .requestMatchers("/api/comments/**")
                        .authenticated()

                        // 채팅 API 보호
                        .requestMatchers("/api/chat/**")
                        .authenticated()

                        // 회원 탈퇴 및 온라인 회원 조회 보호
                        .requestMatchers(
                                "/api/members/me",
                                "/api/members/online"
                        ).authenticated()

                        // -------------------------------------------------
                        // 그 외 API 보호
                        // -------------------------------------------------

                        // 앞으로 추가하는 모든 API는 기본적으로 인증 필요
                        .requestMatchers("/api/**")
                        .authenticated()

                        // API가 아닌 기존 프론트엔드 경로는 공개 유지
                        .anyRequest()
                        .permitAll()
                )

                // =====================================================
                // JWT 인증 필터 등록
                // =====================================================

                // UsernamePasswordAuthenticationFilter보다 먼저 실행
                .addFilterBefore(
                        jwtAuthenticationFilter,
                        UsernamePasswordAuthenticationFilter.class
                );

        return http.build();
    }
}
