"use strict";

// 로그인 사용자 정보
const currentUser = getCurrentUser();

// 화면 요소
const currentUserElement = document.getElementById("current-user");
const logoutButton = document.getElementById("logout-button");

const createRoomForm = document.getElementById("create-room-form");
const targetMemberIdInput = document.getElementById("target-member-id");
const refreshRoomsButton = document.getElementById("refresh-rooms-button");
const roomListElement = document.getElementById("room-list");
const roomListStatus = document.getElementById("room-list-status");

const partnerNameElement = document.getElementById("partner-name");
const roomIdLabel = document.getElementById("room-id-label");
const connectionStatus = document.getElementById("connection-status");
const messageListElement = document.getElementById("message-list");
const chatStatusElement = document.getElementById("chat-status");

// 메시지 입력 및 전송 요소
const messageForm = document.getElementById("message-form");
const messageInput = document.getElementById("message-input");
const sendButton = document.getElementById("send-button");

// 사진 선택 및 업로드 요소
const imageButton = document.getElementById("image-button");
const imageInput = document.getElementById("image-input");

// 현재 채팅방과 WebSocket 클라이언트
let currentRoom = null;
let stompClient = null;
let isConnected = false;

// 중복 메시지 표시 방지
const displayedMessageIds = new Set();

// 로그인하지 않았다면 로그인 페이지로 이동
if (!currentUser || !getToken()) {
    window.location.replace("/login.html");
} else {
    currentUserElement.textContent =
        `${currentUser.nickname} (${currentUser.email})`;

    initializeChatPage();
}

// 로그아웃
logoutButton.addEventListener("click", () => {
    disconnectWebSocket();
    logout();
});

// 채팅 페이지 초기화
async function initializeChatPage() {
    if (!window.StompJs) {
        showChatError(
            "실시간 채팅 라이브러리를 불러오지 못했습니다. 인터넷 연결을 확인하세요."
        );
        return;
    }

    await loadChatRooms();

    // 온라인 회원 목록에서 전달된 채팅방 ID
    const roomIdFromUrl = new URLSearchParams(
        window.location.search
    ).get("roomId");

    if (roomIdFromUrl) {
        const roomId = Number(roomIdFromUrl);

        if (!Number.isSafeInteger(roomId) || roomId <= 0) {
            showChatError("올바르지 않은 채팅방 번호입니다.");
            return;
        }

        try {
            // 채팅방 목록 API로 해당 방의 상대방 정보를 확인
            const rooms = await apiRequest("/chat/rooms");
            const room = rooms.find(
                (item) => Number(item.roomId) === roomId
            );

            if (room) {
                await openChatRoom(room);
            } else {
                showChatError(
                    "채팅방을 찾을 수 없거나 접근 권한이 없습니다."
                );
            }
        } catch (error) {
            showChatError(`채팅방을 열지 못했습니다: ${error.message}`);
        }
    }
}


// 채팅방 목록 조회
async function loadChatRooms() {
    roomListStatus.textContent = "채팅방을 불러오는 중...";
    roomListElement.replaceChildren();

    try {
        const rooms = await apiRequest("/chat/rooms");

        renderRoomList(Array.isArray(rooms) ? rooms : []);
        roomListStatus.textContent =
            rooms.length === 0 ? "아직 채팅방이 없습니다." : "";
    } catch (error) {
        roomListStatus.textContent = error.message;
    }
}

// 채팅방 목록 화면 구성
function renderRoomList(rooms) {
    roomListElement.replaceChildren();

    rooms.forEach((room) => {
        const item = document.createElement("li");
        const button = document.createElement("button");

        button.type = "button";
        button.className = "room-item";

        if (currentRoom && currentRoom.roomId === room.roomId) {
            button.classList.add("active");
        }

        const name = document.createElement("span");
        name.className = "room-item-name";
        name.textContent = room.partnerNickname || "이름 없는 회원";

        const meta = document.createElement("span");
        meta.className = "room-item-meta";
        meta.textContent = `회원 ID: ${room.partnerMemberId}`;

        button.append(name, meta);

        button.addEventListener("click", () => {
            openChatRoom(room);
        });

        item.appendChild(button);
        roomListElement.appendChild(item);
    });
}

