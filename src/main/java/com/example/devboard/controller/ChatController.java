
package com.example.devboard.controller;

import com.example.devboard.entity.ChatRoom;
import com.example.devboard.service.ChatService.ChatRoomSummary;
import com.example.devboard.service.ChatService.ChatMessageResponse;
import com.example.devboard.entity.Member;
import com.example.devboard.service.ChatService;
import org.springframework.data.domain.Page;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.util.List;

import static com.example.devboard.service.ChatService.*;

@RestController
@RequestMapping("/api/chat/rooms")
public class ChatController {

    private final ChatService chatService;

    public ChatController(ChatService chatService) {
        this.chatService = chatService;
    }

    // 로그인한 회원이 상대 회원과의 채팅방을 생성하거나 기존 방을 조회한다.
    @PostMapping
    public ResponseEntity<ChatRoomResponse> createRoom(
            @RequestBody CreateChatRoomRequest request,
            Authentication authentication
    ) {
        // JWT 인증 정보에서 현재 로그인한 회원의 ID를 가져온다.
        Long currentMemberId = getCurrentMemberId(authentication);

        // 요청으로 받은 상대 회원 ID를 사용한다.
        ChatRoom room = chatService.createOrGetRoom(
                currentMemberId,
                request.targetMemberId()
        );

        // 상대 회원 정보를 구한다.
        Member partner;
        if (room.getMemberLow().getId().equals(currentMemberId)) {
            partner = room.getMemberHigh();
        } else {
            partner = room.getMemberLow();
        }

        // JPA 엔티티 자체 대신 필요한 데이터만 반환한다.
        ChatRoomResponse response = new ChatRoomResponse(
                room.getId(),
                partner.getId(),
                partner.getNickname(),
                room.getCreatedAt(),
                room.getLastMessageAt()
        );

        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    // 로그인한 회원이 참여 중인 채팅방 목록을 조회한다.
    @GetMapping
    public ResponseEntity<List<ChatRoomSummary>> getMyRooms(
            Authentication authentication
    ) {
        Long currentMemberId = getCurrentMemberId(authentication);

        List<ChatRoomSummary> rooms =
                chatService.getMyRooms(currentMemberId);

        return ResponseEntity.ok(rooms);
    }

    // 인증 정보에서 로그인한 회원 ID를 안전하게 가져온다.
    private Long getCurrentMemberId(Authentication authentication) {
        if (authentication == null || !authentication.isAuthenticated()) {
            throw new IllegalArgumentException("로그인이 필요합니다.");
        }

        Object details = authentication.getDetails();

        if (!(details instanceof Long memberId)) {
            throw new IllegalStateException(
                    "인증 정보에서 회원 ID를 확인할 수 없습니다."
            );
        }

        return memberId;
    }

    // 채팅방 생성 요청 DTO
    public record CreateChatRoomRequest(
            Long targetMemberId
    ) {}

    // 채팅방 생성 응답 DTO
    public record ChatRoomResponse(
            Long roomId,
            Long partnerMemberId,
            String partnerNickname,
            java.time.LocalDateTime createdAt,
            java.time.LocalDateTime lastMessageAt
    ) {}

    // 메시지 저장 API
    @PostMapping("/{roomId}/messages")
    public ResponseEntity<ChatService.ChatMessageResponse> sendMessage(
            @PathVariable Long roomId,
            @RequestBody SendMessageRequest request,
            Authentication authentication
    ) {
        // 로그인한 회원 ID를 JWT 인증 정보에서 가져온다.
        Long memberId = getCurrentMemberId(authentication);

        // 서비스에서 메시지를 저장하고 DTO를 반환한다.
        ChatService.ChatMessageResponse response =
                chatService.sendMessage(
                        roomId,
                        memberId,
                        request.content()
                );

        return ResponseEntity
                .status(HttpStatus.CREATED)
                .body(response);
    }


    // 채팅 기록 조회 API
    @GetMapping("/{roomId}/messages")
    public ResponseEntity<ChatHistoryResponse> getMessages(
            @PathVariable Long roomId,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "30") int size,
            Authentication authentication
    ) {
        Long memberId = getCurrentMemberId(authentication);

        Page<ChatMessageResponse> messages =
                chatService.getMessages(roomId, memberId, page, size);

        ChatHistoryResponse response = new ChatHistoryResponse(
                messages.getContent(),
                messages.getNumber(),
                messages.getSize(),
                messages.getTotalElements(),
                messages.getTotalPages()
        );

        return ResponseEntity.ok(response);
    }

    // 채팅 기록 및 페이지 정보 DTO
    public record ChatHistoryResponse(
            List<ChatMessageResponse> content,
            int page,
            int size,
            long totalElements,
            int totalPages
    ) {}

    // 메시지 전송 요청 DTO
    public record SendMessageRequest(
            String content
    ) {}
}
