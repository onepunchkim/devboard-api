package com.example.devboard.entity;

import jakarta.persistence.*;
import lombok.Getter;

import java.time.LocalDateTime;

@Getter
@Entity
@Table(
        name = "chat_messages",
        indexes = {
                // 채팅방의 메시지를 ID 순서로 조회하기 위한 인덱스
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

    // 메시지 본문
    @Column(nullable = false, length = 2000)
    private String content;

    // 메시지 작성 시간
    @Column(nullable = false, updatable = false)
    private LocalDateTime createdAt;

    protected ChatMessage() {
    }

    public ChatMessage(ChatRoom chatRoom, Member sender, String content) {
        if (chatRoom == null || sender == null) {
            throw new IllegalArgumentException("채팅방과 발신자는 필수입니다.");
        }

        if (content == null || content.isBlank()) {
            throw new IllegalArgumentException("메시지 내용을 입력해 주세요.");
        }

        if (content.length() > 2000) {
            throw new IllegalArgumentException("메시지는 2000자 이하여야 합니다.");
        }

        // 채팅방의 두 참여자 중 한 명만 메시지를 보낼 수 있다.
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
        this.content = content.trim();
    }

    @PrePersist
    protected void onCreate() {
        this.createdAt = LocalDateTime.now();
    }

}
