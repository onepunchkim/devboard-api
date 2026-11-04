
package com.example.devboard.service;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import javax.crypto.SecretKey;
import java.util.Base64;
import java.util.Date;

import static org.junit.jupiter.api.Assertions.*;

class JwtServiceTest {

    // 테스트용 Base64 Secret Key (32바이트 이상)
    private static final String SECRET =
            Base64.getEncoder().encodeToString(
                    "0123456789abcdef0123456789abcdef".getBytes()
            );

    private static final String OTHER_SECRET =
            Base64.getEncoder().encodeToString(
                    "abcdef0123456789abcdef0123456789".getBytes()
            );

    private JwtService jwtService;

    private SecretKey secretKey;

    @BeforeEach
    void setUp() {
        // 토큰 유효시간을 1시간으로 설정
        jwtService = new JwtService(SECRET, 60 * 60 * 1000L);

        secretKey = Keys.hmacShaKeyFor(
                Base64.getDecoder().decode(SECRET)
        );
    }

    // ==============================
    // 토큰 생성 테스트
    // ==============================

    @Test
    void generateTokenSuccessfully() {
        String token = jwtService.generateToken(
                1L,
                "test@test.com"
        );

        assertNotNull(token);
        assertFalse(token.isBlank());
    }

    @Test
    void generateTokenContainsMemberIdAndEmail() {
        String token = jwtService.generateToken(
                10L,
                "member@test.com"
        );

        Claims claims = jwtService.extractClaims(token);

        assertEquals("10", claims.getSubject());
        assertEquals("member@test.com", claims.get("email"));
    }

    // ==============================
    // Claims 추출 테스트
    // ==============================

    @Test
    void extractClaimsSuccessfully() {
        String token = jwtService.generateToken(
                20L,
                "claims@test.com"
        );

        Claims claims = jwtService.extractClaims(token);

        assertEquals("20", claims.getSubject());
        assertEquals("claims@test.com", claims.get("email"));
        assertNotNull(claims.getIssuedAt());
        assertNotNull(claims.getExpiration());
    }

    // ==============================
    // 회원 ID 추출 테스트
    // ==============================

    @Test
    void extractMemberIdSuccessfully() {
        String token = jwtService.generateToken(
                100L,
                "id@test.com"
        );

        Long memberId = jwtService.extractMemberId(token);

        assertEquals(100L, memberId);
    }

    // ==============================
    // 토큰 유효성 테스트
    // ==============================

    @Test
    void isTokenValidReturnsTrueForValidToken() {
        String token = jwtService.generateToken(
                1L,
                "valid@test.com"
        );

        assertTrue(jwtService.isTokenValid(token));
    }

    @Test
    void isTokenValidReturnsFalseForExpiredToken() {
        // 이미 만료된 토큰 생성
        Date now = new Date();

        String expiredToken = Jwts.builder()
                .subject("1")
                .claim("email", "expired@test.com")
                .issuedAt(new Date(now.getTime() - 2000))
                .expiration(new Date(now.getTime() - 1000))
                .signWith(secretKey)
                .compact();

        assertFalse(jwtService.isTokenValid(expiredToken));
    }

    @Test
    void isTokenValidReturnsFalseForWrongSignature() {
        // 다른 Secret Key로 서명한 토큰 생성
        SecretKey otherKey = Keys.hmacShaKeyFor(
                Base64.getDecoder().decode(OTHER_SECRET)
        );

        String token = Jwts.builder()
                .subject("1")
                .claim("email", "wrong@test.com")
                .expiration(new Date(System.currentTimeMillis() + 60000))
                .signWith(otherKey)
                .compact();

        assertFalse(jwtService.isTokenValid(token));
    }

    @Test
    void isTokenValidReturnsFalseForMalformedToken() {
        assertFalse(
                jwtService.isTokenValid("this-is-not-a-jwt")
        );
    }

    @Test
    void isTokenValidReturnsFalseWhenExpirationIsMissing() {
        // 만료 시간이 없는 토큰 생성
        String token = Jwts.builder()
                .subject("1")
                .claim("email", "no-exp@test.com")
                .issuedAt(new Date())
                .signWith(secretKey)
                .compact();

        assertFalse(jwtService.isTokenValid(token));
    }

    // ==============================
    // 토큰 추출 예외 테스트
    // ==============================

    @Test
    void extractClaimsRejectsInvalidSignature() {
        SecretKey otherKey = Keys.hmacShaKeyFor(
                Base64.getDecoder().decode(OTHER_SECRET)
        );

        String token = Jwts.builder()
                .subject("1")
                .expiration(new Date(System.currentTimeMillis() + 60000))
                .signWith(otherKey)
                .compact();

        assertThrows(
                Exception.class,
                () -> jwtService.extractClaims(token)
        );
    }

    @Test
    void extractMemberIdRejectsInvalidToken() {
        assertThrows(
                Exception.class,
                () -> jwtService.extractMemberId("invalid-token")
        );
    }
}
