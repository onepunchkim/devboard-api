package com.example.devboard.entity;

import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;

import static com.example.devboard.entity.ChatMessage.MessageType.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class ChatMessageTest {

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

    // 정상적인 채팅방과 참여자를 준비한다.
    private ChatMessage createTextMessage(String content) {
        Member low = member(1L);
        Member high = member(2L);

        return new ChatMessage(room(low, high), low, content);
    }

    // 1. 기본 생성자로 텍스트 메시지 생성
    @Test
    void textMessage_success() {
        ChatMessage message = createTextMessage("안녕하세요.");

        assertEquals(TEXT, message.getMessageType());
        assertEquals("안녕하세요.", message.getContent());
        assertNull(message.getImageUrl());
    }

    // 2. 두 번째 참여자도 메시지를 보낼 수 있다.
    @Test
    void highMember_canSendMessage() {
        Member low = member(1L);
        Member high = member(2L);

        ChatMessage message =
                new ChatMessage(room(low, high), high, "반갑습니다.");

        assertSame(high, message.getSender());
    }

    // 3. 사진 메시지 정상 생성
    @Test
    void imageMessage_success() {
        Member low = member(1L);
        Member high = member(2L);

        ChatMessage message = new ChatMessage(
                room(low, high),
                low,
                "사진 설명",
                IMAGE,
                "/images/test.png"
        );

        assertEquals(IMAGE, message.getMessageType());
        assertEquals("사진 설명", message.getContent());
        assertEquals("/images/test.png", message.getImageUrl());
    }

    // 4. 사진 메시지는 내용이 null이어도 저장할 수 있다.
    @Test
    void imageMessage_nullContent_success() {
        Member low = member(1L);
        Member high = member(2L);

        ChatMessage message = new ChatMessage(
                room(low, high),
                low,
                null,
                IMAGE,
                "/images/test.png"
        );

        assertEquals("", message.getContent());
    }

    // 5. 채팅방이 없으면 메시지를 생성할 수 없다.
    @Test
    void nullRoom_throwsException() {
        Member sender = member(1L);

        assertThrows(
                IllegalArgumentException.class,
                () -> new ChatMessage(null, sender, "안녕하세요.")
        );
    }

    // 6. 발신자가 없으면 메시지를 생성할 수 없다.
    @Test
    void nullSender_throwsException() {
        ChatRoom chatRoom = mock(ChatRoom.class);

        assertThrows(
                IllegalArgumentException.class,
                () -> new ChatMessage(chatRoom, null, "안녕하세요.")
        );
    }

    // 7. 메시지 종류는 필수다.
    @Test
    void nullMessageType_throwsException() {
        Member sender = member(1L);
        ChatRoom chatRoom = mock(ChatRoom.class);

        assertThrows(
                IllegalArgumentException.class,
                () -> new ChatMessage(
                        chatRoom, sender, "안녕하세요.", null, null
                )
        );
    }

    // 8. 텍스트 메시지 내용이 null이면 거부한다.
    @Test
    void textMessage_nullContent_throwsException() {
        assertThrows(
                IllegalArgumentException.class,
                () -> createTextMessage(null)
        );
    }

    // 9. 빈 텍스트 메시지는 거부한다.
    @Test
    void textMessage_emptyContent_throwsException() {
        assertThrows(
                IllegalArgumentException.class,
                () -> createTextMessage("")
        );
    }

    // 10. 공백만 있는 텍스트 메시지는 거부한다.
    @Test
    void textMessage_blankContent_throwsException() {
        assertThrows(
                IllegalArgumentException.class,
                () -> createTextMessage("   ")
        );
    }

    // 11. 메시지 내용이 2000자면 허용한다.
    @Test
    void content_2000Characters_success() {
        ChatMessage message = createTextMessage("a".repeat(2000));

        assertEquals(2000, message.getContent().length());
    }

    // 12. 메시지 내용이 2000자를 초과하면 거부한다.
    @Test
    void content_over2000Characters_throwsException() {
        assertThrows(
                IllegalArgumentException.class,
                () -> createTextMessage("a".repeat(2001))
        );
    }

    // 13. 사진 URL이 null이면 거부한다.
    @Test
    void imageMessage_nullUrl_throwsException() {
        Member sender = member(1L);
        ChatRoom chatRoom = mock(ChatRoom.class);

        assertThrows(
                IllegalArgumentException.class,
                () -> new ChatMessage(
                        chatRoom, sender, "사진", IMAGE, null
                )
        );
    }

    // 14. 사진 URL이 공백이면 거부한다.
    @Test
    void imageMessage_blankUrl_throwsException() {
        Member sender = member(1L);
        ChatRoom chatRoom = mock(ChatRoom.class);

        assertThrows(
                IllegalArgumentException.class,
                () -> new ChatMessage(
                        chatRoom, sender, "사진", IMAGE, "   "
                )
        );
    }

    // 15. 사진 URL이 500자면 허용한다.
    @Test
    void imageUrl_500Characters_success() {
        Member low = member(1L);
        Member high = member(2L);

        ChatMessage message = new ChatMessage(
                room(low, high),
                low,
                "사진",
                IMAGE,
                "a".repeat(500)
        );

        assertEquals(500, message.getImageUrl().length());
    }

    // 16. 사진 URL이 500자를 초과하면 거부한다.
    @Test
    void imageUrl_over500Characters_throwsException() {
        Member sender = member(1L);
        ChatRoom chatRoom = mock(ChatRoom.class);

        assertThrows(
                IllegalArgumentException.class,
                () -> new ChatMessage(
                        chatRoom, sender, "사진", IMAGE,
                        "a".repeat(501)
                )
        );
    }

    // 17. 채팅방에 참여하지 않은 회원은 메시지를 보낼 수 없다.
    @Test
    void nonParticipant_throwsException() {
        Member low = member(1L);
        Member high = member(2L);
        Member outsider = member(3L);

        assertThrows(
                IllegalArgumentException.class,
                () -> new ChatMessage(
                        room(low, high), outsider, "안녕하세요."
                )
        );
    }

    // 18. 메시지 내용의 앞뒤 공백을 제거한다.
    @Test
    void content_shouldBeTrimmed() {
        ChatMessage message =
                createTextMessage("  안녕하세요.  ");

        assertEquals("안녕하세요.", message.getContent());
    }

    // 19. 저장 직전 생성 시간이 설정된다.
    @Test
    void onCreate_setsCreatedAt() {
        ChatMessage message = createTextMessage("안녕하세요.");

        LocalDateTime before = LocalDateTime.now();

        message.onCreate();

        LocalDateTime after = LocalDateTime.now();

        assertNotNull(message.getCreatedAt());
        assertFalse(message.getCreatedAt().isBefore(before));
        assertFalse(message.getCreatedAt().isAfter(after));
    }
}