// 새 채팅방 생성 또는 기존 채팅방 조회
createRoomForm.addEventListener("submit", async (event) => {
    event.preventDefault();

    const targetMemberId = Number(targetMemberIdInput.value);

    if (!Number.isInteger(targetMemberId) || targetMemberId <= 0) {
        showChatError("올바른 상대방 회원 ID를 입력하세요.");
        return;
    }

    if (targetMemberId === Number(currentUser.memberId)) {
        showChatError("자기 자신과는 채팅할 수 없습니다.");
        return;
    }

    try {
        clearChatError();

        const room = await apiRequest("/chat/rooms", {
            method: "POST",
            body: JSON.stringify({
                targetMemberId: targetMemberId
            })
        });

        // API 응답의 방 정보를 화면에서 사용할 형식으로 변환
        const roomForDisplay = {
            roomId: room.roomId,
            partnerMemberId: room.partnerMemberId,
            partnerNickname: room.partnerNickname,
            createdAt: room.createdAt,
            lastMessageAt: room.lastMessageAt
        };

        targetMemberIdInput.value = "";

        await loadChatRooms();
        await openChatRoom(roomForDisplay);
    } catch (error) {
        showChatError(`채팅방 생성 실패: ${error.message}`);
    }
});

// 채팅방 목록 새로고침
refreshRoomsButton.addEventListener("click", loadChatRooms);

// 채팅방 열기
async function openChatRoom(room) {
    clearChatError();

    currentRoom = room;
    displayedMessageIds.clear();

    partnerNameElement.textContent =
        room.partnerNickname || "상대방";

    roomIdLabel.textContent = `방 번호: ${room.roomId}`;

    messageInput.disabled = true;
    sendButton.disabled = true;

    messageListElement.replaceChildren();
    renderRoomListActiveState();

    await disconnectWebSocket();

    // 다른 방을 여는 동안 사용자가 다른 방을 선택했을 수 있음
    if (!currentRoom || currentRoom.roomId !== room.roomId) {
        return;
    }

    connectWebSocket(room.roomId);
}

// 선택된 채팅방 표시 갱신
function renderRoomListActiveState() {
    const buttons = roomListElement.querySelectorAll(".room-item");

    buttons.forEach((button) => {
        button.classList.remove("active");

        const roomName = button.querySelector(".room-item-name");

        if (
            currentRoom &&
            roomName &&
            roomName.textContent === currentRoom.partnerNickname
        ) {
            button.classList.add("active");
        }
    });
}

// WebSocket 연결
function connectWebSocket(roomId) {
    const token = getToken();

    if (!token) {
        showChatError("로그인이 필요합니다.");
        window.location.replace("/login.html");
        return;
    }

    if (!window.StompJs) {
        showChatError("STOMP 라이브러리를 사용할 수 없습니다.");
        return;
    }

    setConnectionStatus("연결 중...", "");

    const protocol = window.location.protocol === "https:" ? "wss:" : "ws:";
    const brokerURL = `${protocol}//${window.location.host}/ws`;

    stompClient = new StompJs.Client({
        brokerURL: brokerURL,

        // 기존 api.js와 동일한 JWT 저장 키를 사용
        connectHeaders: {
            Authorization: `Bearer ${token}`
        },

        reconnectDelay: 3000,
        heartbeatIncoming: 10000,
        heartbeatOutgoing: 10000,

        onConnect: () => {
            isConnected = true;
            setConnectionStatus("실시간 연결됨", "connected");

            // 채팅 연결이 완료되면 메시지와 사진 전송을 활성화한다.
            messageInput.disabled = false;
            sendButton.disabled = false;
            imageButton.disabled = false;

            // 현재 방의 메시지만 구독
            stompClient.subscribe(
                `/topic/chat/rooms/${roomId}`,
                (frame) => {
                    try {
                        const message = JSON.parse(frame.body);

                        if (
                            currentRoom &&
                            currentRoom.roomId === roomId
                        ) {
                            renderMessage(message);
                        }
                    } catch (error) {
                        console.error("메시지 응답을 읽지 못했습니다.", error);
                    }
                },
                {
                    id: `chat-room-${roomId}`
                }
            );

            // 연결 후 기존 메시지를 조회
            loadMessages(roomId);
        },

        onStompError: (frame) => {
            console.error("STOMP 오류:", frame.headers, frame.body);

            setConnectionStatus("채팅 연결 오류", "error");
            showChatError(
                "채팅 서버에서 오류가 발생했습니다. 서버 로그를 확인하세요."
            );
        },

        onWebSocketError: (error) => {
            console.error("WebSocket 오류:", error);
            setConnectionStatus("연결 실패", "error");
        },

        onWebSocketClose: () => {
            isConnected = false;

            if (currentRoom && currentRoom.roomId === roomId) {
                setConnectionStatus("연결이 끊겼습니다. 재연결 중...", "");
            }
        }
    });

    stompClient.activate();
}

