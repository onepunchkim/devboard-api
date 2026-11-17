package com.example.devboard.controller;

import com.example.devboard.service.ChatService;
import com.example.devboard.service.ChatService.ChatMessageResponse;

import io.swagger.v3.oas.annotations.media.Schema;

import org.springframework.messaging.handler.annotation.DestinationVariable;
import org.springframework.messaging.handler.annotation.MessageMapping;
import org.springframework.messaging.handler.annotation.Payload;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Controller;

import java.security.Principal;

/**
 * STOMP 기반 실시간 채팅 메시지 컨트롤러.
 *
 * 클라이언트는 application destination prefix가 적용된
 * /chat/rooms/{roomId}/messages 경로로 메시지를 전송한다.
 *
 * 서버는 메시지를 저장한 뒤
 * /topic/chat/rooms/{roomId} 경로로 발행한다.
 *
 * 주의:
 * @MessageMapping은 일반 REST API가 아니므로
 * Swagger UI에 자동으로 표시되지 않는다.
 */
@Controller
public class ChatWebSocketController {

    private final ChatService chatService;
    private final SimpMessagingTemplate messagingTemplate;

    // 필요한 서비스들을 생성자로 주입
    public ChatWebSocketController(
            ChatService chatService,
            SimpMessagingTemplate messagingTemplate
    ) {
        this.chatService = chatService;
        this.messagingTemplate = messagingTemplate;
    }

    // =====================================================
    // WebSocket 채팅 메시지 전송
    // =====================================================

    /**
     * 로그인한 회원이 채팅방에 메시지를 전송한다.
     *
     * 1. WebSocket 연결에 설정된 인증 정보 확인
     * 2. 인증된 회원 ID 조회
     * 3. ChatService를 통해 메시지 저장
     * 4. 저장된 메시지를 구독자에게 실시간 발행
     *
     * 클라이언트 전송 경로:
     * application prefix + /chat/rooms/{roomId}/messages
     *
     * 서버 발행 경로:
     * /topic/chat/rooms/{roomId}
     */
    @MessageMapping("/chat/rooms/{roomId}/messages")
    public void sendMessage(
            @DestinationVariable Long roomId,
            @Payload SendMessageRequest request,
            Principal principal
    ) {

        // WebSocket 연결 시 인증된 회원 정보만 사용
        if (!(principal instanceof Authentication authentication)
                || !(authentication.getDetails() instanceof Long memberId)) {

            throw new IllegalArgumentException(
                    "로그인한 회원만 메시지를 보낼 수 있습니다."
            );
        }

        // 기존 서비스로 메시지를 저장하고 응답 DTO 생성
        ChatMessageResponse response =
                chatService.sendMessage(
                        roomId,
                        memberId,
                        request.content()
                );

        // 해당 채팅방 구독자들에게 실시간 메시지 전달
        messagingTemplate.convertAndSend(
                "/topic/chat/rooms/" + roomId,
                response
        );
    }

    // =====================================================
    // WebSocket 메시지 요청 DTO
    // =====================================================

    @Schema(description = "WebSocket 채팅 메시지 전송 요청")
    public record SendMessageRequest(

            @Schema(
                    description = "전송할 채팅 메시지 내용",
                    example = "안녕하세요! WebSocket 채팅 테스트입니다.",
                    requiredMode = Schema.RequiredMode.REQUIRED
            )
            String content
    ) {}
}
