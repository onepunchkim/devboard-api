package com.example.devboard.entity;

import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;

import static org.junit.jupiter.api.Assertions.*;

class MemberTest {

    @Test
    void createMember_success() {
        // given: 회원가입에 사용할 정보
        String email = "test@test.com";
        String password = "encodedPassword123";
        String nickname = "테스트회원";

        // when: 회원 엔티티 생성
        Member member = new Member(email, password, nickname);

        // then: 전달한 정보가 올바르게 저장되는지 확인
        assertEquals(email, member.getEmail());
        assertEquals(password, member.getPassword());
        assertEquals(nickname, member.getNickname());

        // DB 저장 전에는 ID와 가입 시간이 설정되지 않는다.
        assertNull(member.getId());
        assertNull(member.getCreateAt());
    }

    @Test
    void onCreate_shouldSetCreateAt() {
        // given: 아직 DB에 저장하지 않은 회원
        Member member = new Member(
                "test@test.com",
                "encodedPassword123",
                "테스트회원"
        );

        LocalDateTime before = LocalDateTime.now();

        // when: JPA @PrePersist 콜백 실행
        member.onCreate();

        LocalDateTime after = LocalDateTime.now();

        // then: 가입 시간이 정상적으로 설정되는지 확인
        assertNotNull(member.getCreateAt());
        assertFalse(member.getCreateAt().isBefore(before));
        assertFalse(member.getCreateAt().isAfter(after));
    }

    @Test
    void defaultConstructor_shouldInitializeFieldsAsNull() {
        // given & when: JPA 기본 생성자 호출
        Member member = new Member();

        // then: 초기 상태 확인
        assertNull(member.getId());
        assertNull(member.getEmail());
        assertNull(member.getPassword());
        assertNull(member.getNickname());
        assertNull(member.getCreateAt());
    }
}
