
const params = new URLSearchParams(window.location.search);
const postId = params.get("id");

const detailMessage = document.querySelector("#detailMessage");
const postDetail = document.querySelector("#postDetail");
const postTitle = document.querySelector("#postTitle");
const postDate = document.querySelector("#postDate");
const postContent = document.querySelector("#postContent");

const editButton = document.querySelector("#editButton");
const deleteButton = document.querySelector("#deleteButton");

// 게시글 상세 조회
async function loadPostDetail() {
    if (!postId || !/^\d+$/.test(postId)) {
        detailMessage.textContent = "올바르지 않은 게시글 번호입니다.";
        return;
    }

    try {
        const post = await apiRequest(
            `/posts/${encodeURIComponent(postId)}`,
            { method: "GET" }
        );

        postTitle.textContent = post.title ?? "";
        postContent.textContent = post.content ?? "";

        postDate.textContent = post.createdAt
            ? `작성일: ${new Date(post.createdAt).toLocaleString("ko-KR")}`
            : "";

        postDetail.hidden = false;
        detailMessage.textContent = "";

        // 로그인한 사용자에게 수정·삭제 UI를 표시한다.
        // 실제 작성자 권한 검사는 반드시 백엔드에서 수행해야 한다.
        if (isLoggedIn()) {
            editButton.hidden = false;
            deleteButton.hidden = false;
            editButton.href =
                `/write.html?id=${encodeURIComponent(postId)}`;
        }
    } catch (error) {
        console.error("게시글 상세 조회 실패:", error);

        detailMessage.textContent =
            `게시글을 불러오지 못했습니다: ${error.message}`;

        postDetail.hidden = true;
    }
}

// 게시글 삭제
deleteButton.addEventListener("click", async () => {
    if (!postId || !/^\d+$/.test(postId)) {
        alert("올바르지 않은 게시글 번호입니다.");
        return;
    }

    if (!confirm("정말 이 게시글을 삭제하시겠습니까?")) {
        return;
    }

    deleteButton.disabled = true;

    try {
        await apiRequest(`/posts/${encodeURIComponent(postId)}`, {
            method: "DELETE"
        });

        alert("게시글이 삭제되었습니다.");
        window.location.href = "/";
    } catch (error) {
        alert(`삭제 실패: ${error.message}`);
    } finally {
        deleteButton.disabled = false;
    }
});

loadPostDetail();
