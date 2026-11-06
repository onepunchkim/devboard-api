package com.example.devboard.entity;

import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class ChatRoomTest {

    // 지정한 ID를 반환하는 테스트용 회원 객체 생성
    private Member member(Long id) {
        Member member = mock(Member.class);
        when(member.getId()).thenReturn(id);
        return member;
    }

    @Test
    void createRoom_success_whenMemberIdsAscending() {
        // given: 첫 번째 회원의 ID가 더 작은 경우
        Member memberA = member(1L);
        Member memberB = member(2L);

        // when
        ChatRoom room = new ChatRoom(memberA, memberB);

        // then: ID가 작은 회원이 memberLow에 저장되어야 한다.
        assertSame(memberA, room.getMemberLow());
        assertSame(memberB, room.getMemberHigh());
    }

    @Test
    void createRoom_success_whenMemberIdsDescending() {
        // given: 첫 번째 회원의 ID가 더 큰 경우
        Member memberA = member(2L);
        Member memberB = member(1L);

        // when
        ChatRoom room = new ChatRoom(memberA, memberB);

        // then: 전달 순서와 관계없이 ID 순으로 정렬되어야 한다.
        assertSame(memberB, room.getMemberLow());
        assertSame(memberA, room.getMemberHigh());
    }

    @Test
    void createRoom_shouldRejectNullFirstMember() {
        // given
        Member memberB = mock(Member.class);

        // when & then: 첫 번째 회원이 null이면 생성 불가
        IllegalArgumentException exception = assertThrows(
                IllegalArgumentException.class,
                () -> new ChatRoom(null, memberB)
        );

        assertEquals("채팅 참여자는 필수입니다.", exception.getMessage());
    }

    @Test
    void createRoom_shouldRejectNullSecondMember() {
        // given
        Member memberA = mock(Member.class);

        // when & then: 두 번째 회원이 null이면 생성 불가
        IllegalArgumentException exception = assertThrows(
                IllegalArgumentException.class,
                () -> new ChatRoom(memberA, null)
        );

        assertEquals("채팅 참여자는 필수입니다.", exception.getMessage());
    }

    @Test
    void createRoom_shouldRejectSameMemberId() {
        // given: 서로 다른 객체지만 회원 ID는 동일
        Member memberA = member(1L);
        Member memberB = member(1L);

        // when & then: 자기 자신과 채팅방 생성 불가
        IllegalArgumentException exception = assertThrows(
                IllegalArgumentException.class,
                () -> new ChatRoom(memberA, memberB)
        );

        assertEquals(
                "자기 자신과의 채팅방은 만들 수 없습니다.",
                exception.getMessage()
        );
    }

    @Test
    void onCreate_shouldSetCreatedAt() {
        // given: JPA 저장 직전 실행되는 콜백 확인
        ChatRoom room = new ChatRoom(member(1L), member(2L));

        assertNull(room.getCreatedAt());

        LocalDateTime before = LocalDateTime.now();

        // when: @PrePersist 메서드를 직접 실행
        room.onCreate();

        LocalDateTime after = LocalDateTime.now();

        // then: 생성 시간이 현재 시각 범위에 있어야 한다.
        assertNotNull(room.getCreatedAt());
        assertFalse(room.getCreatedAt().isBefore(before));
        assertFalse(room.getCreatedAt().isAfter(after));
    }

    @Test
    void updateLastMessageAt_shouldUpdateTimestamp() {
        // given
        ChatRoom room = new ChatRoom(member(1L), member(2L));
        LocalDateTime messageTime = LocalDateTime.of(
                2026, 11, 6, 10, 30
        );

        // when
        room.updateLastMessageAt(messageTime);

        // then: 마지막 메시지 시간이 갱신되어야 한다.
        assertEquals(messageTime, room.getLastMessageAt());
    }

    @Test
    void updateLastMessageAt_shouldAllowNull() {
        // 현재 구현은 null을 허용한다.
        // 이 테스트는 현재 동작을 확인하기 위한 것이다.
        ChatRoom room = new ChatRoom(member(1L), member(2L));

        room.updateLastMessageAt(null);

        assertNull(room.getLastMessageAt());
    }
}
