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

    // MockMvc를 이용해 실제 HTTP 요청처럼 컨트롤러를 테스트
    @Autowired
    private MockMvc mockMvc;

    // 회원 정보를 DB에 저장하기 위한 Repository
    @Autowired
    private MemberRepository memberRepository;

    // 게시글 정보를 DB에 저장하기 위한 Repository
    @Autowired
    private PostRepository postRepository;

    // JWT 토큰 발급 서비스
    @Autowired
    private JwtService jwtService;

    // 비밀번호 암호화
    @Autowired
    private PasswordEncoder passwordEncoder;

    // JWT 설정값
    @Value("${jwt.secret}")
    private String jwtSecret;

    // 테스트용 회원
    private Member writer;
    private Member anotherMember;

    // 테스트용 게시글 ID
    private Long postId;

    // 테스트용 JWT
    private String writerToken;
    private String anotherMemberToken;

    @BeforeEach
    void setUp() {

        // 테스트 회원 1 생성
        writer = new Member(
                "writer@test.com",
                passwordEncoder.encode("password123"),
                "작성자"
        );

        memberRepository.save(writer);

        // 테스트 회원 2 생성
        anotherMember = new Member(
                "another@test.com",
                passwordEncoder.encode("password123"),
                "다른 회원"
        );

        memberRepository.save(anotherMember);

        // 작성자 명의의 게시글 생성
        Post post = new Post(
                "테스트 게시글",
                "테스트 내용",
                writer
        );

        postRepository.save(post);

        postId = post.getId();

        // 회원별 JWT 발급
        writerToken = jwtService.generateToken(
                writer.getId(),
                writer.getEmail()
        );

        anotherMemberToken = jwtService.generateToken(
                anotherMember.getId(),
                anotherMember.getEmail()
        );
    }

    // Authorization 헤더에 JWT를 추가하는 메서드
    private String bearerToken(String token) {
        return "Bearer " + token;
    }

    // 잘못된 서명으로 생성한 JWT
    private String createInvalidToken() {

        // 기존 JWT와 다른 임의의 서명 키 생성
        byte[] wrongKeyBytes = new byte[64];
        Arrays.fill(wrongKeyBytes, (byte) 1);

        SecretKey wrongKey = Keys.hmacShaKeyFor(wrongKeyBytes);

        // 잘못된 키로 JWT 생성
        return Jwts.builder()
                .subject(String.valueOf(writer.getId()))
                .claim("email", writer.getEmail())
                .issuedAt(new Date())
                .expiration(new Date(System.currentTimeMillis() + 60000))
                .signWith(wrongKey)
                .compact();
    }

    // 만료된 JWT 생성
    private String createExpiredToken() {

        // application.yml에 설정된 JWT 비밀 키 사용
        byte[] keyBytes = Decoders.BASE64.decode(jwtSecret);
        SecretKey secretKey = Keys.hmacShaKeyFor(keyBytes);

        Date now = new Date();

        // 이미 만료된 시간 설정
        Date expiredDate = new Date(now.getTime() - 60000);

        return Jwts.builder()
                .subject(String.valueOf(writer.getId()))
                .claim("email", writer.getEmail())
                .issuedAt(new Date(now.getTime() - 120000))
                .expiration(expiredDate)
                .signWith(secretKey)
                .compact();
    }

    // 존재하지 않는 회원 ID로 JWT 생성
    private String createNonexistentMemberToken() {

        return jwtService.generateToken(
                Long.MAX_VALUE,
                "notfound@test.com"
        );
    }

    // 1. JWT가 없는 요청은 401을 반환해야 한다.
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

    // 2. 잘못된 JWT는 401을 반환해야 한다.
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

    // 3. 만료된 JWT는 401을 반환해야 한다.
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

    // 4. DB에 존재하지 않는 회원의 JWT는 401을 반환해야 한다.
    @Test
    void nonexistentMemberTokenReturns401() throws Exception {

        // DB에 존재하지 않는 회원 ID로 JWT 생성
        String token = createNonexistentMemberToken();

        mockMvc.perform(get("/api/posts")
                        .header(
                                "Authorization",
                                bearerToken(token)
                        ))
                // 존재하지 않는 회원이므로 인증 실패
                .andExpect(status().isUnauthorized());
    }

    // 5. 정상적인 JWT로 게시글을 작성할 수 있어야 한다.
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

    // 6. 정상적인 JWT로 게시글을 조회할 수 있어야 한다.
    @Test
    void validTokenCanGetPost() throws Exception {

        mockMvc.perform(get("/api/posts/{id}", postId)
                        .header(
                                "Authorization",
                                bearerToken(writerToken)
                        ))
                .andExpect(status().isOk());
    }

    // 7. 다른 회원은 게시글을 수정할 수 없어야 한다.
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

    // 8. 다른 회원은 게시글을 삭제할 수 없어야 한다.
    @Test
    void anotherMemberCannotDeletePost() throws Exception {

        mockMvc.perform(delete("/api/posts/{id}", postId)
                        .header(
                                "Authorization",
                                bearerToken(anotherMemberToken)
                        ))
                .andExpect(status().isForbidden());
    }
}