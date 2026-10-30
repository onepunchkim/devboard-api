const API_BASE_URL = "/api";

// 로그인 토큰 저장 키
const TOKEN_KEY = "devboard_access_token";

// 로그인 사용자 정보 저장 키
const USER_KEY = "devboard_user";

// ========================================
// 1. 인증 정보 관리
// ========================================

// JWT 토큰과 로그인 사용자 정보 저장
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

// 저장된 JWT 토큰 가져오기
function getToken() {
    return localStorage.getItem(TOKEN_KEY);
}

// 로그인한 회원 정보 가져오기
function getCurrentUser() {
    const userJson = localStorage.getItem(USER_KEY);

    if (!userJson) {
        return null;
    }

    try {
        return JSON.parse(userJson);
    } catch (error) {
        console.error("저장된 회원 정보를 읽지 못했습니다.", error);
        return null;
    }
}

// 로그인 여부 확인
function isLoggedIn() {
    return Boolean(getToken());
}

// 로그아웃 처리
function logout() {
    localStorage.removeItem(TOKEN_KEY);
    localStorage.removeItem(USER_KEY);

    window.location.href = "/login.html";
}

// ========================================
// 2. 공통 API 요청
// ========================================

async function apiRequest(path, options = {}) {
    // 호출할 때 전달된 헤더 복사
    const headers = {
        ...(options.headers || {})
    };

    // 본문이 FormData인지 확인
    const isFormData =
        typeof FormData !== "undefined" &&
        options.body instanceof FormData;

    // 본문이 있는 요청의 Content-Type 설정
    if (options.body !== undefined && options.body !== null) {
        if (isFormData) {
            // 파일 업로드 시 Content-Type을 직접 지정하지 않는다.
            // 브라우저가 multipart/form-data 경계를 자동으로 설정한다.
            delete headers["Content-Type"];
            delete headers["content-type"];
        } else if (typeof options.body === "string") {
            // JSON 문자열을 전송하는 기존 요청 처리
            headers["Content-Type"] = "application/json";
        }
    }

    // 로그인 상태라면 JWT 토큰 전달
    const token = getToken();

    if (token) {
        headers["Authorization"] = `Bearer ${token}`;
    }

    // 서버에 API 요청 전송
    const response = await fetch(`${API_BASE_URL}${path}`, {
        ...options,
        headers
    });

    // 204 No Content 응답 처리
    if (response.status === 204) {
        return null;
    }

    // 응답 형식 확인
    const contentType = response.headers.get("content-type") || "";

    let result = null;

    // JSON 응답과 일반 문자열 응답을 구분
    if (contentType.includes("application/json")) {
        result = await response.json();
    } else {
        result = await response.text();
    }

    // 오류 응답 처리
    if (!response.ok) {
        const message =
            typeof result === "string"
                ? result
                : result?.message ||
                result?.detail ||
                "요청 처리에 실패했습니다.";

        // 401 응답을 받으면 저장된 인증 정보 제거
        if (response.status === 401 && getToken()) {
            localStorage.removeItem(TOKEN_KEY);
            localStorage.removeItem(USER_KEY);
        }

        throw new Error(message);
    }

    // 정상 응답 반환
    return result;
}