package com.example.devboard.controller;

import com.example.devboard.entity.ChatRoom;
import com.example.devboard.entity.Member;
import com.example.devboard.service.ChatService;
import com.example.devboard.service.ChatService.ChatRoomSummary;
import com.example.devboard.service.ChatService.ChatMessageResponse;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;

import org.springframework.data.domain.Page;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

import java.time.LocalDateTime;
import java.util.List;

@RestController
@RequestMapping("/api/chat/rooms")

@Tag(
        name = "채팅 API",
        description = "채팅방 생성, 목록 조회, 메시지 전송 및 채팅 기록 조회 API"
)
public class ChatController {

    private final ChatService chatService;

    // 채팅 서비스 생성자 주입
    public ChatController(ChatService chatService) {
        this.chatService = chatService;
    }

    // =====================================================
    // 채팅방 생성 또는 기존 채팅방 조회
    // =====================================================

    @Operation(
            summary = "채팅방 생성 또는 조회",
            description = "로그인한 회원과 상대 회원 사이의 채팅방을 생성하거나 기존 채팅방을 반환합니다.",
            security = @SecurityRequirement(name = "bearerAuth")
    )
    @ApiResponses({
            @ApiResponse(
                    responseCode = "201",
                    description = "채팅방 생성 또는 기존 채팅방 반환 성공"
            ),
            @ApiResponse(
                    responseCode = "400",
                    description = "잘못된 채팅방 생성 요청"
            ),
            @ApiResponse(
                    responseCode = "401",
                    description = "JWT 인증 필요"
            ),
            @ApiResponse(
                    responseCode = "404",
                    description = "상대 회원을 찾을 수 없음"
            )
    })
    @PostMapping
    public ResponseEntity<ChatRoomResponse> createRoom(

            // 상대 회원 ID를 전달받는 요청 DTO
            @RequestBody CreateChatRoomRequest request,

            // Spring Security 인증 정보는 Swagger 입력란에서 숨김
            @Parameter(hidden = true)
            Authentication authentication
    ) {
        // JWT 인증 정보에서 로그인한 회원 ID 조회
        Long currentMemberId = getCurrentMemberId(authentication);

        // 상대 회원과의 채팅방 생성 또는 기존 방 조회
        ChatRoom room = chatService.createOrGetRoom(
                currentMemberId,
                request.targetMemberId()
        );

        // 현재 회원 ID와 비교하여 상대 회원 확인
        Member partner;

        if (room.getMemberLow().getId().equals(currentMemberId)) {
            partner = room.getMemberHigh();
        } else {
            partner = room.getMemberLow();
        }

        // JPA 엔티티 대신 필요한 정보만 DTO로 반환
        ChatRoomResponse response = new ChatRoomResponse(
                room.getId(),
                partner.getId(),
                partner.getNickname(),
                room.getCreatedAt(),
                room.getLastMessageAt()
        );

        return ResponseEntity
                .status(HttpStatus.CREATED)
                .body(response);
    }

    // =====================================================
    // 내 채팅방 목록 조회
    // =====================================================

    @Operation(
            summary = "내 채팅방 목록 조회",
            description = "현재 로그인한 회원이 참여 중인 채팅방 목록을 조회합니다.",
            security = @SecurityRequirement(name = "bearerAuth")
    )
    @ApiResponses({
            @ApiResponse(
                    responseCode = "200",
                    description = "채팅방 목록 조회 성공"
            ),
            @ApiResponse(
                    responseCode = "401",
                    description = "JWT 인증 필요"
            )
    })
    @GetMapping
    public ResponseEntity<List<ChatRoomSummary>> getMyRooms(

            @Parameter(hidden = true)
            Authentication authentication
    ) {
        // 로그인한 회원 ID 조회
        Long currentMemberId = getCurrentMemberId(authentication);

        // 회원이 참여 중인 채팅방 목록 조회
        List<ChatRoomSummary> rooms =
                chatService.getMyRooms(currentMemberId);

        return ResponseEntity.ok(rooms);
    }

    // =====================================================
    // 채팅 메시지 전송
    // =====================================================

