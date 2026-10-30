package com.example.devboard.service;

import com.example.devboard.dto.response.PostResponse;
import com.example.devboard.entity.Member;
import com.example.devboard.entity.Post;
import com.example.devboard.entity.PostImage;
import com.example.devboard.repository.*;
import jakarta.persistence.EntityNotFoundException;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;

@Service
@Transactional(readOnly = true)
public class PostService {

    private final PostRepository postRepository;
    private final MemberRepository memberRepository;
    private final CommentRepository commentRepository;
    // 게시글 좋아요 기록을 관리하는 Repository
    private final PostLikeRepository postLikeRepository;
    // 게시글 이미지 정보를 관리하는 Repository
    private final PostImageRepository postImageRepository;
    // 실제 이미지 파일을 삭제하는 서비스
    private final ImageStorageService imageStorageService;

    public PostService(PostRepository postRepository, MemberRepository memberRepository,
                       CommentRepository commentRepository, PostLikeRepository postLikeRepository,
                       PostImageRepository postImageRepository, ImageStorageService imageStorageService) {
        this.postRepository = postRepository;
        this.memberRepository = memberRepository;
        this.commentRepository = commentRepository;
        this.postLikeRepository = postLikeRepository;
        this.postImageRepository = postImageRepository;
        this.imageStorageService = imageStorageService;
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

    // 게시글 목록 조회 및 검색 - 기본 정렬은 최신순
    @Transactional(readOnly = true)
    public Page<PostResponse> findAll(String keyword, int page, int size) {
        // 기존 호출 방식은 최신순으로 처리
        return findAll(keyword, page, size, "latest");
    }

    // 게시글 목록 조회 및 검색 - 정렬 조건 지원
    @Transactional(readOnly = true)
    public Page<PostResponse> findAll(
            String keyword,
            int page,
            int size,
            String sort
    ) {
        // 페이지 번호와 페이지 크기 검증
        if (page < 0 || size < 1 || size > 100) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "페이지 번호 또는 페이지 크기가 올바르지 않습니다."
            );
        }

        // 정렬 조건에 따라 생성일 오름차순 또는 내림차순 설정
        Sort.Direction direction;

        if ("oldest".equalsIgnoreCase(sort)) {
            direction = Sort.Direction.ASC;  // 오래된 게시글 먼저
        } else if (sort == null || sort.isBlank()
                || "latest".equalsIgnoreCase(sort)) {
            direction = Sort.Direction.DESC; // 최신 게시글 먼저
        } else {
            // 지원하지 않는 정렬 조건은 잘못된 요청으로 처리
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "지원하지 않는 정렬 조건입니다."
            );
        }

        // 게시글 생성일과 ID를 기준으로 정렬
        // 생성 시간이 같을 때도 순서가 일정하도록 ID를 보조 기준으로 사용
        Pageable pageable = PageRequest.of(
                page,
                size,
                Sort.by(
                        new Sort.Order(direction, "createdAt"),
                        new Sort.Order(direction, "id")
                )
        );

        Page<Post> posts;

        if (keyword == null || keyword.isBlank()) {
            // 검색어가 없으면 전체 게시글 조회
            posts = postRepository.findAll(pageable);
        } else {
            // 제목 또는 내용에 검색어가 포함된 게시글 조회
            String searchKeyword = keyword.trim();

            posts = postRepository.findByTitleContainingOrContentContaining(
                    searchKeyword,
                    searchKeyword,
                    pageable
            );
        }

        // Entity를 응답 DTO로 변환
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

    // 게시글 삭제: 작성자만 삭제할 수 있음
    @Transactional
    public void deletePost(Long id, String email) {
        // 1. 게시글 조회
        Post post = findPost(id);
        // 2. 게시글 작성자 확인
        validateOwner(post, email);
        // 3. 해당 게시글의 댓글 삭제
        commentRepository.deleteByPostId(id);
        // 4. 해당 게시글의 좋아요 기록 삭제
        postLikeRepository.deleteByPostId(id);
        // 5. 해당 게시글에 연결된 이미지 정보 조회
        List<PostImage> images =
                postImageRepository.findByPostIdOrderByIdAsc(id);
        // 6. 실제 서버에 저장된 이미지 파일 삭제
        for (PostImage image : images) {
            imageStorageService.deleteImage(image.getImagePath());
        }
        // 7. 이미지 DB 정보 삭제
        postImageRepository.deleteByPostId(id);
        // 8. 모든 하위 데이터 삭제 후 게시글 삭제
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
