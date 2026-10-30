package com.example.devboard.entity;

import jakarta.persistence.*;

import java.time.LocalDateTime;

@Entity
@Table(name = "post_images")
public class PostImage {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    // 서버에 저장된 이미지의 경로
    @Column(nullable = false, length = 500)
    private String imagePath;

    // 사용자가 업로드한 원본 파일명
    @Column(nullable = false, length = 255)
    private String originalFilename;

    // 이미지 등록 시간
    @Column(nullable = false, updatable = false)
    private LocalDateTime createdAt;

    // 이미지가 속한 게시글
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "post_id", nullable = false)
    private Post post;

    // JPA 기본 생성자
    protected PostImage() {
    }

    // 이미지 생성자
    public PostImage(String imagePath, String originalFilename, Post post) {
        this.imagePath = imagePath;
        this.originalFilename = originalFilename;
        this.post = post;
    }

    // 이미지 생성 시 등록 시간 설정
    @PrePersist
    protected void onCreate() {
        this.createdAt = LocalDateTime.now();
    }

    public Long getId() {
        return id;
    }

    public String getImagePath() {
        return imagePath;
    }

    public String getOriginalFilename() {
        return originalFilename;
    }

    public LocalDateTime getCreatedAt() {
        return createdAt;
    }

    public Post getPost() {
        return post;
    }
}