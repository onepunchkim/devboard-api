package com.example.devboard.service;

import com.example.devboard.entity.Post;
import com.example.devboard.repository.PostRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@Transactional(readOnly = true)
public class PostService {

    private final PostRepository postRepository;

    public PostService(PostRepository postRepository) {
        this.postRepository = postRepository;
    }

    //게시글 생성
    @Transactional
    public Post createPost(String title, String content) {
        Post post = new Post(title, content);
        return postRepository.save(post);
    }

    //전체 게시글 조회
    public List<Post> getPosts() {
        return postRepository.findAll();
    }

    //특정 게시글 조회
    public Post getPost(Long id) {
        return findPostById(id);
    }

    //게시글 수정
    @Transactional
    public Post updatePost(Long id, String title, String content) {
        Post post = findPostById(id);
        post.update(title, content);

        //변경감지 (Dirty checking) 으로 업데이트 됨
        return post;
    }

    //게시글 삭제
    @Transactional
    public void deletePost(Long id) {
        Post post = findPostById(id);
        postRepository.delete(post);
    }

    //게시글 조회 공통 logic
    private Post findPostById(Long id) {
        return postRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("게시글을 찾을 수 없습니다. ID: " + id));
    }
}