// WebSocket 연결 종료
async function disconnectWebSocket() {
    const client = stompClient;

    stompClient = null;
    isConnected = false;

    if (client) {
        try {
            await client.deactivate();
        } catch (error) {
            console.error("WebSocket 종료 중 오류:", error);
        }
    }

    // 연결이 종료되면 메시지와 사진 전송을 비활성화한다.
    messageInput.disabled = true;
    sendButton.disabled = true;
    imageButton.disabled = true;
    setConnectionStatus("연결되지 않음", "");
}

// 이전 메시지 조회
async function loadMessages(roomId) {
    try {
        const history = await apiRequest(
            `/chat/rooms/${roomId}/messages?page=0&size=30`
        );

        // 서버는 최신 메시지부터 반환하므로 화면에는 오래된 순서로 표시
        const messages = Array.isArray(history.content)
            ? [...history.content].reverse()
            : [];

        if (!currentRoom || currentRoom.roomId !== roomId) {
            return;
        }

        messages.forEach(renderMessage);
        scrollToLatestMessage();
    } catch (error) {
        showChatError(`메시지 조회 실패: ${error.message}`);
    }
}


/**
 * 메시지를 채팅 화면에 표시한다.
 * TEXT 메시지는 글자로, IMAGE 메시지는 사진으로 표시한다.
 */
function renderMessage(message) {
    // 메시지 ID가 없으면 표시하지 않는다.
    if (!message || message.messageId == null) {
        console.warn("메시지 ID가 없는 응답입니다.", message);
        return;
    }

    // 기존 메시지 조회와 실시간 수신에서 중복 표시되는 것을 방지한다.
    const messageId = String(message.messageId);

    if (displayedMessageIds.has(messageId)) {
        return;
    }

    displayedMessageIds.add(messageId);

    // 현재 로그인한 회원이 보낸 메시지인지 확인한다.
    const isMine =
        Number(message.senderId) === Number(currentUser.memberId);

    // 메시지 한 줄을 구성한다.
    const row = document.createElement("div");
    row.className = isMine ? "message-row mine" : "message-row";

    // 보낸 사람 이름을 표시한다.
    const sender = document.createElement("div");
    sender.className = "message-sender";
    sender.textContent = isMine
        ? "나"
        : (message.senderNickname || "회원");

    // 메시지 말풍선을 만든다.
    const bubble = document.createElement("div");
    bubble.className = "message-bubble";

    if (message.messageType === "IMAGE" && message.imageUrl) {
        // 사진 메시지는 img 요소로 표시한다.
        const image = document.createElement("img");

        // 서버가 반환한 사진 경로를 사용한다.
        image.src = message.imageUrl;
        image.alt = "채팅 사진";
        image.className = "chat-image";
        image.loading = "lazy";

        // 사진 로딩 실패 시 사용자에게 안내한다.
        image.addEventListener("error", () => {
            image.alt = "사진을 불러오지 못했습니다.";
            image.classList.add("image-load-error");
        });

        // 사진을 클릭하면 원본 크기로 새 탭에서 연다.
        image.addEventListener("click", () => {
            window.open(image.src, "_blank", "noopener,noreferrer");
        });

        bubble.appendChild(image);

        // 사진에 설명이 있다면 함께 표시한다.
        if (message.content && message.content.trim()) {
            const caption = document.createElement("p");
            caption.className = "image-caption";
            caption.textContent = message.content;
            bubble.appendChild(caption);
        }
    } else {
        // 일반 텍스트 메시지는 기존처럼 글자로 표시한다.
        bubble.textContent = message.content || "";
    }

    // 메시지 시간을 표시한다.
    const time = document.createElement("div");
    time.className = "message-time";
    time.textContent = formatMessageTime(message.createdAt);

    // 화면에 메시지를 추가한다.
    row.append(sender, bubble, time);
    messageListElement.appendChild(row);

    // 가장 최근 메시지로 스크롤한다.
    scrollToLatestMessage();
}


// 메시지 시간 표시
function formatMessageTime(value) {
    if (!value) {
        return "";
    }

    const date = new Date(value);

    if (Number.isNaN(date.getTime())) {
        return String(value);
    }

    return date.toLocaleString("ko-KR", {
        month: "2-digit",
        day: "2-digit",
        hour: "2-digit",
        minute: "2-digit"
    });
}

// 최신 메시지 위치로 스크롤
function scrollToLatestMessage() {
    messageListElement.scrollTop = messageListElement.scrollHeight;
}

