package com.example.devboard.controller;

import com.example.devboard.service.ChatService;
import com.example.devboard.service.ChatService.ChatMessageResponse;

import org.springframework.messaging.handler.annotation.DestinationVariable;
import org.springframework.messaging.handler.annotation.MessageMapping;
import org.springframework.messaging.handler.annotation.Payload;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Controller;

import java.security.Principal;

@Controller
public class ChatWebSocketController {

    private final ChatService chatService;
    private final SimpMessagingTemplate messagingTemplate;

    public ChatWebSocketController(
            ChatService chatService,
            SimpMessagingTemplate messagingTemplate
    ) {
        this.chatService = chatService;
        this.messagingTemplate = messagingTemplate;
    }

    @MessageMapping("/chat/rooms/{roomId}/messages")
    public void sendMessage(
            @DestinationVariable Long roomId,
            @Payload SendMessageRequest request,
            Principal principal
    ) {
        // 연결 시 인증된 회원 정보만 사용한다.
        if (!(principal instanceof Authentication authentication)
                || !(authentication.getDetails() instanceof Long memberId)) {
            throw new IllegalArgumentException(
                    "로그인한 회원만 메시지를 보낼 수 있습니다."
            );
        }

        // 기존 서비스로 메시지를 저장하고 응답 DTO를 생성한다.
        ChatMessageResponse response = chatService.sendMessage(
                roomId,
                memberId,
                request.content()
        );

        // 해당 채팅방을 구독 중인 사용자들에게 실시간 전송한다.
        messagingTemplate.convertAndSend(
                "/topic/chat/rooms/" + roomId,
                response
        );
    }

    // 클라이언트에서 전달받는 메시지 형식
    public record SendMessageRequest(String content) {}
}