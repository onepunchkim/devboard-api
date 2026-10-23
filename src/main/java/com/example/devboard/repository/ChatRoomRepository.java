package com.example.devboard.repository;

import com.example.devboard.entity.ChatRoom;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface ChatRoomRepository extends JpaRepository<ChatRoom, Long> {

    // 두 회원의 ID 조합으로 기존 1:1 채팅방을 찾는다.
    Optional<ChatRoom> findByMemberLow_IdAndMemberHigh_Id(
            Long memberLowId,
            Long memberHighId
    );
}
