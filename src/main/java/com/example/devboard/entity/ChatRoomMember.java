package com.example.devboard.entity;

import jakarta.persistence.*;
import lombok.Getter;

import java.time.LocalDateTime;

@Getter
@Entity
@Table(
        name = "chat_room_members",
        uniqueConstraints = {
                // 한 회원이 같은 채팅방에 중복 등록되지 않도록 제한
                @UniqueConstraint(
                        name = "uk_chat_room_member",
                        columnNames = {"room_id", "member_id"}
                )
        },
        indexes = {
                // 회원별 채팅방 목록 조회를 위한 인덱스
                @Index(name = "idx_chat_room_members_member", columnList = "member_id")
        }
)
public class ChatRoomMember {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    // 참여 중인 채팅방
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "room_id", nullable = false)
    private ChatRoom chatRoom;

    // 채팅방에 참여한 회원
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "member_id", nullable = false)
    private Member member;

    // 마지막으로 읽은 메시지 ID, 아직 읽지 않았다면 null
    private Long lastReadMessageId;

    // 채팅방 참여 등록 시간
    @Column(nullable = false, updatable = false)
    private LocalDateTime joinedAt;

    protected ChatRoomMember() {
    }

    public ChatRoomMember(ChatRoom chatRoom, Member member) {
        if (chatRoom == null || member == null) {
            throw new IllegalArgumentException("채팅방과 회원은 필수입니다.");
        }

        // 실제 채팅방의 두 참여자만 등록할 수 있도록 확인
        Long memberId = member.getId();

        boolean isParticipant =
                chatRoom.getMemberLow().getId().equals(memberId)
                        || chatRoom.getMemberHigh().getId().equals(memberId);

        if (!isParticipant) {
            throw new IllegalArgumentException(
                    "해당 채팅방의 참여자만 등록할 수 있습니다."
            );
        }

        this.chatRoom = chatRoom;
        this.member = member;
    }

    @PrePersist
    protected void onCreate() {
        this.joinedAt = LocalDateTime.now();
    }

    // 회원이 특정 메시지까지 읽었음을 기록한다.
    public void markAsRead(Long messageId) {
        if (messageId == null) {
            throw new IllegalArgumentException("읽은 메시지 ID는 필수입니다.");
        }

        // 읽음 위치가 뒤로 이동하지 않도록 한다.
        if (lastReadMessageId == null || messageId > lastReadMessageId) {
            this.lastReadMessageId = messageId;
        }
    }

}
