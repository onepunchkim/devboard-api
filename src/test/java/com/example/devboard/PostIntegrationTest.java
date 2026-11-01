package com.example.devboard;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

import static org.junit.jupiter.api.Assertions.assertTrue;

@SpringBootTest
@ActiveProfiles("test")
class PostIntegrationTest {

    // Spring 애플리케이션과 테스트 DB 연결이 정상인지 확인
    @Test
    void applicationContextLoads() {
        assertTrue(true);
    }
}