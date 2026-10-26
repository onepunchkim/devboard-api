package com.example.devboard.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

/**
 * 채팅 사진의 공개 정적 파일 제공을 비활성화한다.
 *
 * 사진은 ResourceHandler로 직접 공개하지 않고,
 * 이후 인증과 채팅방 참여 여부를 확인하는 컨트롤러로 제공한다.
 */
@Configuration
public class ChatImageWebConfig implements WebMvcConfigurer {
    // 의도적으로 ResourceHandler를 등록하지 않는다.
    // 따라서 /uploads/chat/** 요청은 정적 파일로 직접 제공되지 않는다.
}
