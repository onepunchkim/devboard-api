package com.example.devboard.entity;

import jakarta.persistence.*;
import lombok.Getter;

import java.time.LocalDateTime;

@Getter
@Entity
@Table(
        name = "chat_messages",
        indexes = {
                // 채팅방별 메시지 조회를 위한 인덱스
                @Index(
                        name = "idx_chat_messages_room_id",
                        columnList = "room_id, id"
                ),
                // 발신자별 메시지 조회를 위한 인덱스
                @Index(
                        name = "idx_chat_messages_sender_id",
                        columnList = "sender_id"
                )
        }
)
public class ChatMessage {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    // 메시지가 속한 채팅방
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "room_id", nullable = false)
    private ChatRoom chatRoom;

    // 메시지를 보낸 회원
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "sender_id", nullable = false)
    private Member sender;

    // 메시지 내용 또는 사진 설명
    @Column(nullable = false, length = 2000)
    private String content;

    // 메시지 종류: TEXT 또는 IMAGE
    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 10)
    private MessageType messageType = MessageType.TEXT;

    // 업로드된 사진의 URL (텍스트 메시지는 null)
    @Column(length = 500)
    private String imageUrl;

    // 메시지 작성 시간
    @Column(nullable = false, updatable = false)
    private LocalDateTime createdAt;

    protected ChatMessage() {
    }

    // 기존 텍스트 메시지 생성자: 기존 호출 코드를 유지한다.
    public ChatMessage(ChatRoom chatRoom, Member sender, String content) {
        this(chatRoom, sender, content, MessageType.TEXT, null);
    }

    // 텍스트 또는 사진 메시지를 생성한다.
    public ChatMessage(
            ChatRoom chatRoom,
            Member sender,
            String content,
            MessageType messageType,
            String imageUrl
    ) {
        if (chatRoom == null || sender == null) {
            throw new IllegalArgumentException("채팅방과 발신자는 필수입니다.");
        }

        if (messageType == null) {
            throw new IllegalArgumentException("메시지 종류는 필수입니다.");
        }

        // 텍스트 메시지는 내용이 반드시 있어야 한다.
        if (messageType == MessageType.TEXT
                && (content == null || content.isBlank())) {
            throw new IllegalArgumentException("메시지 내용을 입력해 주세요.");
        }

        if (content != null && content.length() > 2000) {
            throw new IllegalArgumentException(
                    "메시지 내용은 2000자 이하여야 합니다."
            );
        }

        // 사진 메시지는 사진 URL이 반드시 있어야 한다.
        if (messageType == MessageType.IMAGE
                && (imageUrl == null || imageUrl.isBlank())) {
            throw new IllegalArgumentException("사진 경로는 필수입니다.");
        }

        if (imageUrl != null && imageUrl.length() > 500) {
            throw new IllegalArgumentException("사진 경로가 너무 깁니다.");
        }

        // 채팅방 참여자만 메시지를 보낼 수 있다.
        Long senderId = sender.getId();

        boolean isParticipant =
                chatRoom.getMemberLow().getId().equals(senderId)
                        || chatRoom.getMemberHigh().getId().equals(senderId);

        if (!isParticipant) {
            throw new IllegalArgumentException(
                    "해당 채팅방의 참여자만 메시지를 보낼 수 있습니다."
            );
        }

        this.chatRoom = chatRoom;
        this.sender = sender;
        this.messageType = messageType;
        this.imageUrl = imageUrl;

        // 사진 메시지는 내용이 없어도 저장할 수 있다.
        this.content = content == null ? "" : content.trim();
    }

    @PrePersist
    protected void onCreate() {
        this.createdAt = LocalDateTime.now();
    }

    // 메시지 종류
    public enum MessageType {
        TEXT,
        IMAGE
    }
}
