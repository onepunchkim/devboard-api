package com.example.devboard.repository;

import com.example.devboard.entity.ChatMessage;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface ChatMessageRepository
        extends JpaRepository<ChatMessage, Long> {

    // 특정 채팅방의 메시지를 최신순으로 페이지 단위 조회한다.
    Page<ChatMessage> findByChatRoom_IdOrderByIdDesc(
            Long roomId,
            Pageable pageable
    );

    // 읽음 처리할 메시지가 해당 채팅방에 속하는지 확인한다.
    Optional<ChatMessage> findByIdAndChatRoom_Id(
            Long messageId,
            Long roomId
    );
}
