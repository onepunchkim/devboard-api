package com.example.devboard.config;

import com.example.devboard.entity.Member;
import com.example.devboard.service.ChatService;
import com.example.devboard.service.JwtService;
import com.example.devboard.repository.MemberRepository;

import io.jsonwebtoken.Claims;

import org.springframework.messaging.Message;
import org.springframework.messaging.MessageChannel;
import org.springframework.messaging.simp.stomp.StompCommand;
import org.springframework.messaging.simp.stomp.StompHeaderAccessor;
import org.springframework.messaging.support.ChannelInterceptor;
import org.springframework.messaging.support.MessageHeaderAccessor;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Component
public class JwtStompInterceptor implements ChannelInterceptor {

    private final JwtService jwtService;
    private final MemberRepository memberRepository;
    private final ChatService chatService;

    public JwtStompInterceptor(
            JwtService jwtService,
            MemberRepository memberRepository,
            ChatService chatService
    ) {
        this.jwtService = jwtService;
        this.memberRepository = memberRepository;
        this.chatService = chatService;
    }

    @Override
    public Message<?> preSend(
            Message<?> message,
            MessageChannel channel
    ) {
        StompHeaderAccessor accessor =
                MessageHeaderAccessor.getAccessor(
                        message,
                        StompHeaderAccessor.class
                );

        if (accessor == null) {
            return message;
        }

        StompCommand command = accessor.getCommand();

        // 1. WebSocket 최초 연결 시 JWT를 검증한다.
        if (StompCommand.CONNECT.equals(command)) {
            authenticate(accessor);
        }

        // 2. 메시지 전송과 채팅방 구독 권한을 검사한다.
        if (StompCommand.SEND.equals(command)
                || StompCommand.SUBSCRIBE.equals(command)) {

            Authentication authentication =
                    getAuthentication(accessor);

            Long memberId = getMemberId(authentication);
            String destination = accessor.getDestination();

            if (destination == null) {
                throw new IllegalArgumentException(
                        "요청 목적지를 확인할 수 없습니다."
                );
            }

            String pattern;

            if (StompCommand.SEND.equals(command)) {
                // 클라이언트가 메시지를 보낼 수 있는 목적지
                pattern = "^/app/chat/rooms/(\\d+)/messages$";
            } else {
                // 클라이언트가 메시지를 구독할 수 있는 목적지
                pattern = "^/topic/chat/rooms/(\\d+)$";
            }

            Matcher matcher =
                    Pattern.compile(pattern).matcher(destination);

            // 허용된 채팅 목적지 외의 요청은 차단한다.
            if (!matcher.matches()) {
                throw new IllegalArgumentException(
                        "허용되지 않은 채팅 목적지입니다."
                );
            }

            Long roomId = Long.valueOf(matcher.group(1));

            // 해당 회원이 실제 채팅방 참여자인지 검사한다.
            chatService.validateRoomMember(roomId, memberId);
        }

        return message;
    }

    // 최초 연결에서 JWT를 검증하고 인증 정보를 설정한다.
    private void authenticate(StompHeaderAccessor accessor) {
        String authorization =
                accessor.getFirstNativeHeader("Authorization");

        if (authorization == null
                || !authorization.startsWith("Bearer ")) {
            throw new IllegalArgumentException(
                    "채팅 연결에는 JWT 인증이 필요합니다."
            );
        }

        String token = authorization.substring(7);

        try {
            Claims claims = jwtService.extractClaims(token);

            String subject = claims.getSubject();
            String email = claims.get("email", String.class);

            if (subject == null || email == null) {
                throw new IllegalArgumentException(
                        "JWT 회원 정보가 올바르지 않습니다."
                );
            }

            Long memberId = Long.valueOf(subject);

            Member member = memberRepository.findById(memberId)
                    .orElseThrow(() ->
                            new IllegalArgumentException(
                                    "회원을 찾을 수 없습니다."
                            )
                    );

            // 토큰에 들어 있는 이메일과 DB 정보를 비교한다.
            if (!member.getEmail().equals(email)) {
                throw new IllegalArgumentException(
                        "JWT 회원 정보가 일치하지 않습니다."
                );
            }

            UsernamePasswordAuthenticationToken authentication =
                    new UsernamePasswordAuthenticationToken(
                            member.getEmail(),
                            null,
                            List.of(
                                    new SimpleGrantedAuthority("ROLE_USER")
                            )
                    );

            // 이후 메시지 처리에서 회원 ID를 사용할 수 있게 한다.
            authentication.setDetails(member.getId());

            accessor.setUser(authentication);

        } catch (Exception e) {
            throw new IllegalArgumentException(
                    "유효하지 않거나 만료된 JWT입니다."
            );
        }
    }

    // STOMP 메시지에 연결된 인증 정보를 가져온다.
    private Authentication getAuthentication(
            StompHeaderAccessor accessor
    ) {
        if (!(accessor.getUser() instanceof Authentication authentication)
                || !authentication.isAuthenticated()) {
            throw new IllegalArgumentException(
                    "로그인이 필요합니다."
            );
        }

        return authentication;
    }

    // 인증 정보에서 회원 ID를 가져온다.
    private Long getMemberId(Authentication authentication) {
        Object details = authentication.getDetails();

        if (!(details instanceof Long memberId)) {
            throw new IllegalArgumentException(
                    "회원 ID를 확인할 수 없습니다."
            );
        }

        return memberId;
    }
}