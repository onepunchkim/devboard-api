
package com.example.devboard.service;

import com.example.devboard.entity.ChatMessage;
import com.example.devboard.entity.ChatRoom;
import com.example.devboard.entity.Member;
import com.example.devboard.repository.ChatMessageRepository;
import com.example.devboard.repository.ChatRoomMemberRepository;
import com.example.devboard.repository.ChatRoomRepository;
import com.example.devboard.repository.MemberRepository;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest
@ActiveProfiles("test")
@Transactional
class ChatServiceIntegrationTest {

    @Autowired
    private ChatService chatService;

    @Autowired
    private MemberRepository memberRepository;

    @Autowired
    private ChatRoomRepository chatRoomRepository;

    @Autowired
    private ChatRoomMemberRepository chatRoomMemberRepository;

    @Autowired
    private ChatMessageRepository chatMessageRepository;

    @Autowired
    private PasswordEncoder passwordEncoder;

    // 테스트에 사용할 회원
    private Member memberA;
    private Member memberB;
    private Member outsider;

    @BeforeEach
    void setUp() {

        // 첫 번째 회원 생성
        memberA = new Member(
                "chatA@test.com",
                passwordEncoder.encode("password123"),
                "채팅회원A"
        );
        memberRepository.save(memberA);

        // 두 번째 회원 생성
        memberB = new Member(
                "chatB@test.com",
                passwordEncoder.encode("password123"),
                "채팅회원B"
        );
        memberRepository.save(memberB);

        // 채팅방에 참여하지 않은 회원
        outsider = new Member(
                "outsider@test.com",
                passwordEncoder.encode("password123"),
                "외부회원"
        );
        memberRepository.save(outsider);
    }

    // 두 회원 사이의 채팅방을 생성하는 공통 메서드
    private ChatRoom createRoom() {
        return chatService.createOrGetRoom(
                memberA.getId(),
                memberB.getId()
        );
    }

    // 1. 새로운 채팅방 생성 테스트
    @Test
    void createOrGetRoomCreatesNewRoom() {

        ChatRoom room = createRoom();

        // 채팅방이 생성되었는지 확인
        assertNotNull(room.getId());

        // 첫 번째 회원이 참여자로 등록되었는지 확인
        assertTrue(chatRoomMemberRepository
                .existsByChatRoom_IdAndMember_Id(
                        room.getId(),
                        memberA.getId()
                ));

        // 두 번째 회원이 참여자로 등록되었는지 확인
        assertTrue(chatRoomMemberRepository
                .existsByChatRoom_IdAndMember_Id(
                        room.getId(),
                        memberB.getId()
                ));
    }

    // 2. 회원 순서를 바꿔도 기존 채팅방을 반환하는지 테스트
    @Test
    void createOrGetRoomReturnsExistingRoom() {

        ChatRoom firstRoom = createRoom();

        // 회원 순서를 반대로 전달
        ChatRoom secondRoom = chatService.createOrGetRoom(
                memberB.getId(),
                memberA.getId()
        );

        // 동일한 채팅방인지 확인
        assertEquals(firstRoom.getId(), secondRoom.getId());

        // 중복 채팅방이 생성되지 않았는지 확인
        assertEquals(1, chatRoomRepository.count());
    }

    // 3. 자기 자신과 채팅방을 만들 수 없는지 테스트
    @Test
    void createOrGetRoomRejectsSameMember() {

        assertThrows(
                IllegalArgumentException.class,
                () -> chatService.createOrGetRoom(
                        memberA.getId(),
                        memberA.getId()
                )
        );
    }

    // 4. 존재하지 않는 회원으로 채팅방을 만들 수 없는지 테스트
    @Test
    void createOrGetRoomRejectsMissingMember() {

        assertThrows(
                IllegalArgumentException.class,
                () -> chatService.createOrGetRoom(
                        memberA.getId(),
                        Long.MAX_VALUE
                )
        );
    }

