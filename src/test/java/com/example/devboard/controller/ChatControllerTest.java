package com.example.devboard.controller;

import com.example.devboard.entity.ChatRoom;
import com.example.devboard.entity.Member;
import com.example.devboard.service.ChatService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.util.List;

import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(ChatController.class)
@AutoConfigureMockMvc(addFilters = false)
class ChatControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private ChatService chatService;

    // JWT 인증 정보에 회원 ID가 저장된 상황을 재현한다.
    private UsernamePasswordAuthenticationToken authentication(Long memberId) {

        UsernamePasswordAuthenticationToken authentication =
                new UsernamePasswordAuthenticationToken(
                        "test@test.com",
                        null,
                        List.of()
                );

        authentication.setDetails(memberId);

        return authentication;
    }

    // 채팅방 테스트에 사용할 회원 엔티티를 생성한다.
    private Member mockMember(Long id, String nickname) {

        Member member = mock(Member.class);

        when(member.getId()).thenReturn(id);
        when(member.getNickname()).thenReturn(nickname);

        return member;
    }

    // 1. 채팅방 생성: 현재 회원이 memberLow인 경우
    @Test
    void createRoom_currentMemberIsLow_success() throws Exception {

        // given
        Member low = mockMember(1L, "회원A");
        Member high = mockMember(2L, "회원B");

        ChatRoom room = mock(ChatRoom.class);

        when(room.getId()).thenReturn(10L);
        when(room.getMemberLow()).thenReturn(low);
        when(room.getMemberHigh()).thenReturn(high);

        when(chatService.createOrGetRoom(1L, 2L))
                .thenReturn(room);

        // when & then
        mockMvc.perform(post("/api/chat/rooms")
                        .principal(authentication(1L))
                        .contentType("application/json")
                        .content("""
                                {
                                  "targetMemberId": 2
                                }
                                """))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.roomId").value(10))
                .andExpect(jsonPath("$.partnerMemberId").value(2))
                .andExpect(jsonPath("$.partnerNickname").value("회원B"));

        verify(chatService).createOrGetRoom(1L, 2L);
    }

    // 2. 채팅방 생성: 현재 회원이 memberHigh인 경우
    @Test
    void createRoom_currentMemberIsHigh_success() throws Exception {

        // given
        Member low = mockMember(1L, "회원A");
        Member high = mockMember(2L, "회원B");

        ChatRoom room = mock(ChatRoom.class);

        when(room.getId()).thenReturn(10L);
        when(room.getMemberLow()).thenReturn(low);
        when(room.getMemberHigh()).thenReturn(high);

        when(chatService.createOrGetRoom(2L, 1L))
                .thenReturn(room);

        // when & then
        mockMvc.perform(post("/api/chat/rooms")
                        .principal(authentication(2L))
                        .contentType("application/json")
                        .content("""
                                {
                                  "targetMemberId": 1
                                }
                                """))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.partnerMemberId").value(1))
                .andExpect(jsonPath("$.partnerNickname").value("회원A"));

        verify(chatService).createOrGetRoom(2L, 1L);
    }

    // 3. 내 채팅방 목록 조회
    @Test
    void getMyRooms_success() throws Exception {

        // given: 채팅방이 없는 회원
        when(chatService.getMyRooms(1L))
                .thenReturn(List.of());

        // when & then
        mockMvc.perform(get("/api/chat/rooms")
                        .principal(authentication(1L)))
                .andExpect(status().isOk())
                .andExpect(content().json("[]"));

        verify(chatService).getMyRooms(1L);
    }

    // 4. 메시지 전송
    @Test
    void sendMessage_success() throws Exception {

        // given: 서비스 응답 객체
        ChatService.ChatMessageResponse response =
                mock(ChatService.ChatMessageResponse.class);

        when(chatService.sendMessage(10L, 1L, "안녕하세요"))
                .thenReturn(response);

        // when & then
        mockMvc.perform(post("/api/chat/rooms/10/messages")
                        .principal(authentication(1L))
                        .contentType("application/json")
                        .content("""
                                {
                                  "content": "안녕하세요"
                                }
                                """))
                .andExpect(status().isCreated());

        verify(chatService).sendMessage(
                10L, 1L, "안녕하세요"
        );
    }

    // 5. 채팅 기록 조회: 기본 페이지 설정
    @Test
    void getMessages_defaultPaging_success() throws Exception {

        // given
        when(chatService.getMessages(10L, 1L, 0, 30))
                .thenReturn(new PageImpl<>(
                        List.of(),
                        PageRequest.of(0, 30),
                        0
                ));

        // when & then
        mockMvc.perform(get("/api/chat/rooms/10/messages")
                        .principal(authentication(1L)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.page").value(0))
                .andExpect(jsonPath("$.size").value(30))
                .andExpect(jsonPath("$.totalElements").value(0))
                .andExpect(jsonPath("$.totalPages").value(0))
                .andExpect(jsonPath("$.content").isArray());

        verify(chatService).getMessages(10L, 1L, 0, 30);
    }

    // 6. 채팅 기록 조회: 사용자가 지정한 페이지
    @Test
    void getMessages_customPaging_success() throws Exception {

        // given
        when(chatService.getMessages(10L, 1L, 2, 5))
                .thenReturn(new PageImpl<>(
                        List.of(),
                        PageRequest.of(2, 5),
                        0
                ));

        // when & then
        mockMvc.perform(get("/api/chat/rooms/10/messages")
                        .principal(authentication(1L))
                        .param("page", "2")
                        .param("size", "5"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.page").value(2))
                .andExpect(jsonPath("$.size").value(5));

        verify(chatService).getMessages(10L, 1L, 2, 5);
    }

    // 7. 회원 ID가 인증 정보에 없는 경우
    @Test
    void getMyRooms_missingMemberId_shouldReturn409() throws Exception {

        // given: details에 회원 ID가 없는 인증 객체
        UsernamePasswordAuthenticationToken auth =
                new UsernamePasswordAuthenticationToken(
                        "test@test.com",
                        null,
                        List.of()
                );

        // when & then
        mockMvc.perform(get("/api/chat/rooms")
                        .principal(auth))
                .andExpect(status().isConflict());

        verifyNoInteractions(chatService);
    }

    // 8. 잘못된 채팅방 ID
    @Test
    void getMessages_invalidRoomId_shouldReturn400() throws Exception {

        // when & then
        mockMvc.perform(get("/api/chat/rooms/abc/messages")
                        .principal(authentication(1L)))
                .andExpect(status().isBadRequest());

        verifyNoInteractions(chatService);
    }

    // 9. 잘못된 페이지 번호 형식
    @Test
    void getMessages_invalidPage_shouldReturn400() throws Exception {

        // when & then
        mockMvc.perform(get("/api/chat/rooms/10/messages")
                        .principal(authentication(1L))
                        .param("page", "abc"))
                .andExpect(status().isBadRequest());

        verifyNoInteractions(chatService);
    }


    // 10. 인증 정보가 없는 경우 HTTP 401 반환
    @Test
    void getMyRooms_noAuthentication_shouldReturn401() throws Exception {

        // given: 인증 정보를 전달하지 않음

        // when & then: 인증 정보가 없으므로 401 반환
        mockMvc.perform(get("/api/chat/rooms"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.status").value(401))
                .andExpect(jsonPath("$.message").value("로그인이 필요합니다."));

        // 인증에 실패했으므로 서비스는 호출되지 않아야 한다.
        verifyNoInteractions(chatService);
    }

    // 11. 인증되지 않은 사용자 요청 시 HTTP 401 반환
    @Test
    void getMyRooms_unauthenticated_shouldReturn401() throws Exception {

        // given: 인증되지 않은 Authentication 객체
        UsernamePasswordAuthenticationToken authentication =
                new UsernamePasswordAuthenticationToken(
                        "test@test.com",
                        "password"
                );

        // when & then
        mockMvc.perform(get("/api/chat/rooms")
                        .principal(authentication))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.status").value(401))
                .andExpect(jsonPath("$.message").value("로그인이 필요합니다."));

        verifyNoInteractions(chatService);
    }

}
