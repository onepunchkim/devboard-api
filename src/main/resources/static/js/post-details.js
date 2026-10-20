
const params = new URLSearchParams(window.location.search);
const postId = params.get("id");

const detailMessage = document.querySelector("#detailMessage");
const postDetail = document.querySelector("#postDetail");
const postTitle = document.querySelector("#postTitle");
const postDate = document.querySelector("#postDate");
const postContent = document.querySelector("#postContent");
const postAuthor = document.querySelector("#postAuthor");

const editButton = document.querySelector("#editButton");
const deleteButton = document.querySelector("#deleteButton");

// 댓글 요소
const commentSection = document.querySelector("#commentSection");
const commentCount = document.querySelector("#commentCount");
const commentForm = document.querySelector("#commentForm");
const commentContent = document.querySelector("#commentContent");
const commentMessage = document.querySelector("#commentMessage");
const commentList = document.querySelector("#commentList");

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

        postAuthor.textContent = post.nickname
            ? `작성자: ${post.nickname}`
            : "작성자 정보 없음";

        const currentUser = getCurrentUser();

        const isAuthor =
            currentUser &&
            currentUser.memberId != null &&
            post.memberId != null &&
            String(currentUser.memberId) === String(post.memberId);

        editButton.hidden = !isAuthor;
        deleteButton.hidden = !isAuthor;

        if (isAuthor) {
            editButton.href =
                `/write.html?id=${encodeURIComponent(postId)}`;
        }

        postDetail.hidden = false;
        detailMessage.textContent = "";

        // 게시글 조회 성공 후 댓글 영역 표시 및 조회
        commentSection.hidden = false;
        await loadComments();

    } catch (error) {
        console.error("게시글 상세 조회 실패:", error);

        detailMessage.textContent =
            `게시글을 불러오지 못했습니다: ${error.message}`;

        postDetail.hidden = true;
        commentSection.hidden = true;
    }
}

// 댓글 목록 조회
async function loadComments() {
    commentList.replaceChildren();

    const loadingMessage = document.createElement("p");
    loadingMessage.textContent = "댓글을 불러오는 중입니다.";
    commentList.appendChild(loadingMessage);

    try {
        const comments = await apiRequest(
            `/posts/${encodeURIComponent(postId)}/comments`,
{ method: "GET" }
);

commentList.replaceChildren();

commentCount.textContent = Array.isArray(comments)
    ? comments.length
    : 0;

if (!Array.isArray(comments) || comments.length === 0) {
    const emptyMessage = document.createElement("p");
    emptyMessage.textContent = "아직 댓글이 없습니다.";
    commentList.appendChild(emptyMessage);
    return;
}

const currentUser = getCurrentUser();

comments.forEach(comment => {
    const commentItem = document.createElement("article");
    commentItem.className = "comment-item";

    const header = document.createElement("div");
    header.className = "comment-header";

    const nickname = document.createElement("strong");
    nickname.className = "comment-author";
    nickname.textContent = comment.nickname ?? "알 수 없는 회원";

    const date = document.createElement("span");
    date.className = "comment-date";
    date.textContent = comment.createdAt
        ? new Date(comment.createdAt).toLocaleString("ko-KR")
        : "";

    header.append(nickname, date);

    const content = document.createElement("p");
    content.className = "comment-content";
    content.textContent = comment.content ?? "";

    commentItem.append(header, content);

    // 댓글 작성자에게만 수정·삭제 버튼 표시
    const isCommentAuthor =
        currentUser &&
        currentUser.memberId != null &&
        comment.memberId != null &&
        String(currentUser.memberId) === String(comment.memberId);

    if (isCommentAuthor) {
        const actions = document.createElement("div");
        actions.className = "comment-actions";

        const editCommentButton = document.createElement("button");
        editCommentButton.type = "button";
        editCommentButton.className = "button";
        editCommentButton.textContent = "수정";

        editCommentButton.addEventListener("click", () => {
            updateComment(comment);
        });

        const deleteCommentButton = document.createElement("button");
        deleteCommentButton.type = "button";
        deleteCommentButton.className = "button danger";
        deleteCommentButton.textContent = "삭제";

        deleteCommentButton.addEventListener("click", () => {
            deleteComment(comment.id, deleteCommentButton);
        });

        actions.append(editCommentButton, deleteCommentButton);
        commentItem.appendChild(actions);
    }

    commentList.appendChild(commentItem);
});

} catch (error) {
    console.error("댓글 목록 조회 실패:", error);

    commentList.replaceChildren();

    const errorMessage = document.createElement("p");
    errorMessage.textContent =
        `댓글을 불러오지 못했습니다: ${error.message}`;

    commentList.appendChild(errorMessage);
}
}

// 댓글 작성
commentForm.addEventListener("submit", async event => {
    event.preventDefault();

    const content = commentContent.value.trim();

    if (!content) {
        commentMessage.textContent = "댓글 내용을 입력해 주세요.";
        return;
    }

    if (content.length > 1000) {
        commentMessage.textContent = "댓글은 1000자 이하로 작성해 주세요.";
        return;
    }

    const submitButton = commentForm.querySelector(
        'button[type="submit"]'
    );

    submitButton.disabled = true;
    commentMessage.textContent = "댓글을 등록하는 중입니다.";

    try {
        await apiRequest(
            `/posts/${encodeURIComponent(postId)}/comments`,
            {
                method: "POST",
                body: JSON.stringify({ content })
            }
        );

        commentContent.value = "";
        commentMessage.textContent = "댓글이 등록되었습니다.";

        await loadComments();

    } catch (error) {
        console.error("댓글 작성 실패:", error);
        commentMessage.textContent = `댓글 작성 실패: ${error.message}`;
    } finally {
        submitButton.disabled = false;
    }
});

// 댓글 수정
async function updateComment(comment) {
    const newContent = prompt("수정할 댓글 내용을 입력해 주세요.", comment.content);

    if (newContent === null) {
        return;
    }

    const content = newContent.trim();

    if (!content) {
        alert("댓글 내용을 입력해 주세요.");
        return;
    }

    if (content.length > 1000) {
        alert("댓글은 1000자 이하로 작성해 주세요.");
        return;
    }

    try {
        await apiRequest(`/comments/${encodeURIComponent(comment.id)}`, {
            method: "PUT",
            body: JSON.stringify({ content })
        });

        alert("댓글이 수정되었습니다.");
        await loadComments();

    } catch (error) {
        console.error("댓글 수정 실패:", error);
        alert(`댓글 수정 실패: ${error.message}`);
    }
}

// 댓글 삭제
async function deleteComment(commentId, button) {
    if (!confirm("이 댓글을 삭제하시겠습니까?")) {
        return;
    }

    button.disabled = true;

    try {
        await apiRequest(`/comments/${encodeURIComponent(commentId)}`, {
            method: "DELETE"
        });

        commentMessage.textContent = "댓글이 삭제되었습니다.";
        await loadComments();

    } catch (error) {
        console.error("댓글 삭제 실패:", error);
        alert(`댓글 삭제 실패: ${error.message}`);
    } finally {
        button.disabled = false;
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

// 초기 실행
loadPostDetail();