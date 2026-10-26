package com.example.devboard.service;

import com.example.devboard.entity.ChatMessage;
import com.example.devboard.entity.ChatMessage.MessageType;
import com.example.devboard.entity.ChatRoom;
import com.example.devboard.entity.ChatRoomMember;
import com.example.devboard.entity.Member;
import com.example.devboard.repository.ChatMessageRepository;
import com.example.devboard.repository.ChatRoomMemberRepository;
import com.example.devboard.repository.ChatRoomRepository;
import com.example.devboard.repository.MemberRepository;
import jakarta.transaction.Transactional;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.List;

@Service
@Transactional
public class ChatService {

    private final ChatRoomRepository chatRoomRepository;
    private final ChatRoomMemberRepository chatRoomMemberRepository;
    private final MemberRepository memberRepository;
    private final ChatMessageRepository chatMessageRepository;

    public ChatService(
            ChatRoomRepository chatRoomRepository,
            ChatRoomMemberRepository chatRoomMemberRepository,
            MemberRepository memberRepository,
            ChatMessageRepository chatMessageRepository
    ) {
        this.chatRoomRepository = chatRoomRepository;
        this.chatRoomMemberRepository = chatRoomMemberRepository;
        this.memberRepository = memberRepository;
        this.chatMessageRepository = chatMessageRepository;
    }

    // 두 회원 사이의 채팅방을 찾거나 새로 생성한다.
    public ChatRoom createOrGetRoom(Long currentMemberId, Long targetMemberId) {

        // 자기 자신과의 채팅은 허용하지 않는다.
        if (currentMemberId.equals(targetMemberId)) {
            throw new IllegalArgumentException(
                    "자기 자신과의 채팅방은 만들 수 없습니다."
            );
        }

        // DB에 존재하는 회원인지 확인한다.
        Member currentMember = memberRepository.findById(currentMemberId)
                .orElseThrow(() ->
                        new IllegalArgumentException("로그인 회원을 찾을 수 없습니다.")
                );

        Member targetMember = memberRepository.findById(targetMemberId)
                .orElseThrow(() ->
                        new IllegalArgumentException("상대 회원을 찾을 수 없습니다.")
                );

        // 회원 ID 순서대로 기존 채팅방을 찾는다.
        Long lowId = Math.min(currentMemberId, targetMemberId);
        Long highId = Math.max(currentMemberId, targetMemberId);

        return chatRoomRepository
                .findByMemberLow_IdAndMemberHigh_Id(lowId, highId)
                .orElseGet(() -> {
                    ChatRoom room = chatRoomRepository.save(
                            new ChatRoom(currentMember, targetMember)
                    );

                    // 두 회원을 채팅방 참여자로 등록한다.
                    chatRoomMemberRepository.save(
                            new ChatRoomMember(room, currentMember)
                    );
                    chatRoomMemberRepository.save(
                            new ChatRoomMember(room, targetMember)
                    );

                    return room;
                });
    }

    // 로그인한 회원이 참여 중인 채팅방 목록을 조회한다.
    public List<ChatRoomSummary> getMyRooms(Long memberId) {

        if (!memberRepository.existsById(memberId)) {
            throw new IllegalArgumentException("회원 정보를 찾을 수 없습니다.");
        }

        return chatRoomMemberRepository.findByMember_Id(memberId)
                .stream()
                .map(roomMember -> {
                    ChatRoom room = roomMember.getChatRoom();

                    // 로그인한 회원이 아닌 상대 회원을 찾는다.
                    Member partner;

                    if (room.getMemberLow().getId().equals(memberId)) {
                        partner = room.getMemberHigh();
                    } else {
                        partner = room.getMemberLow();
                    }

                    return new ChatRoomSummary(
                            room.getId(),
                            partner.getId(),
                            partner.getNickname(),
                            room.getCreatedAt(),
                            room.getLastMessageAt()
                    );
                })
                .toList();
    }

    // 채팅방 목록 응답 DTO
    public record ChatRoomSummary(
            Long roomId,
            Long partnerMemberId,
            String partnerNickname,
            LocalDateTime createdAt,
            LocalDateTime lastMessageAt
    ) {}

