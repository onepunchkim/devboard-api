package com.example.devboard.service;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import io.jsonwebtoken.security.SignatureException;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import javax.crypto.SecretKey;

import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.Date;

import static org.junit.jupiter.api.Assertions.*;

class JwtServiceTest {

    // 테스트용 비밀 키: 32바이트
    private static final String SECRET =
            Base64.getEncoder().encodeToString(
                    "0123456789abcdef0123456789abcdef"
                            .getBytes(StandardCharsets.UTF_8)
            );

    // 잘못된 서명 테스트에 사용할 별도의 비밀 키
    private static final String OTHER_SECRET =
            Base64.getEncoder().encodeToString(
                    "abcdef0123456789abcdef0123456789"
                            .getBytes(StandardCharsets.UTF_8)
            );

    // JWT 만료 시간: 1시간
    private static final long EXPIRATION_MS = 60 * 60 * 1000L;

    private JwtService jwtService;
    private SecretKey secretKey;

    @BeforeEach
    void setUp() {

        // Spring Context 없이 JWT 서비스 직접 생성
        jwtService = new JwtService(SECRET, EXPIRATION_MS);

        // 테스트용 JWT 서명 키 생성
        secretKey = Keys.hmacShaKeyFor(
                Base64.getDecoder().decode(SECRET)
        );
    }

    // =====================================================
    // 1. JWT 생성 성공
    // =====================================================

    @Test
    void generateTokenSuccessfully() {

        String token = jwtService.generateToken(
                1L,
                "test@test.com"
        );

        assertNotNull(token);
        assertFalse(token.isBlank());
    }

    // =====================================================
    // 2. JWT에 회원 ID와 이메일 포함
    // =====================================================

    @Test
    void generateTokenContainsMemberIdAndEmail() {

        String token = jwtService.generateToken(
                10L,
                "member@test.com"
        );

        Claims claims = jwtService.extractClaims(token);

        assertEquals("10", claims.getSubject());
        assertEquals(
                "member@test.com",
                claims.get("email", String.class)
        );
    }

    // =====================================================
    // 3. JWT Claims 추출 성공
    // =====================================================

    @Test
    void extractClaimsSuccessfully() {

        String token = jwtService.generateToken(
                20L,
                "claims@test.com"
        );

        Claims claims = jwtService.extractClaims(token);

        assertEquals("20", claims.getSubject());
        assertEquals("claims@test.com", claims.get("email"));

        // 발급 시간과 만료 시간이 포함되어 있는지 확인
        assertNotNull(claims.getIssuedAt());
        assertNotNull(claims.getExpiration());
    }

    // =====================================================
    // 4. JWT 회원 ID 추출 성공
    // =====================================================

    @Test
    void extractMemberIdSuccessfully() {

        String token = jwtService.generateToken(
                100L,
                "id@test.com"
        );

        Long memberId = jwtService.extractMemberId(token);

        assertEquals(100L, memberId);
    }

    // =====================================================
    // 5. 정상 JWT 유효성 검증
    // =====================================================

    @Test
    void isTokenValidReturnsTrueForValidToken() {

        String token = jwtService.generateToken(
                1L,
                "valid@test.com"
        );

        assertTrue(jwtService.isTokenValid(token));
    }

    // =====================================================
    // 6. 만료된 JWT 검증
    // =====================================================

    @Test
    void isTokenValidReturnsFalseForExpiredToken() {

        Date now = new Date();

        // 현재 시간보다 이전에 만료된 JWT 생성
        String expiredToken = Jwts.builder()
                .subject("1")
                .claim("email", "expired@test.com")
                .issuedAt(
                        new Date(now.getTime() - 120000)
                )
                .expiration(
                        new Date(now.getTime() - 60000)
                )
                .signWith(secretKey)
                .compact();

        assertFalse(jwtService.isTokenValid(expiredToken));
    }

    // =====================================================
    // 7. 다른 비밀 키로 서명된 JWT 검증
    // =====================================================

    @Test
    void isTokenValidReturnsFalseForWrongSignature() {

        SecretKey otherKey = Keys.hmacShaKeyFor(
                Base64.getDecoder().decode(OTHER_SECRET)
        );

        String token = Jwts.builder()
                .subject("1")
                .claim("email", "wrong@test.com")
                .expiration(
                        new Date(System.currentTimeMillis() + 60000)
                )
                .signWith(otherKey)
                .compact();

        assertFalse(jwtService.isTokenValid(token));
    }

    // =====================================================
    // 8. 잘못된 JWT 문자열 검증
    // =====================================================

    @Test
    void isTokenValidReturnsFalseForMalformedToken() {

        assertFalse(
                jwtService.isTokenValid("this-is-not-a-jwt")
        );
    }

    // =====================================================
    // 9. 만료 시간이 없는 JWT 검증
    // =====================================================

    @Test
    void isTokenValidReturnsFalseWhenExpirationIsMissing() {

        String token = Jwts.builder()
                .subject("1")
                .claim("email", "no-exp@test.com")
                .issuedAt(new Date())
                .signWith(secretKey)
                .compact();

        assertFalse(jwtService.isTokenValid(token));
    }

    // =====================================================
    // 10. 잘못된 서명의 Claims 추출 거부
    // =====================================================

    @Test
    void extractClaimsRejectsInvalidSignature() {

        SecretKey otherKey = Keys.hmacShaKeyFor(
                Base64.getDecoder().decode(OTHER_SECRET)
        );

        String token = Jwts.builder()
                .subject("1")
                .expiration(
                        new Date(System.currentTimeMillis() + 60000)
                )
                .signWith(otherKey)
                .compact();

        // 서명 검증 실패 시 구체적인 예외 확인
        assertThrows(
                SignatureException.class,
                () -> jwtService.extractClaims(token)
        );
    }

    // =====================================================
    // 11. 잘못된 JWT에서 회원 ID 추출 거부
    // =====================================================

    @Test
    void extractMemberIdRejectsInvalidToken() {

        assertThrows(
                io.jsonwebtoken.JwtException.class,
                () -> jwtService.extractMemberId("invalid-token")
        );
    }

    // =====================================================
    // 12. 숫자가 아닌 회원 ID 거부
    // =====================================================

    @Test
    void extractMemberIdRejectsNonNumericSubject() {

        String token = Jwts.builder()
                .subject("invalid-member-id")
                .claim("email", "test@test.com")
                .expiration(
                        new Date(System.currentTimeMillis() + 60000)
                )
                .signWith(secretKey)
                .compact();

        assertThrows(
                NumberFormatException.class,
                () -> jwtService.extractMemberId(token)
        );
    }

    // =====================================================
    // 13. JWT 만료 시간 검증
    // =====================================================

    @Test
    void generateTokenSetsCorrectExpiration() {

        String token = jwtService.generateToken(
                1L,
                "test@test.com"
        );

        Claims claims = jwtService.extractClaims(token);

        long issuedAt = claims.getIssuedAt().getTime();
        long expiration = claims.getExpiration().getTime();

        // JWT 시간 정보는 초 단위로 저장되므로
        // 실제 차이는 설정한 1시간과 일치해야 함
        assertEquals(
                EXPIRATION_MS,
                expiration - issuedAt
        );
    }

    // =====================================================
    // 14. 빈 문자열 JWT 검증
    // =====================================================

    @Test
    void isTokenValidReturnsFalseForEmptyToken() {

        assertFalse(jwtService.isTokenValid(""));
    }

    // =====================================================
    // 15. null JWT 검증
    // =====================================================

    @Test
    void isTokenValidReturnsFalseForNullToken() {

        assertFalse(jwtService.isTokenValid(null));
    }
}
