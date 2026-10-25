// 게시글 목록과 화면 요소
const postList = document.querySelector("#postList");
const boardMessage = document.querySelector("#boardMessage");
const writeButton = document.querySelector("#writeButton");
const navMenu = document.querySelector("#navMenu");

// 페이지네이션 요소
const prevButton = document.querySelector("#prevButton");
const nextButton = document.querySelector("#nextButton");
const pageInfo = document.querySelector("#pageInfo");

// 검색창 요소
const headerSearch = document.querySelector("#headerSearch");
const postSearch = document.querySelector("#postSearch");

// 정렬 및 게시글 개수 표시 요소
const postSort = document.querySelector("#postSort");
const postCount = document.querySelector("#postCount");
const searchResultCount = document.querySelector("#searchResultCount");

// 현재 정렬 조건: 기본값은 최신순
let currentSort = "latest";

// 페이지네이션 상태
let currentPage = 0;
const pageSize = 10;
let totalPages = 0;

// 현재 검색어
let currentKeyword = "";

// 검색 요청이 겹칠 때 오래된 응답이 화면을 덮지 않도록 관리
let latestRequestId = 0;

// 로그인 상태에 맞게 메뉴 변경
function renderNavigation() {
    const user = getCurrentUser();

    if (isLoggedIn() && user) {
        navMenu.replaceChildren();

        const welcome = document.createElement("span");
        welcome.textContent = `${user.nickname || user.email}님`;

        const logoutButton = document.createElement("button");
        logoutButton.className = "button";
        logoutButton.textContent = "로그아웃";
        logoutButton.addEventListener("click", logout);

        navMenu.append(welcome, logoutButton);

        // 로그인한 회원에게만 글쓰기 버튼 표시
        writeButton.hidden = false;
    } else {
        // 비로그인 상태에서는 글쓰기 버튼 숨김
        writeButton.hidden = true;
    }
}

// 페이지 번호와 버튼 상태 갱신
function updatePagination() {
    if (pageInfo) {
        pageInfo.textContent =
            `${totalPages === 0 ? 0 : currentPage + 1} / ${totalPages}`;
}

if (prevButton) {
    prevButton.disabled = currentPage <= 0;
}

if (nextButton) {
    nextButton.disabled =
        totalPages === 0 || currentPage >= totalPages - 1;
}
}

// 검색어를 두 검색창에 동기화
function syncSearchInputs(keyword) {
    if (headerSearch) {
        headerSearch.value = keyword;
    }

    if (postSearch) {
        postSearch.value = keyword;
    }
}

