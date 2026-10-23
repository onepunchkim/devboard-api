package com.example.devboard.entity;

import jakarta.persistence.*;
import lombok.Getter;

import java.time.LocalDateTime;

@Getter
@Entity
@Table(
        name = "chat_room",
        uniqueConstraints = {
                //동일한 두 회원이 중복 채팅방을 만들지 못하도록 제한
                @UniqueConstraint(name = "uk_chat_room_members",
                        columnNames = {"member_low_id", "member_high_id"}
                )
        }
)
public class ChatRoom {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    //두 회원 중 ID가 작은 회원
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "member_low_id", nullable = false)
    private Member memberLow;

    //두 회원 중 ID가 큰 회원
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "member_high_id", nullable = false)
    private Member memberHigh;

    //채팅방 생성 시간
    @Column(nullable = false, updatable = false)
    private LocalDateTime createdAt;

    //가장 최근 메시지 시간: 채팅방 목록 정렬
    private LocalDateTime lastMessageAt;

    protected ChatRoom(){}

    public ChatRoom(Member memberA, Member memberB) {
        if (memberA == null || memberB == null) {
            throw new IllegalArgumentException("채팅 참여자는 필수입니다.");
        }

        if (memberA.getId().equals(memberB.getId())) {
            throw new IllegalArgumentException("자기 자신과의 채팅방은 만들 수 없습니다.");
        }

        //회원ID를 정렬해 같은 두 회원의 채팅방이 항상 같은 형태로 저장
        if (memberA.getId() < memberB.getId()) {
            this.memberLow = memberA;
            this.memberHigh = memberB;
        } else {
            this.memberLow = memberB;
            this.memberHigh = memberA;
        }
    }

    @PrePersist
    protected void onCreate() {
        this.createdAt = LocalDateTime.now();
    }

    //새 메시지가 저장될 때 마지막 메시지 시간을 갱신
    public void updateLastMessageAt(LocalDateTime messageTime) {
        this.lastMessageAt = messageTime;
    }

}
