package com.example.devboard.repository;

import com.example.devboard.entity.Post;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

public interface PostRepository extends JpaRepository<Post, Long> {

    //제목에 검색어가 포함된 게시글을 페이징 조회
    Page<Post> findByTitleContaining(
            String keyword,
            Pageable pageable
    );
}