// 메시지 전송
messageForm.addEventListener("submit", (event) => {
    event.preventDefault();

    if (!currentRoom) {
        showChatError("먼저 채팅방을 선택하세요.");
        return;
    }

    if (!stompClient || !isConnected || !stompClient.connected) {
        showChatError("채팅 서버에 연결되어 있지 않습니다.");
        return;
    }

    const content = messageInput.value.trim();

    if (!content) {
        return;
    }

    if (content.length > 2000) {
        showChatError("메시지는 2000자 이하로 입력하세요.");
        return;
    }

    try {
        stompClient.publish({
            destination:
                `/app/chat/rooms/${currentRoom.roomId}/messages`,
            body: JSON.stringify({
                content: content
            })
        });

        // 메시지는 서버의 실시간 응답을 받은 후 화면에 표시
        messageInput.value = "";
        clearChatError();
        messageInput.focus();
    } catch (error) {
        showChatError(`메시지 전송 실패: ${error.message}`);
    }
});

// Enter로 전송, Shift+Enter로 줄바꿈
messageInput.addEventListener("keydown", (event) => {
    if (event.key === "Enter" && !event.shiftKey) {
        event.preventDefault();

        if (!sendButton.disabled) {
            messageForm.requestSubmit();
        }
    }
});

// 연결 상태 표시
function setConnectionStatus(message, className) {
    connectionStatus.textContent = message;
    connectionStatus.className = "connection-status";

    if (className) {
        connectionStatus.classList.add(className);
    }
}

// 오류 표시
function showChatError(message) {
    chatStatusElement.textContent = message;
}

// 오류 표시 초기화
function clearChatError() {
    chatStatusElement.textContent = "";
}


/**
 * 사진 버튼을 누르면 파일 선택창을 연다.
 */
imageButton.addEventListener("click", () => {
    if (!currentRoom) {
        showChatError("먼저 채팅방을 선택하세요.");
        return;
    }

    imageInput.click();
});

/**
 * 사진을 선택하면 서버에 업로드하고 채팅 메시지로 저장한다.
 */
imageInput.addEventListener("change", async () => {
    const file = imageInput.files[0];

    // 파일을 선택하지 않았다면 종료한다.
    if (!file) {
        return;
    }

    // 현재 채팅방 ID를 보관해 업로드 중 방이 바뀌는 상황에 대비한다.
    const roomId = currentRoom?.roomId;

    if (!roomId) {
        showChatError("먼저 채팅방을 선택하세요.");
        imageInput.value = "";
        return;
    }

    // 허용된 사진 형식을 확인한다.
    const allowedTypes = ["image/jpeg", "image/png", "image/webp"];

    if (!allowedTypes.includes(file.type)) {
        showChatError("JPG, PNG, WEBP 사진만 업로드할 수 있습니다.");
        imageInput.value = "";
        return;
    }

    // 서버 제한과 동일하게 최대 5MB로 제한한다.
    if (file.size > 5 * 1024 * 1024) {
        showChatError("사진은 최대 5MB까지 업로드할 수 있습니다.");
        imageInput.value = "";
        return;
    }

    // 중복 업로드를 방지한다.
    imageButton.disabled = true;
    showChatError("사진을 업로드하는 중...");

    try {
        // multipart/form-data 형식으로 파일을 전송한다.
        const formData = new FormData();
        formData.append("file", file);

        // 사진 업로드 요청에 JWT를 포함한다.
        const token = getToken();

        const response = await fetch(
            `/api/chat/rooms/${roomId}/images`,
            {
                method: "POST",
                headers: token
                    ? { Authorization: `Bearer ${token}` }
                    : {},
                body: formData
            }
        );

        // 서버 오류 메시지를 읽는다.
        const contentType = response.headers.get("content-type") || "";
        let result;

        if (contentType.includes("application/json")) {
            result = await response.json();
        } else {
            result = await response.text();
        }

        if (!response.ok) {
            const errorMessage =
                typeof result === "string"
                    ? result
                    : result?.message ||
                    result?.detail ||
                    "사진 업로드에 실패했습니다.";

            throw new Error(errorMessage);
        }

        // 업로드 중 다른 채팅방으로 이동하지 않았다면 성공 메시지를 안내한다.
        if (currentRoom && currentRoom.roomId === roomId) {
            clearChatError();
        }

        // 서버가 WebSocket으로 메시지를 전달하므로 여기서 직접 렌더링하지 않는다.
        console.log("사진 메시지 업로드 완료:", result);
    } catch (error) {
        showChatError(`사진 업로드 실패: ${error.message}`);
    } finally {
        // 같은 사진을 다시 선택할 수 있도록 입력값을 초기화한다.
        imageInput.value = "";

        // 현재 채팅방이 연결되어 있다면 사진 버튼을 다시 활성화한다.
        imageButton.disabled = !(
            currentRoom &&
            isConnected &&
            stompClient &&
            stompClient.connected
        );
    }
});
