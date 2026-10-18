
const postList = document.querySelector("#postList");
const boardMessage = document.querySelector("#boardMessage");
const writeButton = document.querySelector("#writeButton");
const navMenu = document.querySelector("#navMenu");

const prevButton = document.querySelector("#prevButton");
const nextButton = document.querySelector("#nextButton");
const pageInfo = document.querySelector("#pageInfo");

// 페이지네이션 상태
let currentPage = 0;
const pageSize = 10;
let totalPages = 0;

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
        writeButton.hidden = false;
    } else {
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

// 게시글 목록 조회
async function loadPosts() {
    if (!isLoggedIn()) {
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

    boardMessage.textContent = "게시글을 불러오는 중입니다.";

    try {
        // 현재 페이지에 해당하는 게시글만 요청
        const data = await apiRequest(
            `/posts?page=${currentPage}&size=${pageSize}`,
            { method: "GET" }
        );

        // 배열 응답 또는 Spring Page 응답 처리
        const posts = Array.isArray(data)
            ? data
            : data?.content;

        if (!Array.isArray(posts)) {
            throw new Error(
                "게시글 응답 형식이 예상과 다릅니다."
            );
        }

        // Spring Page 응답에서 전체 페이지 수 가져오기
        totalPages = Array.isArray(data)
            ? (posts.length > 0 ? 1 : 0)
            : (data.totalPages ?? 0);

        // 목록이 비어 있어도 페이지 정보는 갱신
        postList.replaceChildren();

        if (posts.length === 0) {
            boardMessage.textContent =
                "아직 작성된 게시글이 없습니다.";

            updatePagination();
            return;
        }

        boardMessage.textContent = "";

        // 게시글 카드 생성
        posts.forEach((post) => {
            const card = document.createElement("article");
            card.className = "post-card";

            const title = document.createElement("h3");
            const titleLink = document.createElement("a");

            titleLink.href =
                `/post-detail.html?id=${encodeURIComponent(post.id)}`;
            titleLink.textContent = post.title;

            title.appendChild(titleLink);

            const content = document.createElement("p");
            content.textContent = post.content || "";

            const author = document.createElement("small");
            author.textContent = post.nickname
                ? `작성자: ${post.nickname}`
                : "";

            const date = document.createElement("small");
            date.textContent = post.createdAt
                ? `작성일: ${new Date(post.createdAt).toLocaleString("ko-KR")}`
                : "";

            card.append(title, content, author, date);
            postList.appendChild(card);
        });

        updatePagination();

    } catch (error) {
        boardMessage.textContent =
            `게시글을 불러오지 못했습니다: ${error.message}`;
    }
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