    // 5. 참여 중인 채팅방 목록 조회 테스트
    @Test
    void getMyRoomsReturnsMyRooms() {

        ChatRoom room = createRoom();

        List<ChatService.ChatRoomSummary> rooms =
                chatService.getMyRooms(memberA.getId());

        assertEquals(1, rooms.size());

        ChatService.ChatRoomSummary summary = rooms.get(0);

        assertEquals(room.getId(), summary.roomId());
        assertEquals(memberB.getId(), summary.partnerMemberId());
        assertEquals(memberB.getNickname(), summary.partnerNickname());
        assertNotNull(summary.createdAt());
    }

    // 6. 반대편 회원도 상대 회원을 올바르게 조회하는지 테스트
    @Test
    void getMyRoomsReturnsCorrectPartnerForOtherMember() {

        createRoom();

        List<ChatService.ChatRoomSummary> rooms =
                chatService.getMyRooms(memberB.getId());

        assertEquals(1, rooms.size());

        assertEquals(
                memberA.getId(),
                rooms.get(0).partnerMemberId()
        );

        assertEquals(
                memberA.getNickname(),
                rooms.get(0).partnerNickname()
        );
    }

    // 7. 존재하지 않는 회원의 채팅방 목록 조회 차단 테스트
    @Test
    void getMyRoomsRejectsMissingMember() {

        assertThrows(
                IllegalArgumentException.class,
                () -> chatService.getMyRooms(Long.MAX_VALUE)
        );
    }

    // 8. 채팅방 참여자 검증 성공 테스트
    @Test
    void validateRoomMemberSucceedsForParticipant() {

        ChatRoom room = createRoom();

        assertDoesNotThrow(
                () -> chatService.validateRoomMember(
                        room.getId(),
                        memberA.getId()
                )
        );
    }

    // 9. 채팅방에 참여하지 않은 회원의 접근 차단 테스트
    @Test
    void validateRoomMemberRejectsOutsider() {

        ChatRoom room = createRoom();

        assertThrows(
                IllegalArgumentException.class,
                () -> chatService.validateRoomMember(
                        room.getId(),
                        outsider.getId()
                )
        );
    }

    // 10. 텍스트 메시지 전송 테스트
    @Test
    void sendMessageSuccessfully() {

        ChatRoom room = createRoom();

        ChatService.ChatMessageResponse response =
                chatService.sendMessage(
                        room.getId(),
                        memberA.getId(),
                        "안녕하세요!"
                );

        // 응답 DTO 검증
        assertNotNull(response.messageId());
        assertEquals(room.getId(), response.roomId());
        assertEquals(memberA.getId(), response.senderId());
        assertEquals(memberA.getNickname(), response.senderNickname());
        assertEquals("안녕하세요!", response.content());
        assertEquals(
                ChatMessage.MessageType.TEXT,
                response.messageType()
        );
        assertNull(response.imageUrl());
        assertNotNull(response.createdAt());

        // DB 저장 여부 확인
        assertEquals(1, chatMessageRepository.count());

        // 마지막 메시지 시간 갱신 확인
        ChatRoom savedRoom = chatRoomRepository
                .findById(room.getId())
                .orElseThrow();

        assertEquals(
                response.createdAt(),
                savedRoom.getLastMessageAt()
        );
    }

    // 11. 채팅방 비참여자의 메시지 전송 차단 테스트
    @Test
    void sendMessageRejectsOutsider() {

        ChatRoom room = createRoom();

        assertThrows(
                IllegalArgumentException.class,
                () -> chatService.sendMessage(
                        room.getId(),
                        outsider.getId(),
                        "접근 시도"
                )
        );

        // 메시지가 저장되지 않았는지 확인
        assertEquals(0, chatMessageRepository.count());
    }

