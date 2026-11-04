package com.example.devboard.controller;

import com.example.devboard.service.ChatImageService;
import com.example.devboard.service.ChatService;
import com.example.devboard.service.ChatService.ChatMessageResponse;
import jakarta.persistence.EntityNotFoundException;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.util.List;

import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(ChatImageController.class)
@AutoConfigureMockMvc(addFilters = false)
class ChatImageControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private ChatImageService chatImageService;

    @MockitoBean
    private ChatService chatService;

    @MockitoBean
    private SimpMessagingTemplate messagingTemplate;

    // 정상 로그인 사용자 생성
    private UsernamePasswordAuthenticationToken authentication(Long memberId) {

        UsernamePasswordAuthenticationToken authentication =
                new UsernamePasswordAuthenticationToken(
                        "test@test.com",
                        null,
                        List.of()
                );

        // 실제 Controller가 사용하는 회원 ID 저장
        authentication.setDetails(memberId);

        return authentication;
    }

    // 테스트용 PNG 파일 생성
    private MockMultipartFile imageFile() {

        return new MockMultipartFile(
                "file",
                "test.png",
                "image/png",
                new byte[]{1, 2, 3}
        );
    }

    // 1. 이미지 업로드 성공
    @Test
    void uploadImage_success() throws Exception {

        // given
        MockMultipartFile file = imageFile();

        ChatMessageResponse response = mock(ChatMessageResponse.class);

        when(chatImageService.upload(any()))
                .thenReturn("/chat-images/test.png");

        when(chatService.sendImageMessage(
                10L,
                1L,
                "/chat-images/test.png",
                "사진입니다."
        )).thenReturn(response);

        // when & then
        mockMvc.perform(
                        multipart("/api/chat/rooms/10/images")
                                .file(file)
                                .param("caption", "사진입니다.")
                                .principal(authentication(1L))
                )
                .andExpect(status().isCreated());

        // 채팅방 참여자 검증
        verify(chatService).validateRoomMember(10L, 1L);

        // 이미지 저장 확인
        verify(chatImageService).upload(any());

        // 이미지 메시지 DB 저장 확인
        verify(chatService).sendImageMessage(
                10L,
                1L,
                "/chat-images/test.png",
                "사진입니다."
        );

        // WebSocket 구독자에게 전송 확인
        verify(messagingTemplate).convertAndSend(
                eq("/topic/chat/rooms/10"),
                same(response)
        );
    }

    // 2. 설명 없이 이미지 업로드 성공
    @Test
    void uploadImage_withoutCaption_success() throws Exception {

        // given
        ChatMessageResponse response = mock(ChatMessageResponse.class);

        when(chatImageService.upload(any()))
                .thenReturn("/chat-images/test.png");

        when(chatService.sendImageMessage(
                10L, 1L, "/chat-images/test.png", null
        )).thenReturn(response);

        // when & then
        mockMvc.perform(
                        multipart("/api/chat/rooms/10/images")
                                .file(imageFile())
                                .principal(authentication(1L))
                )
                .andExpect(status().isCreated());

        verify(chatService).sendImageMessage(
                10L, 1L, "/chat-images/test.png", null
        );
    }

    // 3. 채팅방 참여자가 아닌 경우 업로드 차단
    @Test
    void uploadImage_notRoomMember_shouldReturn404() throws Exception {

        // given
        doThrow(new EntityNotFoundException("채팅방을 찾을 수 없습니다."))
                .when(chatService)
                .validateRoomMember(10L, 1L);

        // when & then
        mockMvc.perform(
                        multipart("/api/chat/rooms/10/images")
                                .file(imageFile())
                                .principal(authentication(1L))
                )
                .andExpect(status().isNotFound());

        // 권한 검증에 실패하면 파일 저장과 메시지 전송을 수행하지 않는다.
        verifyNoInteractions(chatImageService);
        verifyNoInteractions(messagingTemplate);
        verify(chatService, never()).sendImageMessage(
                anyLong(), anyLong(), anyString(), any()
        );
    }

    // 4. PNG 이미지 조회 성공
    @Test
    void getImage_png_success() throws Exception {

        // given
        byte[] imageBytes = {1, 2, 3, 4};

        when(chatImageService.readImage("test.png"))
                .thenReturn(imageBytes);

        when(chatImageService.getImageContentType("test.png"))
                .thenReturn("image/png");

        // when & then
        mockMvc.perform(
                        get("/api/chat/rooms/10/images/test.png")
                                .principal(authentication(1L))
                )
                .andExpect(status().isOk())
                .andExpect(content().contentType(MediaType.IMAGE_PNG))
                .andExpect(content().bytes(imageBytes))
                .andExpect(header().string(
                        "Cache-Control",
                        org.hamcrest.Matchers.containsString("no-store")
                ));

        verify(chatService).validateRoomMember(10L, 1L);
        verify(chatImageService).readImage("test.png");
    }

    // 5. JPEG 이미지 조회 성공
    @Test
    void getImage_jpeg_success() throws Exception {

        // given
        byte[] imageBytes = {5, 6, 7};

        when(chatImageService.readImage("test.jpg"))
                .thenReturn(imageBytes);

        when(chatImageService.getImageContentType("test.jpg"))
                .thenReturn("image/jpeg");

        // when & then
        mockMvc.perform(
                        get("/api/chat/rooms/10/images/test.jpg")
                                .principal(authentication(1L))
                )
                .andExpect(status().isOk())
                .andExpect(content().contentType(MediaType.IMAGE_JPEG))
                .andExpect(content().bytes(imageBytes));
    }

    // 6. 인증 정보 없이 이미지 조회
    @Test
    void getImage_noAuthentication_shouldReturn401() throws Exception {

        // when & then
        mockMvc.perform(
                        get("/api/chat/rooms/10/images/test.png")
                )
                .andExpect(status().isUnauthorized());

        verifyNoInteractions(chatService, chatImageService);
    }

    // 7. 인증되지 않은 사용자 이미지 조회
    @Test
    void getImage_unauthenticated_shouldReturn401() throws Exception {

        // given
        UsernamePasswordAuthenticationToken auth =
                new UsernamePasswordAuthenticationToken(
                        "test@test.com",
                        "password"
                );

        // when & then
        mockMvc.perform(
                        get("/api/chat/rooms/10/images/test.png")
                                .principal(auth)
                )
                .andExpect(status().isUnauthorized());

        verifyNoInteractions(chatService, chatImageService);
    }

    // 8. 인증 정보에 회원 ID가 없는 경우
    @Test
    void getImage_missingMemberId_shouldReturn401() throws Exception {

        // given: 인증은 되었지만 details에 회원 ID가 없음
        UsernamePasswordAuthenticationToken auth =
                new UsernamePasswordAuthenticationToken(
                        "test@test.com",
                        null,
                        List.of()
                );

        // when & then
        mockMvc.perform(
                        get("/api/chat/rooms/10/images/test.png")
                                .principal(auth)
                )
                .andExpect(status().isUnauthorized());

        verifyNoInteractions(chatService, chatImageService);
    }

    // 9. 잘못된 채팅방 ID
    @Test
    void getImage_invalidRoomId_shouldReturn400() throws Exception {

        // when & then
        mockMvc.perform(
                        get("/api/chat/rooms/abc/images/test.png")
                                .principal(authentication(1L))
                )
                .andExpect(status().isBadRequest());

        verifyNoInteractions(chatService, chatImageService);
    }

    // 10. 이미지 조회 시 채팅방 참여자 검증 실패
    @Test
    void getImage_notRoomMember_shouldReturn404() throws Exception {

        // given
        doThrow(new EntityNotFoundException("채팅방을 찾을 수 없습니다."))
                .when(chatService)
                .validateRoomMember(10L, 1L);

        // when & then
        mockMvc.perform(
                        get("/api/chat/rooms/10/images/test.png")
                                .principal(authentication(1L))
                )
                .andExpect(status().isNotFound());

        // 권한 검증 실패 후 파일을 읽지 않아야 한다.
        verifyNoInteractions(chatImageService);
    }
}
