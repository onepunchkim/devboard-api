package com.example.devboard.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.messaging.simp.config.ChannelRegistration;
import org.springframework.messaging.simp.config.MessageBrokerRegistry;
import org.springframework.web.socket.config.annotation.EnableWebSocketMessageBroker;
import org.springframework.web.socket.config.annotation.StompEndpointRegistry;
import org.springframework.web.socket.config.annotation.WebSocketMessageBrokerConfigurer;

@Configuration
@EnableWebSocketMessageBroker
public class WebSocketConfig implements WebSocketMessageBrokerConfigurer {

    private final JwtStompInterceptor jwtStompInterceptor;

    public WebSocketConfig(JwtStompInterceptor jwtStompInterceptor) {
        this.jwtStompInterceptor = jwtStompInterceptor;
    }

    @Override
    public void configureMessageBroker(MessageBrokerRegistry registry) {
        //서버에서 클라이언트로 메시지를 전달할 경로
        registry.enableSimpleBroker("/topic", "/queue");

        //클라이언트가 서버의 메시지 처리기능을 호출할 경로
        registry.setApplicationDestinationPrefixes("/app");

        //특정 사용자에게 보내는 메시지 경로
        registry.setUserDestinationPrefix("/user");
    }

    @Override
    public void registerStompEndpoints(StompEndpointRegistry registry) {
        //브라우저가 WebSocket 연결을 시작할 주소
        registry.addEndpoint("/ws").setAllowedOriginPatterns("http://localhost:*", "http://127.0.0.1:*");
    }

    @Override
    public void configureClientInboundChannel(ChannelRegistration registration) {
        //클라이언트가 보내는 STOMP 메시지에 JWT 인증 처리를 적용한다.
        registration.interceptors(jwtStompInterceptor);
    }
}
