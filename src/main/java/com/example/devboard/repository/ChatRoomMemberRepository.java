package com.example.devboard.repository;

import com.example.devboard.entity.ChatRoomMember;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface ChatRoomMemberRepository
        extends JpaRepository<ChatRoomMember, Long> {

    // 특정 회원이 해당 채팅방의 참여자인지 확인한다.
    Optional<ChatRoomMember> findByChatRoom_IdAndMember_Id(
            Long roomId,
            Long memberId
    );

    // 회원이 참여 중인 모든 채팅방 정보를 조회한다.
    List<ChatRoomMember> findByMember_Id(Long memberId);

    // 이미 참여자로 등록되어 있는지 확인한다.
    boolean existsByChatRoom_IdAndMember_Id(
            Long roomId,
            Long memberId
    );
}
