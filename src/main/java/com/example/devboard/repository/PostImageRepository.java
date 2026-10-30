package com.example.devboard.repository;

import com.example.devboard.entity.PostImage;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface PostImageRepository extends JpaRepository<PostImage, Long> {

    // 특정 게시글에 등록된 이미지 목록 조회
    List<PostImage> findByPostIdOrderByIdAsc(Long postId);

    // 특정 게시글에 등록된 이미지 개수 조회
    long countByPostId(Long postId);

    // 특정 게시글에 연결된 이미지 정보 삭제
    void deleteByPostId(Long postId);
}