    @Operation(
            summary = "채팅 메시지 전송",
            description = "특정 채팅방에 메시지를 전송하고 저장합니다.",
            security = @SecurityRequirement(name = "bearerAuth")
    )
    @ApiResponses({
            @ApiResponse(
                    responseCode = "201",
                    description = "메시지 전송 성공"
            ),
            @ApiResponse(
                    responseCode = "400",
                    description = "잘못된 메시지 요청"
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
    @PostMapping("/{roomId}/messages")
    public ResponseEntity<ChatMessageResponse> sendMessage(

            @Parameter(
                    description = "메시지를 전송할 채팅방 ID",
                    example = "1"
            )
            @PathVariable Long roomId,

            // 메시지 전송 요청 DTO
            @RequestBody SendMessageRequest request,

            @Parameter(hidden = true)
            Authentication authentication
    ) {
        // JWT 인증 정보에서 회원 ID 조회
        Long memberId = getCurrentMemberId(authentication);

        // 서비스에서 메시지를 저장하고 응답 DTO 반환
        ChatMessageResponse response =
                chatService.sendMessage(
                        roomId,
                        memberId,
                        request.content()
                );

        return ResponseEntity
                .status(HttpStatus.CREATED)
                .body(response);
    }

    // =====================================================
    // 채팅 기록 조회
    // =====================================================

    @Operation(
            summary = "채팅 기록 조회",
            description = "특정 채팅방의 메시지 목록을 페이지 단위로 조회합니다.",
            security = @SecurityRequirement(name = "bearerAuth")
    )
    @ApiResponses({
            @ApiResponse(
                    responseCode = "200",
                    description = "채팅 기록 조회 성공"
            ),
            @ApiResponse(
                    responseCode = "400",
                    description = "잘못된 페이지 요청"
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
    @GetMapping("/{roomId}/messages")
    public ResponseEntity<ChatHistoryResponse> getMessages(

            @Parameter(
                    description = "채팅 기록을 조회할 채팅방 ID",
                    example = "1"
            )
            @PathVariable Long roomId,

            @Parameter(
                    description = "페이지 번호 (0부터 시작)",
                    example = "0"
            )
            @RequestParam(defaultValue = "0")
            int page,

            @Parameter(
                    description = "페이지 크기",
                    example = "30"
            )
            @RequestParam(defaultValue = "30")
            int size,

            @Parameter(hidden = true)
            Authentication authentication
    ) {
        // 현재 로그인한 회원 ID 조회
        Long memberId = getCurrentMemberId(authentication);

        // 서비스에서 페이지 단위 메시지 조회
        Page<ChatMessageResponse> messages =
                chatService.getMessages(
                        roomId,
                        memberId,
                        page,
                        size
                );

        // 메시지 목록과 페이지 정보를 DTO로 변환
        ChatHistoryResponse response = new ChatHistoryResponse(
                messages.getContent(),
                messages.getNumber(),
                messages.getSize(),
                messages.getTotalElements(),
                messages.getTotalPages()
        );

        return ResponseEntity.ok(response);
    }

    // =====================================================
    // JWT 인증 정보에서 회원 ID 조회
    // =====================================================

    private Long getCurrentMemberId(Authentication authentication) {

        // 인증 정보가 없거나 인증되지 않은 경우
        if (authentication == null || !authentication.isAuthenticated()) {
            throw new ResponseStatusException(
                    HttpStatus.UNAUTHORIZED,
                    "로그인이 필요합니다."
            );
        }

        // 인증 정보의 details에 저장된 회원 ID 확인
        Object details = authentication.getDetails();

        if (!(details instanceof Long memberId)) {
            throw new IllegalStateException(
                    "인증 정보에서 회원 ID를 확인할 수 없습니다."
            );
        }

        return memberId;
    }

    // =====================================================
    // 채팅방 생성 요청 DTO
    // =====================================================

    @Schema(description = "채팅방 생성 요청")
    public record CreateChatRoomRequest(

            @Schema(
                    description = "채팅 상대 회원 ID",
                    example = "2",
                    requiredMode = Schema.RequiredMode.REQUIRED
            )
            Long targetMemberId
    ) {}

    // =====================================================
    // 채팅방 생성 응답 DTO
    // =====================================================

    @Schema(description = "채팅방 생성 응답")
    public record ChatRoomResponse(

            @Schema(description = "채팅방 ID", example = "1")
            Long roomId,

            @Schema(description = "상대 회원 ID", example = "2")
            Long partnerMemberId,

            @Schema(description = "상대 회원 닉네임", example = "user2")
            String partnerNickname,

            @Schema(description = "채팅방 생성 시각")
            LocalDateTime createdAt,

            @Schema(description = "마지막 메시지 시각")
            LocalDateTime lastMessageAt
    ) {}

    // =====================================================
    // 채팅 기록 및 페이지 정보 DTO
    // =====================================================

    @Schema(description = "채팅 기록 조회 응답")
    public record ChatHistoryResponse(

            @Schema(description = "메시지 목록")
            List<ChatMessageResponse> content,

            @Schema(description = "현재 페이지 번호", example = "0")
            int page,

            @Schema(description = "페이지 크기", example = "30")
            int size,

            @Schema(description = "전체 메시지 수", example = "100")
            long totalElements,

            @Schema(description = "전체 페이지 수", example = "4")
            int totalPages
    ) {}

    // =====================================================
    // 메시지 전송 요청 DTO
    // =====================================================

    @Schema(description = "채팅 메시지 전송 요청")
    public record SendMessageRequest(

            @Schema(
                    description = "전송할 메시지 내용",
                    example = "안녕하세요! 채팅 테스트입니다.",
                    requiredMode = Schema.RequiredMode.REQUIRED
            )
            String content
    ) {}
}