    // 12. 이미지 메시지 전송 테스트
    @Test
    void sendImageMessageSuccessfully() {

        ChatRoom room = createRoom();

        String imageUrl = "/uploads/chat/test-image.png";

        ChatService.ChatMessageResponse response =
                chatService.sendImageMessage(
                        room.getId(),
                        memberA.getId(),
                        imageUrl,
                        "사진입니다."
                );

        assertNotNull(response.messageId());
        assertEquals(room.getId(), response.roomId());
        assertEquals("사진입니다.", response.content());
        assertEquals(
                ChatMessage.MessageType.IMAGE,
                response.messageType()
        );
        assertEquals(imageUrl, response.imageUrl());

        assertEquals(1, chatMessageRepository.count());
    }

    // 13. 이미지 경로가 없으면 이미지 메시지 전송 차단 테스트
    @Test
    void sendImageMessageRejectsBlankImageUrl() {

        ChatRoom room = createRoom();

        assertThrows(
                IllegalArgumentException.class,
                () -> chatService.sendImageMessage(
                        room.getId(),
                        memberA.getId(),
                        " ",
                        "사진 설명"
                )
        );

        assertEquals(0, chatMessageRepository.count());
    }

    // 14. 이미지 설명 없이도 이미지 메시지를 전송할 수 있는지 테스트
    @Test
    void sendImageMessageAllowsEmptyCaption() {

        ChatRoom room = createRoom();

        ChatService.ChatMessageResponse response =
                chatService.sendImageMessage(
                        room.getId(),
                        memberA.getId(),
                        "/uploads/chat/image.png",
                        null
                );

        assertEquals("", response.content());
        assertEquals(
                ChatMessage.MessageType.IMAGE,
                response.messageType()
        );
    }

    // 15. 채팅 기록 조회 테스트
    @Test
    void getMessagesReturnsMessages() {

        ChatRoom room = createRoom();

        // 메시지 세 개 전송
        chatService.sendMessage(
                room.getId(),
                memberA.getId(),
                "첫 번째 메시지"
        );

        chatService.sendMessage(
                room.getId(),
                memberB.getId(),
                "두 번째 메시지"
        );

        chatService.sendMessage(
                room.getId(),
                memberA.getId(),
                "세 번째 메시지"
        );

        var result = chatService.getMessages(
                room.getId(),
                memberA.getId(),
                0,
                10
        );

        // 총 메시지 수 확인
        assertEquals(3, result.getTotalElements());

        // 최신 메시지가 먼저 조회되는지 확인
        assertEquals(
                "세 번째 메시지",
                result.getContent().get(0).content()
        );

        assertEquals(
                "두 번째 메시지",
                result.getContent().get(1).content()
        );
    }

    // 16. 음수 페이지 번호 차단 테스트
    @Test
    void getMessagesRejectsNegativePage() {

        ChatRoom room = createRoom();

        assertThrows(
                IllegalArgumentException.class,
                () -> chatService.getMessages(
                        room.getId(),
                        memberA.getId(),
                        -1,
                        10
                )
        );
    }

    // 17. 페이지 크기 0 차단 테스트
    @Test
    void getMessagesRejectsZeroSize() {

        ChatRoom room = createRoom();

        assertThrows(
                IllegalArgumentException.class,
                () -> chatService.getMessages(
                        room.getId(),
                        memberA.getId(),
                        0,
                        0
                )
        );
    }

    // 18. 페이지 크기 100 초과 차단 테스트
    @Test
    void getMessagesRejectsSizeOver100() {

        ChatRoom room = createRoom();

        assertThrows(
                IllegalArgumentException.class,
                () -> chatService.getMessages(
                        room.getId(),
                        memberA.getId(),
                        0,
                        101
                )
        );
    }

    // 19. 채팅방 비참여자의 메시지 조회 차단 테스트
    @Test
    void getMessagesRejectsOutsider() {

        ChatRoom room = createRoom();

        assertThrows(
                IllegalArgumentException.class,
                () -> chatService.getMessages(
                        room.getId(),
                        outsider.getId(),
                        0,
                        10
                )
        );
    }
}
