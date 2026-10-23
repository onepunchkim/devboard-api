package com.example.devboard.config;

import com.example.devboard.entity.Member;
import com.example.devboard.repository.MemberRepository;
import com.example.devboard.service.JwtService;
import io.jsonwebtoken.Claims;
import org.springframework.messaging.Message;
import org.springframework.messaging.MessageChannel;
import org.springframework.messaging.simp.stomp.StompCommand;
import org.springframework.messaging.simp.stomp.StompHeaderAccessor;
import org.springframework.messaging.support.ChannelInterceptor;
import org.springframework.messaging.support.MessageHeaderAccessor;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.stereotype.Component;

import java.util.List;

@Component
public class JwtStompInterceptor implements ChannelInterceptor {

    private final JwtService jwtService;
    private final MemberRepository memberRepository;

    public JwtStompInterceptor(JwtService jwtService, MemberRepository memberRepository) {
        this.jwtService = jwtService;
        this.memberRepository = memberRepository;
    }

    @Override
    public Message<?> preSend(
            Message<?> message,
            MessageChannel channel
    ) {
        StompHeaderAccessor accessor = MessageHeaderAccessor.getAccessor(message, StompHeaderAccessor.class);

        //WebSocket 연결을 시작하는 CONNECT 프레임에서 인증한다.
        if (accessor != null && StompCommand.CONNECT.equals(accessor.getCommand())) {
            String authorization = accessor.getFirstNativeHeader("Authorization");

            if (authorization == null || !authorization.startsWith("Bearer ")) {
                throw new IllegalArgumentException("채팅 연결에는 JWT 인증이 필요합니다.");
            }

            String token = authorization.substring(7);

            try {
                //JWT 서명 및 만료 시간을 검증
                Claims claims = jwtService.extractClaims(token);

                String subject = claims.getSubject();
                String email = claims.get("email", String.class);

                if (subject == null || email == null) {
                    throw new IllegalArgumentException("토큰에 회원 정보가 없습니다.");
                }

                Long memberId = Long.valueOf(subject);

                //실제 회원이 존재하는지 DB에서 확인
                Member member = memberRepository.findById(memberId)
                        .orElseThrow(() -> new IllegalArgumentException("존재하지 않는 회원입니다."));

                //토큰의 이메일과 DB의 이메일이 일치해야 한다
                if (!member.getEmail().equals(email)) {
                    throw new IllegalArgumentException("회원정보가 일치하지 않습니다.");
                }

                //이후 메시지 처리에서 사용할 인증 정보를 등록
                UsernamePasswordAuthenticationToken authentication = new UsernamePasswordAuthenticationToken(
                        member.getEmail(), null, List.of(new SimpleGrantedAuthority("ROLE_USER"))
                );

                authentication.setDetails(member.getId());
                accessor.setUser(authentication);
            } catch (Exception e) {
                throw new IllegalArgumentException("유효하지 않거나 만료된 JWT입니다.");
            }
        }

        return message;
    }
}
