package com.example.devboard.service;

import com.example.devboard.dto.response.PostResponse;
import com.example.devboard.entity.Member;
import com.example.devboard.entity.Post;
import com.example.devboard.entity.PostImage;
import com.example.devboard.repository.*;
import jakarta.persistence.EntityNotFoundException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.*;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

import java.util.Collections;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

import static org.mockito.ArgumentMatchers.any;

// Mockito를 사용해 실제 DB와 Spring 서버 없이 단위 테스트
@ExtendWith(MockitoExtension.class)
class PostServiceTest {

    @Mock
    private PostRepository postRepository;
    @Mock
    private MemberRepository memberRepository;
    @Mock
    private CommentRepository commentRepository;
    @Mock
    private PostLikeRepository postLikeRepository;
    @Mock
    private PostImageRepository postImageRepository;
    @Mock
    private ImageStorageService imageStorageService;

    // 위 가짜 객체들을 PostService 생성자에 자동 주입
    @InjectMocks
    private PostService postService;
    private Member member;
    private final String email = "test@test.com";

    @BeforeEach
    void setUp() {
        // 회원 객체 준비
        // 실제 Member 생성자 구조에 맞춰 초기화
        member = mock(Member.class);
    }

    @Test
    void createPost_success() {
        // given: 게시글 생성에 필요한 데이터 준비
        String title = "테스트 제목";
        String content = "테스트 내용";

        // 성공 테스트에서만 회원 정보 설정
        when(member.getId()).thenReturn(1L);
        when(member.getNickname()).thenReturn("테스트회원");

        // 회원 이메일로 회원을 조회하면 회원 객체가 반환되도록 설정
        when(memberRepository.findByEmail(email))
                .thenReturn(Optional.of(member));

        // 게시글 저장 시 전달된 객체를 그대로 반환
        when(postRepository.save(any(Post.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        // when: 게시글 생성 실행
        PostResponse response =
                postService.createPost(title, content, email);

        // then: 반환된 응답 데이터 검증
        assertNotNull(response);
        assertEquals(title, response.title());
        assertEquals(content, response.content());
        assertEquals(1L, response.memberId());
        assertEquals("테스트회원", response.nickname());

        // Repository 호출 횟수 확인
        verify(memberRepository, times(1)).findByEmail(email);
        verify(postRepository, times(1)).save(any(Post.class));
    }

    @Test
    void createPost_memberNotFound() {
        // given: 해당 이메일의 회원이 존재하지 않는 상황
        when(memberRepository.findByEmail(email))
                .thenReturn(Optional.empty());

        // when & then: 회원을 찾지 못하면 예외가 발생해야 함
        assertThrows(
                EntityNotFoundException.class,
                () -> postService.createPost(
                        "테스트 제목",
                        "테스트 내용",
                        email
                )
        );

        // 회원 조회 이후 게시글 저장은 실행되지 않아야 함
        verify(memberRepository, times(1)).findByEmail(email);
        verify(postRepository, never()).save(any(Post.class));
    }

    @Test
    void updatePost_success() {
        // given: 게시글 작성자와 게시글 준비
        String title = "수정된 제목";
        String content = "수정된 내용";

        when(member.getId()).thenReturn(1L);
        when(member.getNickname()).thenReturn("테스트회원");
        when(member.getEmail()).thenReturn(email);

        Post post = new Post("기존 제목", "기존 내용", member);

        when(postRepository.findById(1L))
                .thenReturn(Optional.of(post));

        // when: 작성자가 자신의 게시글 수정
        PostResponse response =
                postService.updatePost(1L, title, content, email);

        // then: 수정된 값 확인
        assertEquals(title, response.title());
        assertEquals(content, response.content());
        assertEquals(1L, response.memberId());

        // 게시글 조회가 한 번 실행됐는지 확인
        verify(postRepository, times(1)).findById(1L);
    }

    @Test
    void updatePost_forbidden() {
        // given: 게시글 작성자와 수정 요청자가 다른 상황
        when(member.getEmail()).thenReturn("owner@test.com");

        Post post = new Post(
                "기존 제목",
                "기존 내용",
                member
        );

        when(postRepository.findById(1L))
                .thenReturn(Optional.of(post));

        // when & then: 다른 회원이 수정하면 403 예외 발생
        ResponseStatusException exception = assertThrows(
                ResponseStatusException.class,
                () -> postService.updatePost(
                        1L,
                        "수정 제목",
                        "수정 내용",
                        "other@test.com"
                )
        );

        assertEquals(
                HttpStatus.FORBIDDEN,
                exception.getStatusCode()
        );
    }

    @Test
    void updatePost_postNotFound() {
        // given: 게시글이 존재하지 않는 상황
        when(postRepository.findById(1L))
                .thenReturn(Optional.empty());

        // when & then: 게시글을 찾지 못하면 404 예외 발생
        ResponseStatusException exception = assertThrows(
                ResponseStatusException.class,
                () -> postService.updatePost(
                        1L,
                        "수정 제목",
                        "수정 내용",
                        email
                )
        );

        assertEquals(
                HttpStatus.NOT_FOUND,
                exception.getStatusCode()
        );
    }

    @Test
    void deletePost_success() {
        // given: 게시글 작성자와 게시글 준비
        when(member.getEmail()).thenReturn(email);

        Post post = new Post(
                "삭제할 제목",
                "삭제할 내용",
                member
        );

        when(postRepository.findById(1L))
                .thenReturn(Optional.of(post));

        // 게시글에 연결된 이미지가 없는 상황
        when(postImageRepository.findByPostIdOrderByIdAsc(1L))
                .thenReturn(java.util.Collections.emptyList());

        // when: 작성자가 게시글 삭제
        postService.deletePost(1L, email);

        // then: 댓글, 좋아요, 이미지 정보, 게시글 삭제가 실행됐는지 확인
        verify(commentRepository, times(1)).deleteByPostId(1L);
        verify(postLikeRepository, times(1)).deleteByPostId(1L);
        verify(postImageRepository, times(1))
                .findByPostIdOrderByIdAsc(1L);
        verify(postImageRepository, times(1)).deleteByPostId(1L);
        verify(postRepository, times(1)).delete(post);

        // 이미지가 없으므로 실제 파일 삭제는 호출되지 않아야 함
        verify(imageStorageService, never()).deleteImage(anyString());
    }

    @Test
    void deletePost_forbidden() {
        // given: 게시글 작성자와 삭제 요청자가 다른 상황
        when(member.getEmail()).thenReturn("owner@test.com");

        Post post = new Post(
                "삭제할 제목",
                "삭제할 내용",
                member
        );

        when(postRepository.findById(1L))
                .thenReturn(Optional.of(post));

        // when & then: 다른 회원이 삭제하면 403 예외 발생
        ResponseStatusException exception = assertThrows(
                ResponseStatusException.class,
                () -> postService.deletePost(1L, "other@test.com")
        );

        assertEquals(
                HttpStatus.FORBIDDEN,
                exception.getStatusCode()
        );

        // 권한 검증에 실패했으므로 삭제 작업은 실행되지 않아야 함
        verify(commentRepository, never()).deleteByPostId(1L);
        verify(postLikeRepository, never()).deleteByPostId(1L);
        verify(postRepository, never()).delete(any(Post.class));
    }

    @Test
    void deletePost_postNotFound() {
        // given: 게시글이 존재하지 않는 상황
        when(postRepository.findById(1L))
                .thenReturn(Optional.empty());

        // when & then: 게시글이 없으면 404 예외 발생
        ResponseStatusException exception = assertThrows(
                ResponseStatusException.class,
                () -> postService.deletePost(1L, email)
        );

        assertEquals(
                HttpStatus.NOT_FOUND,
                exception.getStatusCode()
        );

        // 게시글이 없으므로 삭제 작업은 실행되지 않아야 함
        verify(commentRepository, never()).deleteByPostId(1L);
        verify(postLikeRepository, never()).deleteByPostId(1L);
        verify(postRepository, never()).delete(any(Post.class));
    }

    @Test
    void deletePost_withImages_success() {
        // given: 게시글 작성자와 게시글 준비
        when(member.getEmail()).thenReturn(email);

        Post post = new Post(
                "이미지가 포함된 게시글",
                "게시글 내용",
                member
        );

        when(postRepository.findById(1L))
                .thenReturn(Optional.of(post));

        // 게시글에 연결된 이미지 2개 생성
        PostImage image1 = new PostImage(
                "uploads/image1.jpg",
                "사진1.jpg",
                post
        );

        PostImage image2 = new PostImage(
                "uploads/image2.png",
                "사진2.png",
                post
        );

        // 이미지 조회 시 두 이미지가 반환되도록 설정
        when(postImageRepository.findByPostIdOrderByIdAsc(1L))
                .thenReturn(List.of(image1, image2));

        // when: 게시글 삭제 실행
        postService.deletePost(1L, email);

        // then: 실제 이미지 파일 삭제 서비스가 호출됐는지 확인
        verify(imageStorageService, times(1))
                .deleteImage("uploads/image1.jpg");

        verify(imageStorageService, times(1))
                .deleteImage("uploads/image2.png");

        // 이미지 DB 정보 삭제 확인
        verify(postImageRepository, times(1))
                .deleteByPostId(1L);

        // 게시글 삭제 확인
        verify(postRepository, times(1))
                .delete(post);
    }

    @Test
    void findAll_withoutKeyword_success() {
        // given: 검색어 없이 전체 게시글 조회
        Pageable pageable = PageRequest.of(
                0,
                10,
                Sort.by(
                        new Sort.Order(Sort.Direction.DESC, "createdAt"),
                        new Sort.Order(Sort.Direction.DESC, "id")
                )
        );

        when(postRepository.findAll(any(Pageable.class)))
                .thenReturn(new PageImpl<>(Collections.emptyList()));

        // when: 게시글 목록 조회
        Page<PostResponse> result =
                postService.findAll(null, 0, 10);

        // then: 정상적으로 조회됐는지 확인
        assertNotNull(result);
        assertEquals(0, result.getTotalElements());

        verify(postRepository).findAll(any(Pageable.class));
        verify(postRepository, never())
                .findByTitleContainingOrContentContaining(
                        anyString(), anyString(), any(Pageable.class)
                );
    }

    @Test
    void findAll_withKeyword_success() {
        // given: 검색어가 포함된 게시글 조회
        when(postRepository.findByTitleContainingOrContentContaining(
                eq("스프링"),
                eq("스프링"),
                any(Pageable.class)
        )).thenReturn(new PageImpl<>(Collections.emptyList()));

        // when: 검색어로 게시글 조회
        Page<PostResponse> result =
                postService.findAll(" 스프링 ", 0, 10);

        // then: 검색어 양쪽 공백이 제거되어 전달됐는지 확인
        assertNotNull(result);
        assertEquals(0, result.getTotalElements());

        verify(postRepository).findByTitleContainingOrContentContaining(
                eq("스프링"),
                eq("스프링"),
                any(Pageable.class)
        );
    }

    @Test
    void findAll_oldestSort_success() {
        // given: 오래된 게시글부터 조회하도록 설정
        when(postRepository.findAll(any(Pageable.class)))
                .thenReturn(new PageImpl<>(Collections.emptyList()));

        // when: 오래된 순으로 조회
        postService.findAll(null, 0, 10, "oldest");

        // then: 생성일과 ID가 오름차순인지 확인
        ArgumentCaptor<Pageable> captor =
                ArgumentCaptor.forClass(Pageable.class);

        verify(postRepository).findAll(captor.capture());

        Sort sort = captor.getValue().getSort();

        assertEquals(
                Sort.Direction.ASC,
                sort.getOrderFor("createdAt").getDirection()
        );

        assertEquals(
                Sort.Direction.ASC,
                sort.getOrderFor("id").getDirection()
        );
    }

    @Test
    void findAll_invalidPage_throwsException() {
        // given & when: 페이지 번호가 음수인 경우
        ResponseStatusException exception = assertThrows(
                ResponseStatusException.class,
                () -> postService.findAll(null, -1, 10)
        );

        // then: 400 Bad Request 확인
        assertEquals(HttpStatus.BAD_REQUEST, exception.getStatusCode());

        // Repository는 호출되지 않아야 함
        verifyNoInteractions(postRepository);
    }

    @Test
    void findAll_invalidSort_throwsException() {
        // given & when: 지원하지 않는 정렬 조건
        ResponseStatusException exception = assertThrows(
                ResponseStatusException.class,
                () -> postService.findAll(null, 0, 10, "random")
        );

        // then: 400 Bad Request 확인
        assertEquals(HttpStatus.BAD_REQUEST, exception.getStatusCode());

        verifyNoInteractions(postRepository);
    }
}