    // 특정 회원이 채팅방 참여자인지 검증한다.
    public ChatRoomMember validateRoomMember(Long roomId, Long memberId) {
        return chatRoomMemberRepository
                .findByChatRoom_IdAndMember_Id(roomId, memberId)
                .orElseThrow(() ->
                        new IllegalArgumentException(
                                "해당 채팅방에 접근할 권한이 없습니다."
                        )
                );
    }

    // 기존 텍스트 메시지를 저장한다.
    public ChatMessageResponse sendMessage(
            Long roomId,
            Long senderId,
            String content
    ) {
        // 기존 텍스트 메시지 기능을 유지한다.
        return saveMessage(
                roomId,
                senderId,
                content,
                MessageType.TEXT,
                null
        );
    }

    // 사진 메시지를 저장한다.
    // imageUrl은 서버에 업로드된 사진의 경로를 전달받는다.
    public ChatMessageResponse sendImageMessage(
            Long roomId,
            Long senderId,
            String imageUrl,
            String caption
    ) {
        if (imageUrl == null || imageUrl.isBlank()) {
            throw new IllegalArgumentException("사진 경로가 필요합니다.");
        }

        // 사진 메시지도 일반 메시지와 동일한 권한 검증 및 저장 절차를 거친다.
        return saveMessage(
                roomId,
                senderId,
                caption,
                MessageType.IMAGE,
                imageUrl
        );
    }

    // 텍스트 및 사진 메시지의 공통 저장 처리
    private ChatMessageResponse saveMessage(
            Long roomId,
            Long senderId,
            String content,
            MessageType messageType,
            String imageUrl
    ) {
        // 발신자가 채팅방 참여자인지 확인한다.
        validateRoomMember(roomId, senderId);

        // 채팅방을 조회한다.
        ChatRoom room = chatRoomRepository.findById(roomId)
                .orElseThrow(() ->
                        new IllegalArgumentException("채팅방을 찾을 수 없습니다.")
                );

        // 발신자 정보를 조회한다.
        Member sender = memberRepository.findById(senderId)
                .orElseThrow(() ->
                        new IllegalArgumentException("회원 정보를 찾을 수 없습니다.")
                );

        // 메시지를 생성하고 저장한다.
        ChatMessage message = new ChatMessage(
                room,
                sender,
                content,
                messageType,
                imageUrl
        );

        ChatMessage savedMessage = chatMessageRepository.save(message);

        // 마지막 메시지 시간을 갱신한다.
        room.updateLastMessageAt(savedMessage.getCreatedAt());

        // 실시간 전송 및 API 응답에 사용할 DTO를 반환한다.
        return toResponse(savedMessage);
    }

    // 채팅 기록을 페이지 단위로 조회한다.
    public Page<ChatMessageResponse> getMessages(
            Long roomId,
            Long memberId,
            int page,
            int size
    ) {
        // 채팅방 참여자만 조회할 수 있다.
        validateRoomMember(roomId, memberId);

        if (page < 0) {
            throw new IllegalArgumentException(
                    "페이지 번호는 0 이상이어야 합니다."
            );
        }

        if (size < 1 || size > 100) {
            throw new IllegalArgumentException(
                    "페이지 크기는 1~100 사이여야 합니다."
            );
        }

        Pageable pageable = PageRequest.of(
                page,
                size,
                Sort.by(Sort.Direction.DESC, "id")
        );

        // 최신순으로 조회한 후 메시지 DTO로 변환한다.
        return chatMessageRepository
                .findByChatRoom_IdOrderByIdDesc(roomId, pageable)
                .map(this::toResponse);
    }

    // 엔티티를 응답 DTO로 변환한다.
    private ChatMessageResponse toResponse(ChatMessage message) {
        return new ChatMessageResponse(
                message.getId(),
                message.getChatRoom().getId(),
                message.getSender().getId(),
                message.getSender().getNickname(),
                message.getContent(),
                message.getMessageType(),
                message.getImageUrl(),
                message.getCreatedAt()
        );
    }

    // 메시지 응답 DTO
    public record ChatMessageResponse(
            Long messageId,
            Long roomId,
            Long senderId,
            String senderNickname,
            String content,
            MessageType messageType,
            String imageUrl,
            LocalDateTime createdAt
    ) {}
}
