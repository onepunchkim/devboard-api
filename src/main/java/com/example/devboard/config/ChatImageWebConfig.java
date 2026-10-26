package com.example.devboard.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.ResourceHandlerRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

import java.nio.file.Path;
import java.nio.file.Paths;

@Configuration
public class ChatImageWebConfig implements WebMvcConfigurer {

    private final String uploadDir;

    public ChatImageWebConfig(
            @Value("${chat.image.upload-dir:./uploads/chat}")
            String uploadDir
    ) {
        this.uploadDir = uploadDir;
    }

    @Override
    public void addResourceHandlers(ResourceHandlerRegistry registry) {
        // 업로드 URL을 실제 저장 디렉터리에 연결한다.
        Path directory = Paths.get(uploadDir)
                .toAbsolutePath()
                .normalize();

        String location = directory.toUri().toString();

        registry.addResourceHandler("/uploads/chat/**")
                .addResourceLocations(location)
                .setCachePeriod(3600);
    }
}
