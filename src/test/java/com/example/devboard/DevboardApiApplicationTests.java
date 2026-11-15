package com.example.devboard;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

@SpringBootTest
@ActiveProfiles("test") // 테스트 실행 시 application-test.yml 활성화
class DevboardApiApplicationTests {

	// Spring Boot 전체 컨텍스트가 정상적으로 로딩되는지 확인
	@Test
	void contextLoads() {
	}
}
