
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

// 좋아요 기능에 사용할 HTML 요소
const likeButton = document.querySelector("#likeButton");
const likeIcon = document.querySelector("#likeIcon");
const likeText = document.querySelector("#likeText");
const likeCount = document.querySelector("#likeCount");
const likeMessage = document.querySelector("#likeMessage");
// 서버에서 좋아요 상태를 확인하기 전까지 버튼을 비활성화한다.
likeButton.disabled = true;

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

        // 게시글 상세 화면 표시 후 좋아요 정보 조회
        await loadLikeInfo();

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


/**
 * 서버에서 좋아요 개수와 현재 회원의 좋아요 여부를 조회한다.
 * 조회 실패 시 상태를 알 수 없으므로 버튼을 비활성화한다.
 */
async function loadLikeInfo() {
    // 게시글 ID가 올바르지 않으면 요청하지 않는다.
    if (!postId || !/^\d+$/.test(postId)) {
        likeButton.disabled = true;
        return false;
    }

    try {
        // 좋아요 개수와 현재 회원의 좋아요 여부를 조회한다.
        const data = await apiRequest(
            `/posts/${encodeURIComponent(postId)}/like`,
            { method: "GET" }
        );

        // 서버 응답을 화면에 반영한다.
        likeCount.textContent = data.likeCount ?? 0;

        // 좋아요 상태에 따라 버튼 모양과 문구를 변경한다.
        const liked = data.liked === true;

        likeButton.classList.toggle("liked", liked);
        likeIcon.textContent = liked ? "♥" : "♡";
        likeText.textContent = liked ? "좋아요 취소" : "좋아요";

        // 조회 성공 시 버튼을 활성화하고 오류 메시지를 지운다.
        likeButton.disabled = false;
        likeMessage.textContent = "";

        return true;
    } catch (error) {
        // 조회 실패 원인을 개발자 콘솔에 기록한다.
        console.error("좋아요 정보 조회 실패:", error);

        // 사용자에게 오류를 알리고 버튼을 비활성화한다.
        likeMessage.textContent =
            `좋아요 정보를 불러오지 못했습니다: ${error.message}`;

        likeButton.disabled = true;

        return false;
    }
}



/**
 * 좋아요 버튼 클릭 시 등록 또는 취소 요청을 보낸다.
 */
likeButton.addEventListener("click", async () => {
    // 잘못된 게시글 ID에 대한 요청을 방지한다.
    if (!postId || !/^\d+$/.test(postId)) {
        likeMessage.textContent = "올바르지 않은 게시글 번호입니다.";
        return;
    }

    // 로그인하지 않았다면 로그인 페이지로 이동한다.
    if (!isLoggedIn()) {
        alert("좋아요 기능을 사용하려면 로그인이 필요합니다.");
        window.location.href = "/login.html";
        return;
    }

    // 중복 클릭을 방지한다.
    if (likeButton.disabled) {
        return;
    }

    // 요청 처리 중에는 버튼을 비활성화한다.
    likeButton.disabled = true;
    likeMessage.textContent = "처리 중입니다.";

    try {
        // 현재 화면의 좋아요 상태에 따라 등록 또는 취소를 결정한다.
        const isLiked = likeButton.classList.contains("liked");
        const method = isLiked ? "DELETE" : "POST";

        // 서버에 좋아요 등록 또는 취소를 요청한다.
        await apiRequest(
            `/posts/${encodeURIComponent(postId)}/like`,
            { method }
        );

        // 요청 결과를 다시 조회해 최신 상태를 화면에 반영한다.
        const refreshed = await loadLikeInfo();

        // 상태 조회에 실패했다면 재시도할 수 있도록 안내한다.
        if (!refreshed) {
            likeMessage.textContent =
                "좋아요 요청은 처리되었지만 최신 상태를 불러오지 못했습니다. 새로고침해 주세요.";
            return;
        }

        // 등록 또는 취소 결과를 안내한다.
        likeMessage.textContent = isLiked
            ? "좋아요를 취소했습니다."
            : "좋아요를 등록했습니다.";
    } catch (error) {
        // 서버 요청 실패 원인을 기록하고 사용자에게 안내한다.
        console.error("좋아요 처리 실패:", error);
        likeMessage.textContent = `좋아요 처리 실패: ${error.message}`;

        // 요청 실패 후 서버의 실제 상태를 다시 확인한다.
        await loadLikeInfo();
    }
});



/**
 * 게시글에 등록된 댓글을 조회하고 화면에 표시한다.
 */
async function loadComments() {
    // 기존 목록을 비우고 로딩 메시지를 표시한다.
    commentList.replaceChildren();

    const loadingMessage = document.createElement("p");
    loadingMessage.textContent = "댓글을 불러오는 중입니다.";
    commentList.appendChild(loadingMessage);

    try {
        // 서버에서 댓글 목록을 조회한다.
        const comments = await apiRequest(
            `/posts/${encodeURIComponent(postId)}/comments`,
            { method: "GET" }
        );

        // 로딩 메시지를 제거한다.
        commentList.replaceChildren();

        // 댓글 개수를 표시한다.
        commentCount.textContent = Array.isArray(comments)
            ? comments.length
            : 0;

        // 댓글이 없다면 안내 문구를 표시하고 종료한다.
        if (!Array.isArray(comments) || comments.length === 0) {
            const emptyMessage = document.createElement("p");
            emptyMessage.textContent = "아직 댓글이 없습니다.";
            commentList.appendChild(emptyMessage);
            return;
        }

        // 현재 로그인한 회원 정보를 가져온다.
        const currentUser = getCurrentUser();

        // 댓글마다 화면 요소를 생성한다.
        comments.forEach(comment => {
            const commentItem = document.createElement("article");
            commentItem.className = "comment-item";

            // 작성자와 작성일을 담을 헤더를 만든다.
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

            // 댓글 내용은 textContent로 표시해 HTML 삽입을 방지한다.
            const content = document.createElement("p");
            content.className = "comment-content";
            content.textContent = comment.content ?? "";

            commentItem.append(header, content);

            // 댓글 작성자 본인인지 확인한다.
            const isCommentAuthor =
                currentUser &&
                currentUser.memberId != null &&
                comment.memberId != null &&
                String(currentUser.memberId) === String(comment.memberId);

            // 댓글 작성자에게만 수정 및 삭제 버튼을 제공한다.
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

            // 완성된 댓글 요소를 목록에 추가한다.
            commentList.appendChild(commentItem);
        });
    } catch (error) {
        // 조회 실패 시 오류를 기록하고 안내 문구를 표시한다.
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