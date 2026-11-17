package com.example.devboard.repository;

import com.example.devboard.dto.response.PostResponse;
import com.example.devboard.entity.Member;
import com.example.devboard.entity.Post;
import jakarta.persistence.EntityManager;
import jakarta.persistence.EntityManagerFactory;
import org.hibernate.SessionFactory;
import org.hibernate.stat.Statistics;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest
@ActiveProfiles("test")
@Transactional
class PostRepositoryNPlusOneTest {

    @Autowired
    private MemberRepository memberRepository;

    @Autowired
    private PostRepository postRepository;

    @Autowired
    private EntityManager entityManager;

    @Autowired
    private EntityManagerFactory entityManagerFactory;

    @Test
    void measurePostListQueryCount() {

        // given: 서로 다른 작성자 10명과 게시글 10개 생성
        for (int i = 1; i <= 10; i++) {

            Member member = new Member(
                    "nplusone-" + i + "@example.com",
                    "encoded-test-password",
                    "작성자" + i
            );

            memberRepository.save(member);

            Post post = new Post(
                    "N+1 테스트 제목 " + i,
                    "N+1 테스트 내용 " + i,
                    member
            );

            postRepository.save(post);
        }

        // INSERT SQL을 실행하여 테스트 데이터 저장
        entityManager.flush();

        // 영속성 컨텍스트를 비워 1차 캐시의 영향 제거
        entityManager.clear();

        // Hibernate 통계 기능 가져오기
        SessionFactory sessionFactory =
                entityManagerFactory.unwrap(SessionFactory.class);

        Statistics statistics = sessionFactory.getStatistics();

        // SQL 실행 횟수 통계 활성화
        statistics.setStatisticsEnabled(true);

        // 기존 통계 초기화
        statistics.clear();

        // when: 게시글 10개 조회
        Page<Post> posts = postRepository.findAll(
                PageRequest.of(0, 10)
        );

        // 작성자 닉네임까지 접근하도록 DTO 변환
        List<PostResponse> responses = new ArrayList<>();

        for (Post post : posts.getContent()) {
            responses.add(PostResponse.from(post));
        }

        // then: 조회 결과 및 SQL 실행 횟수 확인
        long queryCount =
                statistics.getPrepareStatementCount();

        System.out.println("================================");
        System.out.println("조회한 게시글 수: " + responses.size());
        System.out.println("SQL 실행 횟수: " + queryCount);
        System.out.println("================================");

        assertEquals(10, responses.size());

        // 측정용 테스트이므로 아직 쿼리 횟수를 고정하지 않음
        assertTrue(queryCount > 0);
    }
}
