
const postList = document.querySelector("#postList");
const boardMessage = document.querySelector("#boardMessage");
const writeButton = document.querySelector("#writeButton");
const navMenu = document.querySelector("#navMenu");

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

// 게시글 목록 조회
async function loadPosts() {
    if (!isLoggedIn()) {
        boardMessage.textContent =
            "게시글을 확인하려면 먼저 로그인해 주세요.";

        const loginLink = document.createElement("a");
        loginLink.href = "/login.html";
        loginLink.textContent = "로그인하러 가기";
        postList.replaceChildren(loginLink);
        return;
    }

    try {
        const posts = await apiRequest("/posts", {
            method: "GET"
        });

        // API 응답이 배열인지 확인
        if (!Array.isArray(posts)) {
            throw new Error(
                "게시글 응답 형식이 예상과 다릅니다. API 응답을 확인해 주세요."
            );
        }

        postList.replaceChildren();

        if (posts.length === 0) {
            boardMessage.textContent = "아직 작성된 게시글이 없습니다.";
            return;
        }

        boardMessage.textContent = "";

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

            const date = document.createElement("small");
            date.textContent = post.createdAt
                ? `작성일: ${new Date(post.createdAt).toLocaleString("ko-KR")}`
                : "";

            card.append(title, content, date);
            postList.appendChild(card);
        });
    } catch (error) {
        boardMessage.textContent =
            `게시글을 불러오지 못했습니다: ${error.message}`;
    }
}

renderNavigation();
loadPosts();
