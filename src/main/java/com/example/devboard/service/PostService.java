package com.example.devboard.service;

import com.example.devboard.dto.response.PostResponse;
import com.example.devboard.entity.Member;
import com.example.devboard.entity.Post;
import com.example.devboard.entity.PostImage;
import com.example.devboard.exception.BusinessException;
import com.example.devboard.exception.ErrorCode;
import com.example.devboard.repository.*;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

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

    // 실제 이미지 파일을 관리하는 서비스
    private final ImageStorageService imageStorageService;

    public PostService(
            PostRepository postRepository,
            MemberRepository memberRepository,
            CommentRepository commentRepository,
            PostLikeRepository postLikeRepository,
            PostImageRepository postImageRepository,
            ImageStorageService imageStorageService
    ) {
        this.postRepository = postRepository;
        this.memberRepository = memberRepository;
        this.commentRepository = commentRepository;
        this.postLikeRepository = postLikeRepository;
        this.postImageRepository = postImageRepository;
        this.imageStorageService = imageStorageService;
    }

    // 게시글 작성
    @Transactional
    public PostResponse createPost(
            String title,
            String content,
            String email
    ) {
        // JWT에서 확인한 이메일로 회원 조회
        // 회원이 존재하지 않으면 공통 비즈니스 예외 발생
        Member member = memberRepository.findByEmail(email)
                .orElseThrow(() -> new BusinessException(
                        ErrorCode.MEMBER_NOT_FOUND
                ));

        // 게시글 Entity 생성
        Post post = new Post(title, content, member);

        // DB에 저장한 후 응답 DTO로 변환
        return PostResponse.from(postRepository.save(post));
    }

    // 게시글 목록 조회 및 검색
    // 정렬 조건을 전달하지 않으면 최신순으로 조회
    @Transactional(readOnly = true)
    public Page<PostResponse> findAll(
            String keyword,
            int page,
            int size
    ) {
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
        // 페이지 번호는 0 이상
        // 페이지 크기는 1~100 사이만 허용
        if (page < 0 || size < 1 || size > 100) {
            throw new BusinessException(ErrorCode.INVALID_PAGE);
        }

        // 정렬 조건에 따라 오름차순 또는 내림차순 결정
        Sort.Direction direction;

        if ("oldest".equalsIgnoreCase(sort)) {

            // 오래된 게시글부터 조회
            direction = Sort.Direction.ASC;

        } else if (
                sort == null
                        || sort.isBlank()
                        || "latest".equalsIgnoreCase(sort)
        ) {

            // 기본값: 최신 게시글부터 조회
            direction = Sort.Direction.DESC;

        } else {

            // 지원하지 않는 정렬 조건
            throw new BusinessException(ErrorCode.INVALID_SORT);
        }

        // 생성일 기준 정렬
        // 생성 시간이 같으면 ID를 보조 정렬 기준으로 사용
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

            // 검색어 앞뒤 공백 제거
            String searchKeyword = keyword.trim();

            // 제목 또는 내용에 검색어가 포함된 게시글 조회
            posts = postRepository.findByTitleContainingOrContentContaining(
                    searchKeyword,
                    searchKeyword,
                    pageable
            );
        }

        // Entity를 응답 DTO로 변환
        return posts.map(PostResponse::from);
    }

    // 게시글 상세 조회
    public PostResponse findById(Long id) {

        // 공통 메서드로 게시글 조회
        Post post = findPost(id);

        return PostResponse.from(post);
    }

    // 게시글 수정 - 작성자만 가능
    @Transactional
    public PostResponse updatePost(
            Long id,
            String title,
            String content,
            String email
    ) {
        // 게시글 존재 여부 확인
        Post post = findPost(id);

        // 현재 로그인한 사용자가 작성자인지 확인
        validateOwner(post, email);

        // 게시글 내용 변경
        post.update(title, content);

        // JPA Dirty Checking으로 변경 사항 반영
        return PostResponse.from(post);
    }

    // 게시글 삭제 - 작성자만 가능
    @Transactional
    public void deletePost(Long id, String email) {

        // 1. 게시글 조회
        Post post = findPost(id);

        // 2. 작성자 권한 확인
        validateOwner(post, email);

        // 3. 해당 게시글의 댓글 삭제
        commentRepository.deleteByPostId(id);

        // 4. 해당 게시글의 좋아요 기록 삭제
        postLikeRepository.deleteByPostId(id);

        // 5. 연결된 이미지 정보 조회
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

    // 게시글 존재 여부를 확인하는 공통 메서드
    private Post findPost(Long id) {

        return postRepository.findById(id)
                .orElseThrow(() -> new BusinessException(
                        ErrorCode.POST_NOT_FOUND
                ));
    }

    // 게시글 작성자 권한 확인
    private void validateOwner(Post post, String email) {

        // 작성자 정보가 없거나 로그인한 사용자와 다르면 접근 거부
        if (post.getMember() == null
                || !post.getMember().getEmail().equals(email)) {

            throw new BusinessException(
                    ErrorCode.POST_ACCESS_DENIED
            );
        }
    }
}
