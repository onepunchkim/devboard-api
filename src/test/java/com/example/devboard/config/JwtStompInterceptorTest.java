package com.example.devboard.config;

import com.example.devboard.entity.Member;
import com.example.devboard.repository.MemberRepository;
import com.example.devboard.service.ChatService;
import com.example.devboard.service.JwtService;
import io.jsonwebtoken.Claims;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.messaging.Message;
import org.springframework.messaging.MessageChannel;
import org.springframework.messaging.simp.stomp.StompCommand;
import org.springframework.messaging.simp.stomp.StompHeaderAccessor;
import org.springframework.messaging.support.MessageBuilder;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.authority.SimpleGrantedAuthority;

import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class JwtStompInterceptorTest {

    @Mock
    private JwtService jwtService;

    @Mock
    private MemberRepository memberRepository;

    @Mock
    private ChatService chatService;

    @Mock
    private MessageChannel channel;

    @InjectMocks
    private JwtStompInterceptor interceptor;

    // STOMP 메시지와 헤더를 함께 생성한다.
    private Message<byte[]> message(StompHeaderAccessor accessor) {
        accessor.setLeaveMutable(true);
        return MessageBuilder.createMessage(
                new byte[0],
                accessor.getMessageHeaders()
        );
    }

    // 인증된 회원 정보를 생성한다.
    private Authentication authentication(Long memberId) {
        UsernamePasswordAuthenticationToken authentication =
                new UsernamePasswordAuthenticationToken(
                        "test@test.com",
                        null,
                        List.of(new SimpleGrantedAuthority("ROLE_USER"))
                );

        authentication.setDetails(memberId);
        return authentication;
    }

    // CONNECT 요청에 사용할 JWT Claims를 설정한다.
    private void stubClaims(String token, String subject, String email) {
        Claims claims = mock(Claims.class);

        // 일부 예외 테스트에서 조회되지 않을 수 있으므로 lenient 사용
        lenient().when(claims.getSubject()).thenReturn(subject);
        lenient().when(claims.get("email", String.class)).thenReturn(email);

        when(jwtService.extractClaims(token)).thenReturn(claims);
    }

    // 1. 정상 JWT로 연결하면 인증 정보를 저장한다.
    @Test
    void connect_validToken_shouldAuthenticate() {
        StompHeaderAccessor accessor =
                StompHeaderAccessor.create(StompCommand.CONNECT);

        accessor.addNativeHeader("Authorization", "Bearer valid-token");

        stubClaims("valid-token", "1", "test@test.com");

        Member member = mock(Member.class);
        when(member.getId()).thenReturn(1L);
        when(member.getEmail()).thenReturn("test@test.com");

        when(memberRepository.findById(1L))
                .thenReturn(Optional.of(member));

        Message<byte[]> message = message(accessor);

        assertSame(message, interceptor.preSend(message, channel));

        Authentication result = (Authentication) accessor.getUser();

        assertNotNull(result);
        assertEquals("test@test.com", result.getName());
        assertEquals(1L, result.getDetails());

        assertTrue(result.getAuthorities().stream()
                .anyMatch(a -> a.getAuthority().equals("ROLE_USER")));
    }

    // 2. Authorization 헤더가 없으면 연결을 거부한다.
    @Test
    void connect_missingToken_shouldReject() {
        StompHeaderAccessor accessor =
                StompHeaderAccessor.create(StompCommand.CONNECT);

        Message<byte[]> message = message(accessor);

        IllegalArgumentException exception = assertThrows(
                IllegalArgumentException.class,
                () -> interceptor.preSend(message, channel)
        );

        assertEquals(
                "채팅 연결에는 JWT 인증이 필요합니다.",
                exception.getMessage()
        );

        verifyNoInteractions(jwtService, memberRepository);
    }

    // 3. Bearer 형식이 아니면 연결을 거부한다.
    @Test
    void connect_invalidAuthorizationPrefix_shouldReject() {
        StompHeaderAccessor accessor =
                StompHeaderAccessor.create(StompCommand.CONNECT);

        accessor.addNativeHeader("Authorization", "Basic invalid");

        Message<byte[]> message = message(accessor);

        assertThrows(
                IllegalArgumentException.class,
                () -> interceptor.preSend(message, channel)
        );

        verifyNoInteractions(jwtService);
    }

    // 4. JWT 검증 중 예외가 발생하면 연결을 거부한다.
    @Test
    void connect_invalidJwt_shouldReject() {
        StompHeaderAccessor accessor =
                StompHeaderAccessor.create(StompCommand.CONNECT);

        accessor.addNativeHeader("Authorization", "Bearer invalid-token");

        when(jwtService.extractClaims("invalid-token"))
                .thenThrow(new IllegalArgumentException("Invalid JWT"));

        Message<byte[]> message = message(accessor);

        IllegalArgumentException exception = assertThrows(
                IllegalArgumentException.class,
                () -> interceptor.preSend(message, channel)
        );

        assertEquals(
                "유효하지 않거나 만료된 JWT입니다.",
                exception.getMessage()
        );
    }

    // 5. JWT 회원 ID가 없으면 연결을 거부한다.
    @Test
    void connect_missingSubject_shouldReject() {
        StompHeaderAccessor accessor =
                StompHeaderAccessor.create(StompCommand.CONNECT);

        accessor.addNativeHeader("Authorization", "Bearer missing-subject");

        stubClaims("missing-subject", null, "test@test.com");

        Message<byte[]> message = message(accessor);

        assertThrows(
                IllegalArgumentException.class,
                () -> interceptor.preSend(message, channel)
        );

        verifyNoInteractions(memberRepository);
    }

    // 6. JWT 이메일이 없으면 연결을 거부한다.
    @Test
    void connect_missingEmail_shouldReject() {
        StompHeaderAccessor accessor =
                StompHeaderAccessor.create(StompCommand.CONNECT);

        accessor.addNativeHeader("Authorization", "Bearer missing-email");

        stubClaims("missing-email", "1", null);

        Message<byte[]> message = message(accessor);

        assertThrows(
                IllegalArgumentException.class,
                () -> interceptor.preSend(message, channel)
        );

        verifyNoInteractions(memberRepository);
    }

    // 7. JWT 회원 ID가 숫자가 아니면 연결을 거부한다.
    @Test
    void connect_invalidSubject_shouldReject() {
        StompHeaderAccessor accessor =
                StompHeaderAccessor.create(StompCommand.CONNECT);

        accessor.addNativeHeader("Authorization", "Bearer invalid-id");

        stubClaims("invalid-id", "abc", "test@test.com");

        Message<byte[]> message = message(accessor);

        assertThrows(
                IllegalArgumentException.class,
                () -> interceptor.preSend(message, channel)
        );
    }

    // 8. 탈퇴했거나 존재하지 않는 회원의 연결을 거부한다.
    @Test
    void connect_deletedMember_shouldReject() {
        StompHeaderAccessor accessor =
                StompHeaderAccessor.create(StompCommand.CONNECT);

        accessor.addNativeHeader("Authorization", "Bearer deleted-member");

        stubClaims("deleted-member", "1", "test@test.com");

        when(memberRepository.findById(1L))
                .thenReturn(Optional.empty());

        Message<byte[]> message = message(accessor);

        assertThrows(
                IllegalArgumentException.class,
                () -> interceptor.preSend(message, channel)
        );
    }

    // 9. JWT 이메일과 DB 이메일이 다르면 연결을 거부한다.
    @Test
    void connect_emailMismatch_shouldReject() {
        StompHeaderAccessor accessor =
                StompHeaderAccessor.create(StompCommand.CONNECT);

        accessor.addNativeHeader("Authorization", "Bearer mismatch");

        stubClaims("mismatch", "1", "wrong@test.com");

        Member member = mock(Member.class);
        when(member.getEmail()).thenReturn("real@test.com");

        when(memberRepository.findById(1L))
                .thenReturn(Optional.of(member));

        Message<byte[]> message = message(accessor);

        assertThrows(
                IllegalArgumentException.class,
                () -> interceptor.preSend(message, channel)
        );
    }

    // 10. 인증된 회원이 정상적인 목적지로 메시지를 전송한다.
    @Test
    void send_validDestination_shouldAllow() {
        StompHeaderAccessor accessor =
                StompHeaderAccessor.create(StompCommand.SEND);

        accessor.setUser(authentication(1L));
        accessor.setDestination("/app/chat/rooms/10/messages");

        Message<byte[]> message = message(accessor);

        assertSame(message, interceptor.preSend(message, channel));

        verify(chatService).validateRoomMember(10L, 1L);
    }

    // 11. 인증된 회원이 정상적인 채팅방을 구독한다.
    @Test
    void subscribe_validDestination_shouldAllow() {
        StompHeaderAccessor accessor =
                StompHeaderAccessor.create(StompCommand.SUBSCRIBE);

        accessor.setUser(authentication(1L));
        accessor.setDestination("/topic/chat/rooms/10");

        Message<byte[]> message = message(accessor);

        assertSame(message, interceptor.preSend(message, channel));

        verify(chatService).validateRoomMember(10L, 1L);
    }

    // 12. 인증 정보 없이 메시지를 전송하면 거부한다.
    @Test
    void send_unauthenticated_shouldReject() {
        StompHeaderAccessor accessor =
                StompHeaderAccessor.create(StompCommand.SEND);

        accessor.setDestination("/app/chat/rooms/10/messages");

        Message<byte[]> message = message(accessor);

        assertThrows(
                IllegalArgumentException.class,
                () -> interceptor.preSend(message, channel)
        );

        verifyNoInteractions(chatService);
    }

    // 13. 인증 정보에 회원 ID가 없으면 거부한다.
    @Test
    void send_missingMemberId_shouldReject() {
        StompHeaderAccessor accessor =
                StompHeaderAccessor.create(StompCommand.SEND);

        UsernamePasswordAuthenticationToken authentication =
                new UsernamePasswordAuthenticationToken(
                        "test@test.com",
                        null,
                        List.of(new SimpleGrantedAuthority("ROLE_USER"))
                );

        accessor.setUser(authentication);
        accessor.setDestination("/app/chat/rooms/10/messages");

        Message<byte[]> message = message(accessor);

        assertThrows(
                IllegalArgumentException.class,
                () -> interceptor.preSend(message, channel)
        );

        verifyNoInteractions(chatService);
    }

    // 14. 목적지가 없으면 요청을 거부한다.
    @Test
    void send_missingDestination_shouldReject() {
        StompHeaderAccessor accessor =
                StompHeaderAccessor.create(StompCommand.SEND);

        accessor.setUser(authentication(1L));

        Message<byte[]> message = message(accessor);

        assertThrows(
                IllegalArgumentException.class,
                () -> interceptor.preSend(message, channel)
        );

        verifyNoInteractions(chatService);
    }

    // 15. 허용되지 않은 메시지 목적지를 거부한다.
    @Test
    void send_invalidDestination_shouldReject() {
        StompHeaderAccessor accessor =
                StompHeaderAccessor.create(StompCommand.SEND);

        accessor.setUser(authentication(1L));
        accessor.setDestination("/app/admin");

        Message<byte[]> message = message(accessor);

        assertThrows(
                IllegalArgumentException.class,
                () -> interceptor.preSend(message, channel)
        );

        verifyNoInteractions(chatService);
    }

    // 16. 허용되지 않은 구독 목적지를 거부한다.
    @Test
    void subscribe_invalidDestination_shouldReject() {
        StompHeaderAccessor accessor =
                StompHeaderAccessor.create(StompCommand.SUBSCRIBE);

        accessor.setUser(authentication(1L));
        accessor.setDestination("/topic/admin");

        Message<byte[]> message = message(accessor);

        assertThrows(
                IllegalArgumentException.class,
                () -> interceptor.preSend(message, channel)
        );

        verifyNoInteractions(chatService);
    }

    // 17. 채팅방 참여자가 아니면 메시지 전송을 거부한다.
    @Test
    void send_notRoomMember_shouldReject() {
        StompHeaderAccessor accessor =
                StompHeaderAccessor.create(StompCommand.SEND);

        accessor.setUser(authentication(1L));
        accessor.setDestination("/app/chat/rooms/10/messages");

        doThrow(new IllegalArgumentException("채팅방 참여자가 아닙니다."))
                .when(chatService)
                .validateRoomMember(10L, 1L);

        Message<byte[]> message = message(accessor);

        assertThrows(
                IllegalArgumentException.class,
                () -> interceptor.preSend(message, channel)
        );

        verify(chatService).validateRoomMember(10L, 1L);
    }

    // 18. 채팅방 참여자가 아니면 구독을 거부한다.
    @Test
    void subscribe_notRoomMember_shouldReject() {
        StompHeaderAccessor accessor =
                StompHeaderAccessor.create(StompCommand.SUBSCRIBE);

        accessor.setUser(authentication(1L));
        accessor.setDestination("/topic/chat/rooms/10");

        doThrow(new IllegalArgumentException("채팅방 참여자가 아닙니다."))
                .when(chatService)
                .validateRoomMember(10L, 1L);

        Message<byte[]> message = message(accessor);

        assertThrows(
                IllegalArgumentException.class,
                () -> interceptor.preSend(message, channel)
        );

        verify(chatService).validateRoomMember(10L, 1L);
    }

    // 19. DISCONNECT 명령은 별도의 인증 검사 없이 통과한다.
    @Test
    void disconnect_shouldPassThrough() {
        StompHeaderAccessor accessor =
                StompHeaderAccessor.create(StompCommand.DISCONNECT);

        Message<byte[]> message = message(accessor);

        assertSame(message, interceptor.preSend(message, channel));

        verifyNoInteractions(jwtService, memberRepository, chatService);
    }
}