// 게시글 목록 조회 및 검색
async function loadPosts() {
    // 비로그인 상태에서는 로그인 안내 표시
    if (!isLoggedIn()) {
        // 비로그인 상태에서는 게시글 개수와 페이지 정보 초기화
        if (postCount) {
            postCount.textContent = "로그인 후 게시글을 확인할 수 있습니다.";
        }

        if (searchResultCount) {
            searchResultCount.textContent = "";
        }
        boardMessage.textContent =
            "게시글을 확인하려면 먼저 로그인해 주세요.";

        const loginLink = document.createElement("a");
        loginLink.href = "/login.html";
        loginLink.textContent = "로그인하러 가기";

        postList.replaceChildren(loginLink);

        totalPages = 0;
        updatePagination();
        return;
    }

    // 현재 요청을 식별하는 번호
    const requestId = ++latestRequestId;

    boardMessage.textContent = "게시글을 불러오는 중입니다.";
    postList.replaceChildren();

    try {
        // 페이지, 페이지 크기, 정렬 조건 설정
        const params = new URLSearchParams({
            page: String(currentPage),
            size: String(pageSize),
            sort: currentSort
        });

        // 검색어가 있으면 검색 조건 추가
        if (currentKeyword) {
            params.set("keyword", currentKeyword);
        }

        // 백엔드에 게시글 목록 또는 검색 결과 요청
        const data = await apiRequest(
            `/posts?${params.toString()}`,
            { method: "GET" }
        );

        // 더 최근 검색 요청이 있다면 이전 응답은 무시
        if (requestId !== latestRequestId) {
            return;
        }

        // Spring Page 응답 처리
        const posts = Array.isArray(data)
            ? data
            : data?.content;

        if (!Array.isArray(posts)) {
            throw new Error("게시글 응답 형식이 예상과 다릅니다.");
        }

        // 전체 페이지 수 갱신
        totalPages = Array.isArray(data)
            ? (posts.length > 0 ? 1 : 0)
            : (data.totalPages ?? 0);

        // 전체 게시글 수 또는 검색 결과 수 표시
        const totalElements = Array.isArray(data)
            ? posts.length
            : (data.totalElements ?? 0);

        if (postCount) {
            postCount.textContent = `전체 게시글 ${totalElements}개`;
        }

        if (searchResultCount) {
            searchResultCount.textContent = currentKeyword
                ? `검색 결과 ${totalElements}개`
                : "";
        }

        // 현재 페이지가 범위를 벗어나면 첫 페이지로 복귀
        if (
            totalPages > 0 &&
            currentPage >= totalPages
        ) {
            currentPage = totalPages - 1;
            updatePagination();
            await loadPosts();
            return;
        }

        if (posts.length === 0) {
            boardMessage.textContent = currentKeyword
                ? `"${currentKeyword}" 검색 결과가 없습니다.`
                : "아직 작성된 게시글이 없습니다.";

            updatePagination();
            return;
        }

        // 조회 결과가 있으면 안내 메시지 제거
        boardMessage.textContent = "";

        // 게시글 카드 생성
        posts.forEach((post) => {
            const card = document.createElement("article");
            card.className = "post-card";

            // 제목 영역
            const header = document.createElement("div");
            header.className = "post-card-header";

            const title = document.createElement("h3");
            title.className = "post-card-title";

            const titleLink = document.createElement("a");
            titleLink.href =
                `/post-detail.html?id=${encodeURIComponent(post.id)}`;
            titleLink.textContent = post.title || "제목 없음";

            title.appendChild(titleLink);
            header.appendChild(title);

            // 게시글 내용 영역
            const content = document.createElement("p");
            content.className = "post-card-content";
            content.textContent =
                post.content || "작성된 내용이 없습니다.";

            // 작성자와 작성일 영역
            const meta = document.createElement("div");
            meta.className = "post-card-meta";

            const author = document.createElement("span");
            author.className = "post-card-author";
            author.textContent = post.nickname
                ? `작성자 ${post.nickname}`
                : "작성자 정보 없음";

            const date = document.createElement("span");
            date.className = "post-card-date";

            if (post.createdAt) {
                const createdDate = new Date(post.createdAt);

                // 유효한 날짜만 화면에 표시
                date.textContent = Number.isNaN(createdDate.getTime())
                    ? ""
                    : createdDate.toLocaleString("ko-KR", {
                        year: "numeric",
                        month: "2-digit",
                        day: "2-digit",
                        hour: "2-digit",
                        minute: "2-digit"
                    });
            }

            meta.append(author, date);
            card.append(header, content, meta);
            postList.appendChild(card);
        });

        updatePagination();

    } catch (error) {
        // 이전 요청에서 발생한 오류는 무시
        if (requestId !== latestRequestId) {
            return;
        }

        boardMessage.textContent =
            `게시글을 불러오지 못했습니다: ${error.message}`;

        totalPages = 0;
        updatePagination();
    }
}

// 검색 실행
function searchPosts(value) {
    // 검색어 앞뒤의 공백 제거
    currentKeyword = value.trim();

    // 새 검색은 첫 페이지부터 시작
    currentPage = 0;

    // 검색창 두 개의 값을 동기화
    syncSearchInputs(currentKeyword);

    // 검색 결과 요청
    loadPosts();
}

// 상단 검색창 이벤트
if (headerSearch) {
    headerSearch.addEventListener("keydown", (event) => {
        // Enter 키를 누르면 검색
        if (event.key === "Enter") {
            event.preventDefault();
            searchPosts(headerSearch.value);
        }
    });

    // 검색창을 비우면 전체 게시글 조회
    headerSearch.addEventListener("input", () => {
        if (headerSearch.value === "") {
            searchPosts("");
        }
    });
}

// 게시판 검색창 이벤트
if (postSearch) {
    postSearch.addEventListener("keydown", (event) => {
        // Enter 키를 누르면 검색
        if (event.key === "Enter") {
            event.preventDefault();
            searchPosts(postSearch.value);
        }
    });

    // 검색창을 비우면 전체 게시글 조회
    postSearch.addEventListener("input", () => {
        if (postSearch.value === "") {
            searchPosts("");
        }
    });
}

// 정렬 메뉴 변경 이벤트
if (postSort) {
    // 현재 정렬 상태를 선택 메뉴에 반영
    postSort.value = currentSort;

    postSort.addEventListener("change", () => {
        // 선택한 정렬 조건 저장
        currentSort = postSort.value;

        // 정렬 변경 시 첫 페이지부터 조회
        currentPage = 0;

        // 변경된 정렬 조건으로 게시글 재조회
        loadPosts();
    });
}

// 이전 페이지
if (prevButton) {
    prevButton.addEventListener("click", async () => {
        if (currentPage > 0) {
            currentPage--;
            await loadPosts();
        }
    });
}

// 다음 페이지
if (nextButton) {
    nextButton.addEventListener("click", async () => {
        if (currentPage < totalPages - 1) {
            currentPage++;
            await loadPosts();
        }
    });
}

// 초기 실행
renderNavigation();
updatePagination();
loadPosts();