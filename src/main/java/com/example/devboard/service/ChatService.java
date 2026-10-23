package com.example.devboard.service;

import com.example.devboard.entity.ChatMessage;
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
            MemberRepository memberRepository, ChatMessageRepository chatMessageRepository
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

        // 실제 DB에 존재하는 회원인지 확인한다.
        Member currentMember = memberRepository.findById(currentMemberId)
                .orElseThrow(() ->
                        new IllegalArgumentException("로그인 회원을 찾을 수 없습니다.")
                );

        Member targetMember = memberRepository.findById(targetMemberId)
                .orElseThrow(() ->
                        new IllegalArgumentException("상대 회원을 찾을 수 없습니다.")
                );

        // ChatRoom 생성자에서 ID를 정렬하므로 작은 ID를 먼저 전달한다.
        Long lowId = Math.min(currentMemberId, targetMemberId);
        Long highId = Math.max(currentMemberId, targetMemberId);

        // 기존 방이 있으면 재사용한다.
        return chatRoomRepository
                .findByMemberLow_IdAndMemberHigh_Id(lowId, highId)
                .orElseGet(() -> {
                    ChatRoom room = chatRoomRepository.save(
                            new ChatRoom(currentMember, targetMember)
                    );

                    // 두 회원을 참여자로 등록한다.
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

        // 탈퇴했거나 존재하지 않는 회원의 접근을 차단한다.
        if (!memberRepository.existsById(memberId)) {
            throw new IllegalArgumentException("회원 정보를 찾을 수 없습니다.");
        }

        // 로그인한 회원이 참여 중인 채팅방을 조회하고,
        // 상대 회원의 정보만 DTO로 변환해서 반환한다.
        return chatRoomMemberRepository.findByMember_Id(memberId)
                .stream()
                .map(roomMember -> {
                    ChatRoom room = roomMember.getChatRoom();

                    // 채팅방의 두 참여자 중 로그인한 회원이 아닌 사람을 찾는다.
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

    // 채팅방 목록 API에서 반환할 데이터
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

    // 채팅 메시지를 저장하고 DTO로 반환한다.
    public ChatMessageResponse sendMessage(
            Long roomId,
            Long senderId,
            String content
    ) {
        // 발신자가 해당 채팅방의 참여자인지 검증한다.
        validateRoomMember(roomId, senderId);

        // 채팅방 정보를 조회한다.
        ChatRoom room = chatRoomRepository.findById(roomId)
                .orElseThrow(() ->
                        new IllegalArgumentException("채팅방을 찾을 수 없습니다.")
                );

        // 발신자 정보를 조회한다.
        Member sender = memberRepository.findById(senderId)
                .orElseThrow(() ->
                        new IllegalArgumentException("회원 정보를 찾을 수 없습니다.")
                );

        // 메시지를 생성하고 DB에 저장한다.
        ChatMessage message = new ChatMessage(room, sender, content);
        ChatMessage savedMessage = chatMessageRepository.save(message);

        // 채팅방의 마지막 메시지 시간을 갱신한다.
        room.updateLastMessageAt(savedMessage.getCreatedAt());

        // 트랜잭션 안에서 필요한 정보를 DTO에 담는다.
        return new ChatMessageResponse(
                savedMessage.getId(),
                roomId,
                sender.getId(),
                sender.getNickname(),
                savedMessage.getContent(),
                savedMessage.getCreatedAt()
        );
    }

    // 채팅 기록을 페이지 단위로 조회한다.
    public Page<ChatMessageResponse> getMessages(
            Long roomId,
            Long memberId,
            int page,
            int size
    ) {
        // 채팅방 참여자만 기록을 조회할 수 있다.
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

        // 서비스 트랜잭션 안에서 조회하고 DTO로 변환한다.
        return chatMessageRepository
                .findByChatRoom_IdOrderByIdDesc(roomId, pageable)
                .map(message -> new ChatMessageResponse(
                        message.getId(),
                        roomId,
                        message.getSender().getId(),
                        message.getSender().getNickname(),
                        message.getContent(),
                        message.getCreatedAt()
                ));
    }

    // 컨트롤러에 반환할 메시지 DTO
    public record ChatMessageResponse(
            Long messageId,
            Long roomId,
            Long senderId,
            String senderNickname,
            String content,
            LocalDateTime createdAt
    ) {}


}
