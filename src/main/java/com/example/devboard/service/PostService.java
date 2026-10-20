package com.example.devboard.service;

import com.example.devboard.dto.response.PostResponse;
import com.example.devboard.entity.Member;
import com.example.devboard.entity.Post;
import com.example.devboard.repository.CommentRepository;
import com.example.devboard.repository.MemberRepository;
import com.example.devboard.repository.PostRepository;
import jakarta.persistence.EntityNotFoundException;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

@Service
@Transactional(readOnly = true)
public class PostService {

    private final PostRepository postRepository;
    private final MemberRepository memberRepository;
    private final CommentRepository commentRepository;

    public PostService(PostRepository postRepository, MemberRepository memberRepository, CommentRepository commentRepository) {
        this.postRepository = postRepository;
        this.memberRepository = memberRepository;
        this.commentRepository = commentRepository;
    }

    //게시글 작성
    @Transactional
    public PostResponse createPost(String title, String content, String email) {
        System.out.println("JWT에서 전달된 이메일: [" + email + "]");

        Member member = memberRepository.findByEmail(email).
                orElseThrow(() -> new EntityNotFoundException("회원을 찾을 수 없습니다."));
        Post post = new Post(title, content, member);
        return PostResponse.from(postRepository.save(post));
    }

    //게시글 목록 조회 - 페이징
    @Transactional(readOnly = true)
    public Page<PostResponse> findAll(String keyword, int page, int size) {
        Pageable pageable = PageRequest.of(page, size,
                Sort.by(Sort.Direction.DESC, "createdAt")
        );

        Page<Post> posts;

        if (keyword == null || keyword.isBlank()) {
            //검색어가 없으면 전체 게시글 조회
            posts = postRepository.findAll(pageable);
        } else {
            //검색어가 있으면 제목으로 검색
            posts = postRepository.findByTitleContaining(
                    keyword.trim(),
                    pageable
            );
        }

        return posts.map(PostResponse::from);
    }

    //게시글 상세
    public PostResponse findById(Long id) {
        Post post = findPost(id);
        return PostResponse.from(post);
    }

    //게시글 수정(작성자만 가능)
    @Transactional
    public PostResponse updatePost(Long id, String title, String content, String email) {
        Post post = findPost(id);
        validateOwner(post, email);

        post.update(title, content);

        //변경감지 (Dirty checking) 으로 업데이트 됨
        return PostResponse.from(post);

    }

    //게시글 삭제(작성자만 가능)
    @Transactional
    public void deletePost(Long id, String email) {
        Post post = findPost(id); //게시글 조회
        validateOwner(post, email); //게시글 작성자 확인

        commentRepository.deleteByPostId(id); //해당 게시글에 댓글을 먼저 삭제

        postRepository.delete(post); //댓글 삭제 후 게시즐 삭제
    }

    private Post findPost(Long id) {
        return postRepository.findById(id)
                .orElseThrow(() -> new ResponseStatusException(
                        HttpStatus.NOT_FOUND,
                        "게시글을 찾을 수 없습니다."
                ));

    }

    // 회원 조회
    private Member findMemberByEmail(String email) {
        return memberRepository.findByEmail(email)
                .orElseThrow(() -> new ResponseStatusException(
                        HttpStatus.UNAUTHORIZED,
                        "인증된 회원을 찾을 수 없습니다."
                ));
    }

    //게시글 조회 공통 logic
    private Post findPostById(Long id) {
        return postRepository.findById(id)
                .orElseThrow(() -> new ResponseStatusException(
                        HttpStatus.NOT_FOUND,
                        "게시글을 찾을 수 없습니다. ID: " + id));
    }

    //작성자 권한 확인
    private void validateOwner(Post post, String email) {
        if (post.getMember() == null || !post.getMember().getEmail().equals(email)) {
            throw new ResponseStatusException(
                    HttpStatus.FORBIDDEN,
                    "본인이 작성한 게시글만 수정하거나 삭제할 수 있습니다."
            );
        }
    }
}
