package com.example.devboard.controller;

import com.example.devboard.entity.Member;
import com.example.devboard.repository.MemberRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.messaging.simp.user.SimpUser;
import org.springframework.messaging.simp.user.SimpUserRegistry;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.util.List;
import java.util.Optional;
import java.util.Set;

import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(OnlineMemberController.class)
@AutoConfigureMockMvc(addFilters = false)
class OnlineMemberControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private MemberRepository memberRepository;

    @MockitoBean
    private SimpUserRegistry simpUserRegistry;

    // 현재 로그인한 회원의 인증 정보 생성
    private UsernamePasswordAuthenticationToken authentication() {
        return new UsernamePasswordAuthenticationToken(
                "me@test.com",
                null,
                List.of()
        );
    }

    // WebSocket에 접속한 사용자 생성
    private SimpUser onlineUser(String email) {

        SimpUser user = mock(SimpUser.class);

        when(user.getName()).thenReturn(email);

        return user;
    }

    // DB에 저장된 회원 정보 생성
    private Member member(Long id, String nickname) {

        Member member = mock(Member.class);

        when(member.getId()).thenReturn(id);
        when(member.getNickname()).thenReturn(nickname);

        return member;
    }

    // 1. 온라인 회원이 없는 경우 빈 목록 반환
    @Test
    void getOnlineMembers_empty_success() throws Exception {

        // given: WebSocket 접속자가 없음
        when(simpUserRegistry.getUsers())
                .thenReturn(Set.of());

        // when & then
        mockMvc.perform(get("/api/members/online")
                        .principal(authentication()))
                .andExpect(status().isOk())
                .andExpect(content().json("[]"));

        // DB 조회가 발생하지 않아야 한다.
        verifyNoInteractions(memberRepository);
    }

    // 2. 본인을 제외하고 다른 온라인 회원 반환
    @Test
    void getOnlineMembers_excludesCurrentMember_success() throws Exception {

        // given: 현재 회원과 다른 회원이 WebSocket에 접속
        SimpUser me = onlineUser("me@test.com");
        SimpUser other = onlineUser("other@test.com");

        when(simpUserRegistry.getUsers())
                .thenReturn(Set.of(me, other));

        // 회원 객체를 먼저 생성하여 중첩 스텁 방지
        Member otherMember = member(2L, "다른회원");

        when(memberRepository.findByEmail("other@test.com"))
                .thenReturn(Optional.of(otherMember));

        // when & then
        mockMvc.perform(get("/api/members/online")
                        .principal(authentication()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(1))
                .andExpect(jsonPath("$[0].memberId").value(2))
                .andExpect(jsonPath("$[0].nickname").value("다른회원"));

        // 본인 정보는 DB에서 조회하지 않아야 함
        verify(memberRepository, never())
                .findByEmail("me@test.com");

        verify(memberRepository)
                .findByEmail("other@test.com");
    }

    // 3. DB에 존재하지 않는 온라인 사용자는 제외
    @Test
    void getOnlineMembers_missingMember_shouldBeExcluded() throws Exception {

        // given: WebSocket에는 접속했지만 DB 회원 정보가 없음
        SimpUser unknown = onlineUser("unknown@test.com");

        when(simpUserRegistry.getUsers())
                .thenReturn(Set.of(unknown));

        when(memberRepository.findByEmail("unknown@test.com"))
                .thenReturn(Optional.empty());

        // when & then
        mockMvc.perform(get("/api/members/online")
                        .principal(authentication()))
                .andExpect(status().isOk())
                .andExpect(content().json("[]"));

        verify(memberRepository)
                .findByEmail("unknown@test.com");
    }

    // 4. 닉네임 기준 대소문자 구분 없이 정렬
    @Test
    void getOnlineMembers_shouldSortByNickname() throws Exception {

        // given: 온라인 사용자 3명
        SimpUser user1 = onlineUser("user1@test.com");
        SimpUser user2 = onlineUser("user2@test.com");
        SimpUser user3 = onlineUser("user3@test.com");

        when(simpUserRegistry.getUsers())
                .thenReturn(Set.of(user1, user2, user3));

        // 회원 객체를 먼저 생성
        Member member1 = member(1L, "Charlie");
        Member member2 = member(2L, "alice");
        Member member3 = member(3L, "Bob");

        // Repository 반환값 설정
        when(memberRepository.findByEmail("user1@test.com"))
                .thenReturn(Optional.of(member1));

        when(memberRepository.findByEmail("user2@test.com"))
                .thenReturn(Optional.of(member2));

        when(memberRepository.findByEmail("user3@test.com"))
                .thenReturn(Optional.of(member3));

        // when & then: 닉네임 기준 정렬 확인
        mockMvc.perform(get("/api/members/online")
                        .principal(authentication()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(3))
                .andExpect(jsonPath("$[0].nickname").value("alice"))
                .andExpect(jsonPath("$[1].nickname").value("Bob"))
                .andExpect(jsonPath("$[2].nickname").value("Charlie"))
                .andExpect(jsonPath("$[0].memberId").value(2))
                .andExpect(jsonPath("$[1].memberId").value(3))
                .andExpect(jsonPath("$[2].memberId").value(1));
    }

    // 5. 닉네임이 null인 회원은 마지막에 정렬
    @Test
    void getOnlineMembers_nullNickname_shouldBeLast() throws Exception {

        // given: 닉네임이 null인 회원과 일반 회원
        SimpUser user1 = onlineUser("user1@test.com");
        SimpUser user2 = onlineUser("user2@test.com");

        when(simpUserRegistry.getUsers())
                .thenReturn(Set.of(user1, user2));

        // 회원 객체를 먼저 생성
        Member member1 = member(1L, null);
        Member member2 = member(2L, "Alice");

        when(memberRepository.findByEmail("user1@test.com"))
                .thenReturn(Optional.of(member1));

        when(memberRepository.findByEmail("user2@test.com"))
                .thenReturn(Optional.of(member2));

        // when & then
        mockMvc.perform(get("/api/members/online")
                        .principal(authentication()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].nickname").value("Alice"))
                .andExpect(jsonPath("$[1].memberId").value(1))
                .andExpect(jsonPath("$[1].nickname")
                        .value(org.hamcrest.Matchers.nullValue()));
    }
}
