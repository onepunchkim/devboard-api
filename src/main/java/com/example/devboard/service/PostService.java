package com.example.devboard.service;

import com.example.devboard.dto.PostResponse;
import com.example.devboard.entity.Member;
import com.example.devboard.entity.Post;
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

    public PostService(PostRepository postRepository, MemberRepository memberRepository) {
        this.postRepository = postRepository;
        this.memberRepository = memberRepository;
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
    public Page<PostResponse> findAll(int page, int size) {
        Pageable pageable = PageRequest.of(page, size,
                Sort.by(Sort.Direction.DESC, "createdAt")
        );

        return postRepository.findAll(pageable)
                .map(PostResponse::from);
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
        Post post = findPost(id);
        validateOwner(post, email);

        postRepository.delete(post);
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
