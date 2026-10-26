package com.example.devboard.controller;

import com.example.devboard.service.ChatImageService;
import com.example.devboard.service.ChatService;
import com.example.devboard.service.ChatService.ChatMessageResponse;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

@RestController
@RequestMapping("/api/chat/rooms/{roomId}/images")
public class ChatImageController {

    private final ChatImageService chatImageService;
    private final ChatService chatService;
    private final SimpMessagingTemplate messagingTemplate;

    public ChatImageController(
            ChatImageService chatImageService,
            ChatService chatService,
            SimpMessagingTemplate messagingTemplate
    ) {
        this.chatImageService = chatImageService;
        this.chatService = chatService;
        this.messagingTemplate = messagingTemplate;
    }

    /**
     * 인증된 사용자의 ID를 가져온다.
     * JWT 필터가 Authentication details에 회원 ID를 넣는 구조를 전제로 한다.
     */
    private Long getAuthenticatedMemberId(Authentication authentication) {
        if (authentication == null
                || !authentication.isAuthenticated()
                || !(authentication.getDetails() instanceof Long memberId)) {
            throw new IllegalArgumentException("로그인이 필요합니다.");
        }

        return memberId;
    }

    // 사진을 업로드하고 채팅 메시지로 저장한 뒤 상대방에게 전달한다.
    @PostMapping(consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<ChatMessageResponse> uploadImage(
            @PathVariable Long roomId,
            @RequestParam("file") MultipartFile file,
            @RequestParam(required = false) String caption,
            Authentication authentication
    ) {
        // 로그인한 회원 ID를 확인한다.
        Long memberId = getAuthenticatedMemberId(authentication);

        // 해당 회원이 채팅방 참여자인지 확인한다.
        chatService.validateRoomMember(roomId, memberId);

        // 사진 파일을 서버에 저장한다.
        String imageUrl = chatImageService.upload(file);

        // 사진 메시지를 DB에 저장한다.
        ChatMessageResponse response = chatService.sendImageMessage(
                roomId,
                memberId,
                imageUrl,
                caption
        );

        // 해당 채팅방 구독자들에게 사진 메시지를 실시간 전달한다.
        messagingTemplate.convertAndSend(
                "/topic/chat/rooms/" + roomId,
                response
        );

        return ResponseEntity
                .status(HttpStatus.CREATED)
                .body(response);
    }

    // 채팅방 참여자에게만 사진 파일을 반환한다.
    @GetMapping("/{filename:.+}")
    public ResponseEntity<byte[]> getImage(
            @PathVariable Long roomId,
            @PathVariable String filename,
            Authentication authentication
    ) {
        // 로그인한 회원 ID를 확인한다.
        Long memberId = getAuthenticatedMemberId(authentication);

        // 해당 회원이 사진이 속한 채팅방의 참여자인지 확인한다.
        chatService.validateRoomMember(roomId, memberId);

        // 검증된 파일을 읽는다.
        byte[] imageBytes = chatImageService.readImage(filename);

        // 확장자에 맞는 이미지 Content-Type으로 반환한다.
        MediaType contentType = MediaType.parseMediaType(
                chatImageService.getImageContentType(filename)
        );

        return ResponseEntity.ok()
                .contentType(contentType)
                // 민감한 채팅 사진이 공유 캐시에 저장되지 않도록 한다.
                .cacheControl(
                        org.springframework.http.CacheControl.noStore()
                )
                .body(imageBytes);
    }
}
