package com.example.devboard.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.ResourceHandlerRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

import java.nio.file.Path;
import java.nio.file.Paths;

@Configuration
public class WebConfig implements WebMvcConfigurer {

    private final Path imageUploadPath;

    // application.yml에 설정된 이미지 저장 경로를 주입
    public WebConfig(@Value("${file.upload-dir}") String uploadDir) {
        this.imageUploadPath = Paths.get(uploadDir)
                .toAbsolutePath()
                .normalize();
    }

    @Override
    public void addResourceHandlers(ResourceHandlerRegistry registry) {
        // 업로드된 이미지 파일을 브라우저에서 접근할 수 있도록 설정
        registry.addResourceHandler("/uploads/post-images/**")
                .addResourceLocations(imageUploadPath.toUri().toString());
    }
}