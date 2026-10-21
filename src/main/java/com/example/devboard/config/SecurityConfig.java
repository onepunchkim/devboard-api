package com.example.devboard.config;

import com.example.devboard.repository.MemberRepository;
import com.example.devboard.service.JwtService;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
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

    //비밀번호 암호화 설정
    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }

    // CORS 설정
    @Bean
    public CorsConfigurationSource corsConfigurationSource() {
        CorsConfiguration configuration = new CorsConfiguration();

        configuration.setAllowedOrigins(
                List.of("http://localhost:5173")
        );

        configuration.setAllowedMethods(
                List.of("GET", "POST", "PUT", "DELETE", "OPTIONS")
        );

        configuration.setAllowedHeaders(
                List.of("Authorization", "Content-Type", "Accept")
        );

        configuration.setAllowCredentials(false);

        UrlBasedCorsConfigurationSource source =
                new UrlBasedCorsConfigurationSource();

        source.registerCorsConfiguration("/**", configuration);

        return source;
    }

    //Jwt 인증 필터 등록
    @Bean
    public JwtAuthenticationFilter jwtAuthenticationFilter(
            JwtService jwtService,
            MemberRepository memberRepository
    ) {
        return new JwtAuthenticationFilter(jwtService, memberRepository);
    }

    @Bean
    //API 접근 권한 설정
    public SecurityFilterChain securityFilterChain(
            HttpSecurity http,
            JwtAuthenticationFilter jwtAuthenticationFilter) throws Exception {
        http
                .cors(cors -> {})
                //REST API 이므로 CSRF 비활성화
                .csrf(csrf -> csrf.disable())

                //기본 로그인 방식 비활성화
                .formLogin(form -> form.disable())
                .httpBasic(basic -> basic.disable())

                //인증되지 않은 요청은 401 반환
                .exceptionHandling(exception -> exception
                        .authenticationEntryPoint(
                                new HttpStatusEntryPoint(
                                        HttpStatus.UNAUTHORIZED
                                )
                        ))

                //세션을 사용하지 않고 JWT 인증
                .sessionManagement(session -> session.sessionCreationPolicy(
                        SessionCreationPolicy.STATELESS))

                //API별 접근 권한
                .authorizeHttpRequests(auth -> auth
                        //프론트엔드 정적 파일은 누구나 접근 가능
                        .requestMatchers(
                                "/",
                                "/index.html",
                                "/login.html",
                                "/signup.html",
                                "/post-detail.html",
                                "/write.html",
                                "/css/**",
                                "/js/**",
                                "/favicon.ico"
                        ).permitAll()

                        //회원 가입 및 로그인 API는 인증 없이 접근 가능
                        .requestMatchers(
                                "/api/members/signup",
                                "/api/members/login")
                        .permitAll()

                        //게시글 API는 기존 JWT인증 유지
                        .requestMatchers(
                                "/api/posts/**",
                                "/api/comments/**",
                                "/api/members/me")
                        .authenticated()

                        //그 외 API는 기존 보안 정책에 맞게 처리
                        .anyRequest().permitAll())

                //JWT 인증 필터 등록
                .addFilterBefore(jwtAuthenticationFilter,
                UsernamePasswordAuthenticationFilter.class);

        return http.build();
    }
}
