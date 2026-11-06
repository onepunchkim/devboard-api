package com.example.devboard.controller;

import com.example.devboard.service.ChatService;
import com.example.devboard.service.ChatService.ChatMessageResponse;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.InjectMocks;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;

import java.security.Principal;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class ChatWebSocketControllerTest {

    @Mock
    private ChatService chatService;

    @Mock
    private SimpMessagingTemplate messagingTemplate;

    @InjectMocks
    private ChatWebSocketController chatWebSocketController;

    // 인증된 사용자의 Authentication 생성
    private UsernamePasswordAuthenticationToken authentication(Long memberId) {

        UsernamePasswordAuthenticationToken auth =
                new UsernamePasswordAuthenticationToken(
                        "test@test.com",
                        null,
                        List.of()
                );

        // WebSocket 인증 정보에 회원 ID 저장
        auth.setDetails(memberId);

        return auth;
    }

    // 1. 정상 인증된 사용자의 메시지 전송 성공
    @Test
    void sendMessage_success() {

        // given: 메시지를 전송할 채팅방과 사용자
        Long roomId = 10L;
        Long memberId = 1L;

        ChatWebSocketController.SendMessageRequest request =
                new ChatWebSocketController.SendMessageRequest("안녕하세요");

        ChatMessageResponse response = mock(ChatMessageResponse.class);

        when(chatService.sendMessage(roomId, memberId, "안녕하세요"))
                .thenReturn(response);

        // when: WebSocket 메시지 전송
        chatWebSocketController.sendMessage(
                roomId,
                request,
                authentication(memberId)
        );

        // then: 서비스에 메시지 저장 요청
        verify(chatService).sendMessage(
                roomId,
                memberId,
                "안녕하세요"
        );

        // 해당 채팅방 구독자에게 메시지 전송
        verify(messagingTemplate).convertAndSend(
                eq("/topic/chat/rooms/10"),
                same(response)
        );
    }

    // 2. Principal이 null인 경우
    @Test
    void sendMessage_nullPrincipal_shouldThrowException() {

        // given
        ChatWebSocketController.SendMessageRequest request =
                new ChatWebSocketController.SendMessageRequest("테스트");

        // when & then: 인증 정보가 없으므로 예외 발생
        IllegalArgumentException exception =
                assertThrows(
                        IllegalArgumentException.class,
                        () -> chatWebSocketController.sendMessage(
                                10L,
                                request,
                                null
                        )
                );

        assertEquals(
                "로그인한 회원만 메시지를 보낼 수 있습니다.",
                exception.getMessage()
        );

        // 인증 실패 시 서비스와 WebSocket 전송이 실행되지 않아야 함
        verifyNoInteractions(chatService, messagingTemplate);
    }

    // 3. Authentication이 아닌 Principal인 경우
    @Test
    void sendMessage_nonAuthenticationPrincipal_shouldThrowException() {

        // given: Principal이지만 Authentication은 아닌 객체
        Principal principal = () -> "test@test.com";

        ChatWebSocketController.SendMessageRequest request =
                new ChatWebSocketController.SendMessageRequest("테스트");

        // when & then
        assertThrows(
                IllegalArgumentException.class,
                () -> chatWebSocketController.sendMessage(
                        10L,
                        request,
                        principal
                )
        );

        verifyNoInteractions(chatService, messagingTemplate);
    }

    // 4. Authentication에 회원 ID가 없는 경우
    @Test
    void sendMessage_missingMemberId_shouldThrowException() {

        // given: 인증은 되었지만 details에 회원 ID가 없음
        UsernamePasswordAuthenticationToken auth =
                new UsernamePasswordAuthenticationToken(
                        "test@test.com",
                        null,
                        List.of()
                );

        ChatWebSocketController.SendMessageRequest request =
                new ChatWebSocketController.SendMessageRequest("테스트");

        // when & then
        assertThrows(
                IllegalArgumentException.class,
                () -> chatWebSocketController.sendMessage(
                        10L,
                        request,
                        auth
                )
        );

        verifyNoInteractions(chatService, messagingTemplate);
    }

    // 5. 회원 ID가 Long 타입이 아닌 경우
    @Test
    void sendMessage_invalidMemberIdType_shouldThrowException() {

        // given: details에 문자열이 저장된 잘못된 인증 정보
        UsernamePasswordAuthenticationToken auth =
                new UsernamePasswordAuthenticationToken(
                        "test@test.com",
                        null,
                        List.of()
                );

        auth.setDetails("invalid-member-id");

        ChatWebSocketController.SendMessageRequest request =
                new ChatWebSocketController.SendMessageRequest("테스트");

        // when & then
        assertThrows(
                IllegalArgumentException.class,
                () -> chatWebSocketController.sendMessage(
                        10L,
                        request,
                        auth
                )
        );

        verifyNoInteractions(chatService, messagingTemplate);
    }
}
