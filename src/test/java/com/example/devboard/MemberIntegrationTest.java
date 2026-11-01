package com.example.devboard;

import com.example.devboard.dto.request.MemberSignupRequest;
import com.example.devboard.entity.Member;
import com.example.devboard.repository.MemberRepository;
import com.example.devboard.service.MemberService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest
@ActiveProfiles("test")
@Transactional
class MemberIntegrationTest {

    @Autowired
    private MemberService memberService;

    @Autowired
    private MemberRepository memberRepository;

    @Autowired
    private PasswordEncoder passwordEncoder;

    // 회원가입 시 회원 정보가 DB에 저장되고 비밀번호가 암호화되는지 확인
    @Test
    void signup_shouldSaveMemberAndEncodePassword() {
        // given: 회원가입에 사용할 데이터 준비
        String email = "integration-test@example.com";
        String rawPassword = "TestPassword123!";
        String nickname = "통합테스트회원";

        MemberSignupRequest request =
                new MemberSignupRequest(email, rawPassword, nickname);

        // when: 회원가입 서비스 실행
        memberService.signup(request);

        // then: DB에서 회원 정보를 조회하여 검증
        Member savedMember = memberRepository.findByEmail(email)
                .orElseThrow(() -> new AssertionError("회원이 DB에 저장되지 않았습니다."));

        // 이메일과 닉네임이 정상적으로 저장되었는지 확인
        assertEquals(email, savedMember.getEmail());
        assertEquals(nickname, savedMember.getNickname());

        // 비밀번호가 평문으로 저장되지 않았는지 확인
        assertNotEquals(rawPassword, savedMember.getPassword());

        // 저장된 암호화 비밀번호가 원래 비밀번호와 일치하는지 확인
        assertTrue(passwordEncoder.matches(
                rawPassword,
                savedMember.getPassword()
        ));
    }
}