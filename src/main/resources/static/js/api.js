
const API_BASE_URL = "/api";

// 로그인 토큰 저장 키
const TOKEN_KEY = "devboard_access_token";

// 로그인 사용자 정보 저장 키
const USER_KEY = "devboard_user";

// JWT 토큰 저장
function saveAuth(data) {
    localStorage.setItem(TOKEN_KEY, data.accessToken);

    localStorage.setItem(
        USER_KEY,
        JSON.stringify({
            memberId: data.memberId,
            email: data.email,
            nickname: data.nickname
        })
    );
}

// JWT 토큰 가져오기
function getToken() {
    return localStorage.getItem(TOKEN_KEY);
}

// 로그인 사용자 정보 가져오기
function getCurrentUser() {
    const user = localStorage.getItem(USER_KEY);
    return user ? JSON.parse(user) : null;
}

// 로그인 여부 확인
function isLoggedIn() {
    return Boolean(getToken());
}

// 로그아웃
function logout() {
    localStorage.removeItem(TOKEN_KEY);
    localStorage.removeItem(USER_KEY);

    window.location.href = "/login.html";
}

// 공통 API 요청
async function apiRequest(path, options = {}) {
    const headers = {
        ...(options.headers || {})
    };

    // JSON 본문을 전송할 때 Content-Type 설정
    if (options.body !== undefined) {
        headers["Content-Type"] = "application/json";
    }

    // 로그인 상태라면 JWT 전송
    const token = getToken();

    if (token) {
        headers["Authorization"] = `Bearer ${token}`;
    }

    const response = await fetch(`${API_BASE_URL}${path}`, {
        ...options,
        headers
    });

    // 응답 본문이 없는 경우도 처리
    if (response.status === 204) {
        return null;
    }

    const contentType = response.headers.get("content-type") || "";

    let result = null;

    if (contentType.includes("application/json")) {
        result = await response.json();
    } else {
        result = await response.text();
    }

    if (!response.ok) {
        const message =
            typeof result === "string"
                ? result
                : result?.message || result?.detail || "요청 처리에 실패했습니다.";

        // 만료되거나 유효하지 않은 JWT
        if (response.status === 401 && getToken()) {
            localStorage.removeItem(TOKEN_KEY);
            localStorage.removeItem(USER_KEY);
        }

        throw new Error(message);
    }

    return result;
}
