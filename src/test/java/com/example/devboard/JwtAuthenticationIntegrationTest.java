package com.example.devboard;

import com.example.devboard.entity.Member;
import com.example.devboard.entity.Post;
import com.example.devboard.repository.MemberRepository;
import com.example.devboard.repository.PostRepository;
import com.example.devboard.service.JwtService;

import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.io.Decoders;
import io.jsonwebtoken.security.Keys;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;

import org.springframework.http.MediaType;
import org.springframework.security.crypto.password.PasswordEncoder;

import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

import javax.crypto.SecretKey;

import java.util.Arrays;
import java.util.Date;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Transactional
class JwtAuthenticationIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private MemberRepository memberRepository;

    @Autowired
    private PostRepository postRepository;

    @Autowired
    private JwtService jwtService;

    @Autowired
    private PasswordEncoder passwordEncoder;

    // 실제 테스트 프로필에 설정된 JWT 비밀 키
    @Value("${jwt.secret}")
    private String jwtSecret;

    private Member writer;
    private Member anotherMember;

    private Long postId;

    private String writerToken;
    private String anotherMemberToken;

    // =====================================================
    // 테스트 데이터 준비
    // =====================================================

    @BeforeEach
    void setUp() {

        // 게시글 작성자 생성
        writer = new Member(
                "writer@test.com",
                passwordEncoder.encode("password123"),
                "작성자"
        );

        writer = memberRepository.saveAndFlush(writer);

        // 다른 회원 생성
        anotherMember = new Member(
                "another@test.com",
                passwordEncoder.encode("password123"),
                "다른회원"
        );

        anotherMember = memberRepository.saveAndFlush(
                anotherMember
        );

        // 작성자 명의의 게시글 생성
        Post post = new Post(
                "JWT 테스트 게시글",
                "JWT 인증 테스트 내용",
                writer
        );

        post = postRepository.saveAndFlush(post);

        postId = post.getId();

        // 실제 JwtService를 사용하여 JWT 발급
        writerToken = jwtService.generateToken(
                writer.getId(),
                writer.getEmail()
        );

        anotherMemberToken = jwtService.generateToken(
                anotherMember.getId(),
                anotherMember.getEmail()
        );
    }

    // =====================================================
    // 공통 메서드
    // =====================================================

    // JWT Authorization 헤더 생성
    private String bearerToken(String token) {
        return "Bearer " + token;
    }

    // 잘못된 서명으로 JWT 생성
    private String createInvalidToken() {

        byte[] wrongKeyBytes = new byte[64];

        Arrays.fill(wrongKeyBytes, (byte) 1);

        SecretKey wrongKey = Keys.hmacShaKeyFor(
                wrongKeyBytes
        );

        return Jwts.builder()
                .subject(String.valueOf(writer.getId()))
                .claim("email", writer.getEmail())
                .issuedAt(new Date())
                .expiration(
                        new Date(System.currentTimeMillis() + 60000)
                )
                .signWith(wrongKey)
                .compact();
    }

    // 이미 만료된 JWT 생성
    private String createExpiredToken() {

        byte[] keyBytes = Decoders.BASE64.decode(jwtSecret);

        SecretKey secretKey = Keys.hmacShaKeyFor(keyBytes);

        Date now = new Date();

        return Jwts.builder()
                .subject(String.valueOf(writer.getId()))
                .claim("email", writer.getEmail())
                .issuedAt(
                        new Date(now.getTime() - 120000)
                )
                .expiration(
                        new Date(now.getTime() - 60000)
                )
                .signWith(secretKey)
                .compact();
    }

    // 존재하지 않는 회원의 JWT 생성
    private String createNonexistentMemberToken() {

        return jwtService.generateToken(
                Long.MAX_VALUE,
                "notfound@test.com"
        );
    }

    // =====================================================
    // 1. JWT가 없는 요청은 401
    // =====================================================

    @Test
    void noTokenReturns401() throws Exception {

        String requestBody = """
                {
                    "title": "새 게시글",
                    "content": "게시글 내용"
                }
                """;

        mockMvc.perform(post("/api/posts")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(requestBody))
                .andExpect(status().isUnauthorized());
    }

    // =====================================================
    // 2. 잘못된 JWT는 401
    // =====================================================

    @Test
    void invalidTokenReturns401() throws Exception {

        String invalidToken = createInvalidToken();

        mockMvc.perform(post("/api/posts")
                        .header(
                                "Authorization",
                                bearerToken(invalidToken)
                        )
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                    "title": "새 게시글",
                                    "content": "게시글 내용"
                                }
                                """))
                .andExpect(status().isUnauthorized());
    }

    // =====================================================
    // 3. 만료된 JWT는 401
    // =====================================================

    @Test
    void expiredTokenReturns401() throws Exception {

        String expiredToken = createExpiredToken();

        mockMvc.perform(post("/api/posts")
                        .header(
                                "Authorization",
                                bearerToken(expiredToken)
                        )
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                    "title": "새 게시글",
                                    "content": "게시글 내용"
                                }
                                """))
                .andExpect(status().isUnauthorized());
    }

    // =====================================================
    // 4. 존재하지 않는 회원의 JWT는 401
    // =====================================================

    @Test
    void nonexistentMemberTokenReturns401() throws Exception {

        String token = createNonexistentMemberToken();

        mockMvc.perform(get("/api/posts")
                        .header(
                                "Authorization",
                                bearerToken(token)
                        ))
                .andExpect(status().isUnauthorized());
    }

    // =====================================================
    // 5. 정상 JWT로 게시글 작성
    // =====================================================

    @Test
    void validTokenCanCreatePost() throws Exception {

        String requestBody = """
                {
                    "title": "JWT 테스트 게시글",
                    "content": "정상적인 JWT 인증 테스트"
                }
                """;

        mockMvc.perform(post("/api/posts")
                        .header(
                                "Authorization",
                                bearerToken(writerToken)
                        )
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(requestBody))
                .andExpect(status().isOk());
    }

    // =====================================================
    // 6. 정상 JWT로 게시글 조회
    // =====================================================

    @Test
    void validTokenCanGetPost() throws Exception {

        mockMvc.perform(get("/api/posts/{id}", postId)
                        .header(
                                "Authorization",
                                bearerToken(writerToken)
                        ))
                .andExpect(status().isOk());
    }

    // =====================================================
    // 7. 다른 회원의 게시글 수정 거부
    // =====================================================

    @Test
    void anotherMemberCannotUpdatePost() throws Exception {

        String requestBody = """
                {
                    "title": "수정 시도",
                    "content": "다른 회원의 게시글 수정 시도"
                }
                """;

        mockMvc.perform(put("/api/posts/{id}", postId)
                        .header(
                                "Authorization",
                                bearerToken(anotherMemberToken)
                        )
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(requestBody))
                .andExpect(status().isForbidden());
    }

    // =====================================================
    // 8. 다른 회원의 게시글 삭제 거부
    // =====================================================

    @Test
    void anotherMemberCannotDeletePost() throws Exception {

        mockMvc.perform(delete("/api/posts/{id}", postId)
                        .header(
                                "Authorization",
                                bearerToken(anotherMemberToken)
                        ))
                .andExpect(status().isForbidden());
    }

    // =====================================================
    // 9. Bearer 형식이 아닌 인증 헤더 거부
    // =====================================================

    @Test
    void malformedAuthorizationHeaderReturns401()
            throws Exception {

        mockMvc.perform(get("/api/posts")
                        .header(
                                "Authorization",
                                "Basic invalid"
                        ))
                .andExpect(status().isUnauthorized());
    }

    // =====================================================
    // 10. 빈 Bearer 토큰 거부
    // =====================================================

    @Test
    void emptyBearerTokenReturns401() throws Exception {

        mockMvc.perform(get("/api/posts")
                        .header(
                                "Authorization",
                                "Bearer "
                        ))
                .andExpect(status().isUnauthorized());
    }

    // =====================================================
    // 11. JWT 이메일과 실제 회원 이메일 불일치
    // =====================================================

    @Test
    void mismatchedEmailReturns401() throws Exception {

        // 실제 회원 ID를 사용하지만 이메일은 다르게 설정
        String token = jwtService.generateToken(
                writer.getId(),
                "wrong@test.com"
        );

        mockMvc.perform(get("/api/posts")
                        .header(
                                "Authorization",
                                bearerToken(token)
                        ))
                .andExpect(status().isUnauthorized());
    }

    // =====================================================
    // 12. 회원가입 API는 JWT 없이 접근 가능
    // =====================================================

    @Test
    void signupWithoutTokenDoesNotRequireAuthentication()
            throws Exception {

        // 잘못된 회원가입 요청을 보내 인증 차단 여부 확인
        mockMvc.perform(post("/api/members/signup")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isBadRequest());
    }
}
