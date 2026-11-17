package com.example.devboard.controller;

import com.example.devboard.service.ChatImageService;
import com.example.devboard.service.ChatService;
import com.example.devboard.service.ChatService.ChatMessageResponse;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;

import org.springframework.http.CacheControl;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.server.ResponseStatusException;

@RestController
@RequestMapping("/api/chat/rooms/{roomId}/images")

@Tag(
        name = "채팅 이미지 API",
        description = "채팅 이미지 업로드, 실시간 메시지 전달 및 이미지 조회 API"
)
public class ChatImageController {

    private final ChatImageService chatImageService;
    private final ChatService chatService;
    private final SimpMessagingTemplate messagingTemplate;

    // 필요한 서비스들을 생성자로 주입
    public ChatImageController(
            ChatImageService chatImageService,
            ChatService chatService,
            SimpMessagingTemplate messagingTemplate
    ) {
        this.chatImageService = chatImageService;
        this.chatService = chatService;
        this.messagingTemplate = messagingTemplate;
    }

    // =====================================================
    // 인증된 회원 ID 조회
    // =====================================================

    /**
     * JWT 인증 정보에서 현재 로그인한 회원 ID를 가져온다.
     * JWT 필터가 Authentication details에
     * Long 타입 회원 ID를 저장하는 구조를 전제로 한다.
     */
    private Long getAuthenticatedMemberId(
            Authentication authentication
    ) {

        // 인증 정보가 없거나 인증되지 않은 경우
        if (authentication == null
                || !authentication.isAuthenticated()) {

            throw new ResponseStatusException(
                    HttpStatus.UNAUTHORIZED,
                    "로그인이 필요합니다."
            );
        }

        // JWT 인증 정보에 저장된 회원 ID 확인
        Object details = authentication.getDetails();

        // 회원 ID가 없거나 Long 타입이 아닌 경우
        if (!(details instanceof Long memberId)) {

            throw new ResponseStatusException(
                    HttpStatus.UNAUTHORIZED,
                    "유효하지 않은 인증 정보입니다."
            );
        }

        return memberId;
    }

    // =====================================================
    // 채팅 이미지 업로드
    // =====================================================

    @Operation(
            summary = "채팅 이미지 업로드",
            description = """
                    로그인한 채팅방 참여자가 이미지를 업로드합니다.

                    1. JWT 인증 정보로 회원을 확인합니다.
                    2. 채팅방 참여 권한을 검증합니다.
                    3. 이미지 파일을 서버에 저장합니다.
                    4. 이미지 메시지를 데이터베이스에 저장합니다.
                    5. WebSocket 구독자에게 이미지 메시지를 전달합니다.

                    WebSocket 전송 경로:
                    /topic/chat/rooms/{roomId}
                    """,
            security = @SecurityRequirement(name = "bearerAuth")
    )
    @ApiResponses({
            @ApiResponse(
                    responseCode = "201",
                    description = "이미지 업로드 및 메시지 저장 성공",
                    content = @Content(
                            mediaType = "application/json",
                            schema = @Schema(
                                    implementation = ChatMessageResponse.class
                            )
                    )
            ),
            @ApiResponse(
                    responseCode = "400",
                    description = "잘못된 이미지 파일 또는 요청"
            ),
            @ApiResponse(
                    responseCode = "401",
                    description = "JWT 인증 필요"
            ),
            @ApiResponse(
                    responseCode = "403",
                    description = "채팅방 참여 권한 없음"
            ),
            @ApiResponse(
                    responseCode = "404",
                    description = "채팅방을 찾을 수 없음"
            )
    })
    @PostMapping(
            consumes = MediaType.MULTIPART_FORM_DATA_VALUE
    )
    public ResponseEntity<ChatMessageResponse> uploadImage(

            @Parameter(
                    description = "이미지를 전송할 채팅방 ID",
                    example = "1"
            )
            @PathVariable Long roomId,

            @Parameter(
                    description = "업로드할 이미지 파일",
                    required = true
            )
            @RequestParam("file")
            MultipartFile file,

            @Parameter(
                    description = "이미지와 함께 전송할 메시지 (선택)",
                    example = "오늘 찍은 사진입니다."
            )
            @RequestParam(required = false)
            String caption,

            // Spring Security 인증 정보는 Swagger에서 숨김
            @Parameter(hidden = true)
            Authentication authentication
    ) {

        // 로그인한 회원 ID 확인
        Long memberId =
                getAuthenticatedMemberId(authentication);

        // 해당 회원이 채팅방 참여자인지 확인
        chatService.validateRoomMember(
                roomId,
                memberId
        );

        // 이미지 파일을 서버에 저장
        String imageUrl =
                chatImageService.upload(file);

        // 이미지 메시지를 DB에 저장
        ChatMessageResponse response =
                chatService.sendImageMessage(
                        roomId,
                        memberId,
                        imageUrl,
                        caption
                );

        // 해당 채팅방 구독자에게 실시간 메시지 전달
        messagingTemplate.convertAndSend(
                "/topic/chat/rooms/" + roomId,
                response
        );

        // 이미지 메시지 응답 반환
        return ResponseEntity
                .status(HttpStatus.CREATED)
                .body(response);
    }

    // =====================================================
    // 채팅 이미지 조회
    // =====================================================

    @Operation(
            summary = "채팅 이미지 조회",
            description = """
                    채팅방에 저장된 이미지를 조회합니다.

                    JWT 인증과 채팅방 참여 권한을 확인한 후
                    이미지 파일을 반환합니다.

                    이미지 형식에 따라 Content-Type이 설정되며,
                    민감한 이미지의 캐시 저장을 방지합니다.
                    """,
            security = @SecurityRequirement(name = "bearerAuth")
    )
    @ApiResponses({
            @ApiResponse(
                    responseCode = "200",
                    description = "이미지 조회 성공",
                    content = @Content(
                            mediaType = "image/*",
                            schema = @Schema(
                                    type = "string",
                                    format = "binary"
                            )
                    )
            ),
            @ApiResponse(
                    responseCode = "400",
                    description = "잘못된 이미지 파일명"
            ),
            @ApiResponse(
                    responseCode = "401",
                    description = "JWT 인증 필요"
            ),
            @ApiResponse(
                    responseCode = "403",
                    description = "채팅방 참여 권한 없음"
            ),
            @ApiResponse(
                    responseCode = "404",
                    description = "이미지 또는 채팅방을 찾을 수 없음"
            )
    })
    @GetMapping("/{filename:.+}")
    public ResponseEntity<byte[]> getImage(

            @Parameter(
                    description = "이미지가 속한 채팅방 ID",
                    example = "1"
            )
            @PathVariable Long roomId,

            @Parameter(
                    description = "조회할 이미지 파일명",
                    example = "example.jpg"
            )
            @PathVariable String filename,

            @Parameter(hidden = true)
            Authentication authentication
    ) {

        // 로그인한 회원 ID 확인
        Long memberId =
                getAuthenticatedMemberId(authentication);

        // 채팅방 참여 권한 확인
        chatService.validateRoomMember(
                roomId,
                memberId
        );

        // 검증된 이미지 파일 읽기
        byte[] imageBytes =
                chatImageService.readImage(filename);

        // 확장자에 맞는 이미지 Content-Type 설정
        MediaType contentType =
                MediaType.parseMediaType(
                        chatImageService.getImageContentType(
                                filename
                        )
                );

        // 민감한 채팅 이미지의 캐시 저장 방지
        return ResponseEntity.ok()
                .contentType(contentType)
                .cacheControl(
                        CacheControl.noStore()
                )
                .body(imageBytes);
    }
}
