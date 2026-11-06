package com.example.devboard.entity;

import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class ChatRoomMemberTest {

    // 테스트용 회원 생성
    private Member member(Long id) {
        Member member = mock(Member.class);
        when(member.getId()).thenReturn(id);
        return member;
    }

    // 테스트용 채팅방 생성
    private ChatRoom room(Member low, Member high) {
        ChatRoom room = mock(ChatRoom.class);
        when(room.getMemberLow()).thenReturn(low);
        when(room.getMemberHigh()).thenReturn(high);
        return room;
    }

    // 1. 첫 번째 참여자는 정상적으로 등록된다.
    @Test
    void constructor_lowMember_success() {
        Member low = member(1L);
        Member high = member(2L);
        ChatRoom chatRoom = room(low, high);

        ChatRoomMember participant =
                new ChatRoomMember(chatRoom, low);

        assertSame(chatRoom, participant.getChatRoom());
        assertSame(low, participant.getMember());
        assertNull(participant.getLastReadMessageId());
    }

    // 2. 두 번째 참여자도 정상적으로 등록된다.
    @Test
    void constructor_highMember_success() {
        Member low = member(1L);
        Member high = member(2L);
        ChatRoom chatRoom = room(low, high);

        ChatRoomMember participant =
                new ChatRoomMember(chatRoom, high);

        assertSame(high, participant.getMember());
    }

    // 3. 채팅방이 null이면 등록할 수 없다.
    @Test
    void constructor_nullRoom_throwsException() {
        Member member = member(1L);

        IllegalArgumentException exception =
                assertThrows(
                        IllegalArgumentException.class,
                        () -> new ChatRoomMember(null, member)
                );

        assertEquals(
                "채팅방과 회원은 필수입니다.",
                exception.getMessage()
        );
    }

    // 4. 회원이 null이면 등록할 수 없다.
    @Test
    void constructor_nullMember_throwsException() {
        ChatRoom chatRoom = mock(ChatRoom.class);

        assertThrows(
                IllegalArgumentException.class,
                () -> new ChatRoomMember(chatRoom, null)
        );
    }

    // 5. 채팅방에 속하지 않은 회원은 등록할 수 없다.
    @Test
    void constructor_nonParticipant_throwsException() {
        Member low = member(1L);
        Member high = member(2L);
        Member outsider = member(3L);

        ChatRoom chatRoom = room(low, high);

        IllegalArgumentException exception =
                assertThrows(
                        IllegalArgumentException.class,
                        () -> new ChatRoomMember(chatRoom, outsider)
                );

        assertEquals(
                "해당 채팅방의 참여자만 등록할 수 있습니다.",
                exception.getMessage()
        );
    }

    // 6. 최초 읽음 처리 시 메시지 ID가 저장된다.
    @Test
    void markAsRead_firstMessage_success() {
        ChatRoomMember participant = createParticipant();

        participant.markAsRead(10L);

        assertEquals(10L, participant.getLastReadMessageId());
    }

    // 7. 더 최신 메시지를 읽으면 읽음 위치가 갱신된다.
    @Test
    void markAsRead_newerMessage_updates() {
        ChatRoomMember participant = createParticipant();

        participant.markAsRead(10L);
        participant.markAsRead(20L);

        assertEquals(20L, participant.getLastReadMessageId());
    }

    // 8. 이전 메시지를 읽어도 읽음 위치가 뒤로 이동하지 않는다.
    @Test
    void markAsRead_olderMessage_doesNotUpdate() {
        ChatRoomMember participant = createParticipant();

        participant.markAsRead(20L);
        participant.markAsRead(10L);

        assertEquals(20L, participant.getLastReadMessageId());
    }

    // 9. 동일 메시지를 다시 읽어도 기존 상태가 유지된다.
    @Test
    void markAsRead_sameMessage_doesNotUpdate() {
        ChatRoomMember participant = createParticipant();

        participant.markAsRead(10L);
        participant.markAsRead(10L);

        assertEquals(10L, participant.getLastReadMessageId());
    }

    // 10. 메시지 ID가 null이면 예외가 발생한다.
    @Test
    void markAsRead_nullMessageId_throwsException() {
        ChatRoomMember participant = createParticipant();

        IllegalArgumentException exception =
                assertThrows(
                        IllegalArgumentException.class,
                        () -> participant.markAsRead(null)
                );

        assertEquals(
                "읽은 메시지 ID는 필수입니다.",
                exception.getMessage()
        );
    }

    // 11. JPA 저장 직전 생성 시각이 설정된다.
    @Test
    void onCreate_setsJoinedAt() {
        ChatRoomMember participant = createParticipant();

        LocalDateTime before = LocalDateTime.now();

        participant.onCreate();

        LocalDateTime after = LocalDateTime.now();

        assertNotNull(participant.getJoinedAt());
        assertFalse(participant.getJoinedAt().isBefore(before));
        assertFalse(participant.getJoinedAt().isAfter(after));
    }

    // 테스트에서 공통으로 사용할 정상 참여자 생성
    private ChatRoomMember createParticipant() {
        Member low = member(1L);
        Member high = member(2L);

        return new ChatRoomMember(room(low, high), low);
    }
}
