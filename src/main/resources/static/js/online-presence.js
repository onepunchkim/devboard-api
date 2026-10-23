"use strict";

const onlineMembersElement = document.getElementById("online-members");
const onlineStatusElement = document.getElementById("online-status");

let presenceClient = null;
let presenceInterval = null;
let presenceConnected = false;

async function initializeOnlinePresence() {
    // 로그인하지 않은 사용자는 온라인 연결을 하지 않음
    if (!getToken() || !getCurrentUser()) {
        renderOnlineStatus("로그인하면 온라인 회원을 확인할 수 있습니다.");
        return;
    }

    if (!window.StompJs) {
        renderOnlineStatus("실시간 연결 라이브러리를 불러오지 못했습니다.");
        return;
    }

    connectPresenceSocket();
}

function connectPresenceSocket() {
    const token = getToken();

    if (!token) {
        return;
    }

    const protocol = location.protocol === "https:" ? "wss:" : "ws:";
    const brokerURL = `${protocol}//${location.host}/ws`;

    presenceClient = new StompJs.Client({
        brokerURL,
        connectHeaders: {
            Authorization: `Bearer ${token}`
        },
        reconnectDelay: 3000,
        heartbeatIncoming: 10000,
        heartbeatOutgoing: 10000,

        onConnect: () => {
            presenceConnected = true;
            renderOnlineStatus("온라인 회원을 확인하는 중...");

            // 연결 직후 목록을 불러오고 이후 주기적으로 갱신
            loadOnlineMembers();

            if (presenceInterval) {
                clearInterval(presenceInterval);
            }

            presenceInterval = setInterval(() => {
                if (presenceConnected) {
                    loadOnlineMembers();
                }
            }, 5000);
        },

        onWebSocketClose: () => {
            presenceConnected = false;
            renderOnlineStatus("실시간 연결이 끊겼습니다. 재연결 중...");

            if (presenceInterval) {
                clearInterval(presenceInterval);
                presenceInterval = null;
            }
        },

        onStompError: (frame) => {
            console.error("온라인 상태 연결 오류:", frame.headers, frame.body);
            renderOnlineStatus("온라인 회원 정보를 가져올 수 없습니다.");
        },

        onWebSocketError: (error) => {
            console.error("온라인 상태 WebSocket 오류:", error);
        }
    });

    presenceClient.activate();
}

async function loadOnlineMembers() {
    if (!presenceConnected) {
        return;
    }

    try {
        const members = await apiRequest("/members/online");
        renderOnlineMembers(Array.isArray(members) ? members : []);
    } catch (error) {
        console.error("온라인 회원 조회 실패:", error);
        renderOnlineStatus(error.message || "온라인 회원 조회에 실패했습니다.");
    }
}

function renderOnlineMembers(members) {
    if (!onlineMembersElement) {
        return;
    }

    onlineMembersElement.replaceChildren();

    if (members.length === 0) {
        renderOnlineStatus("현재 접속 중인 다른 회원이 없습니다.");
        return;
    }

    renderOnlineStatus(`온라인 회원 ${members.length}명`);

    members.forEach((member) => {
        const item = document.createElement("li");
        const button = document.createElement("button");
        const dot = document.createElement("span");
        const name = document.createElement("span");

        button.type = "button";
        button.className = "online-member-button";

        dot.className = "online-dot";
        dot.setAttribute("aria-label", "온라인");

        name.className = "online-member-name";
        name.textContent = member.nickname || `회원 ${member.memberId}`;

        button.append(dot, name);
        button.addEventListener("click", () => startChatWithMember(member));

        item.appendChild(button);
        onlineMembersElement.appendChild(item);
    });
}

function renderOnlineStatus(message) {
    if (onlineStatusElement) {
        onlineStatusElement.textContent = message;
    }
}

async function startChatWithMember(member) {
    if (!member || !member.memberId) {
        return;
    }

    try {
        renderOnlineStatus(`${member.nickname} 회원과 채팅방을 여는 중...`);

        const room = await apiRequest("/chat/rooms", {
            method: "POST",
            body: JSON.stringify({
                targetMemberId: member.memberId
            })
        });

        // 기존 chat.js에서 이 방을 열 수 있도록 방 ID 전달
        window.location.href =
            `/chat.html?roomId=${encodeURIComponent(room.roomId)}`;
    } catch (error) {
        renderOnlineStatus(`채팅방을 열지 못했습니다: ${error.message}`);
    }
}

// 페이지를 열었을 때 온라인 연결 시작
initializeOnlinePresence();

// 페이지를 떠날 때 연결 종료
window.addEventListener("pagehide", () => {
    presenceConnected = false;

    if (presenceInterval) {
        clearInterval(presenceInterval);
        presenceInterval = null;
    }

    if (presenceClient) {
        presenceClient.deactivate();
        presenceClient = null;
    }
});
