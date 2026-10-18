
const postForm = document.querySelector("#postForm");
const formTitle = document.querySelector("#formTitle");
const submitButton = document.querySelector("#submitButton");
const formMessage = document.querySelector("#formMessage");

const titleInput = document.querySelector("#title");
const contentInput = document.querySelector("#content");

const params = new URLSearchParams(window.location.search);
const postId = params.get("id");

const isEditMode = postId !== null;

// 로그인 확인
if (!isLoggedIn()) {
    alert("로그인이 필요합니다.");
    window.location.replace("/login.html");
}

// 게시글 ID 검증
if (isEditMode && !/^\d+$/.test(postId)) {
    postForm.hidden = true;
    formMessage.textContent = "올바르지 않은 게시글 번호입니다.";
}

// 수정 모드라면 기존 게시글 불러오기
async function loadPostForEdit() {
    if (!isEditMode || !/^\d+$/.test(postId)) {
        return;
    }

    formTitle.textContent = "게시글 수정";
    submitButton.textContent = "수정 완료";
    formMessage.textContent = "게시글을 불러오는 중입니다.";

    try {
        const post = await apiRequest(
            `/posts/${encodeURIComponent(postId)}`,
            { method: "GET" }
        );

        titleInput.value = post.title ?? "";
        contentInput.value = post.content ?? "";
        formMessage.textContent = "";
    } catch (error) {
        formMessage.textContent =
            `게시글을 불러오지 못했습니다: ${error.message}`;
        submitButton.disabled = true;
    }
}

// 게시글 등록 또는 수정
postForm.addEventListener("submit", async (event) => {
    event.preventDefault();

    if (!isLoggedIn()) {
        alert("로그인이 필요합니다.");
        window.location.href = "/login.html";
        return;
    }

    if (isEditMode && !/^\d+$/.test(postId)) {
        formMessage.textContent = "올바르지 않은 게시글 번호입니다.";
        return;
    }

    const request = {
        title: titleInput.value.trim(),
        content: contentInput.value.trim()
    };

    if (!request.title || !request.content) {
        formMessage.textContent = "제목과 내용을 입력해 주세요.";
        return;
    }

    submitButton.disabled = true;
    formMessage.textContent = "저장 중입니다.";

    try {
        if (isEditMode) {
            await apiRequest(
                `/posts/${encodeURIComponent(postId)}`,
                {
                    method: "PUT",
                    body: JSON.stringify(request)
                }
            );

            alert("게시글이 수정되었습니다.");
            window.location.href =
                `/post-detail.html?id=${encodeURIComponent(postId)}`;
        } else {
            const result = await apiRequest("/posts", {
                method: "POST",
                body: JSON.stringify(request)
            });

            alert("게시글이 등록되었습니다.");

            // 서버가 생성된 게시글 ID를 반환하는 경우 상세 페이지로 이동
            if (result && result.id != null) {
                window.location.href =
                    `/post-detail.html?id=${encodeURIComponent(result.id)}`;
            } else {
                // 생성 응답에 ID가 없는 경우 목록으로 이동
                window.location.href = "/";
            }
        }
    } catch (error) {
        formMessage.textContent =
            `저장에 실패했습니다: ${error.message}`;
    } finally {
        submitButton.disabled = false;
    }
});

if (isEditMode) {
    loadPostForEdit();
}
