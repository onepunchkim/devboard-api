/* ========================================
   DevBoard 게시글 상세 JavaScript
   게시글 조회, 이미지 갤러리, 이미지 업로드,
   좋아요, 댓글 관리
======================================== */

// URL에서 게시글 ID 가져오기
const params = new URLSearchParams(window.location.search);
const postId = params.get("id");

// ========================================
// 1. 게시글 요소
// ========================================

const detailMessage = document.querySelector("#detailMessage");
const postDetail = document.querySelector("#postDetail");
const postTitle = document.querySelector("#postTitle");
const postDate = document.querySelector("#postDate");
const postContent = document.querySelector("#postContent");
const postAuthor = document.querySelector("#postAuthor");

const editButton = document.querySelector("#editButton");
const deleteButton = document.querySelector("#deleteButton");

// ========================================
// 2. 게시글 이미지 요소
// ========================================

const postImageSection = document.querySelector("#postImageSection");
const postImageMessage = document.querySelector("#postImageMessage");
const postImageList = document.querySelector("#postImageList");

// 사진 업로드 관련 요소
const imageUploadSection = document.querySelector("#imageUploadSection");
const imageFiles = document.querySelector("#imageFiles");
const uploadImagesButton = document.querySelector("#uploadImagesButton");
const imageUploadMessage = document.querySelector("#imageUploadMessage");

// 현재 회원이 게시글 작성자인지 저장
// 작성자에게는 이미지가 없어도 업로드 영역을 표시한다.
let canUploadImages = false;

// ========================================
// 3. 좋아요 요소
// ========================================

const likeButton = document.querySelector("#likeButton");
const likeIcon = document.querySelector("#likeIcon");
const likeText = document.querySelector("#likeText");
const likeCount = document.querySelector("#likeCount");
const likeMessage = document.querySelector("#likeMessage");

// 서버에서 좋아요 상태를 확인하기 전까지 버튼 비활성화
likeButton.disabled = true;

// ========================================
// 4. 댓글 요소
// ========================================

const commentSection = document.querySelector("#commentSection");
const commentCount = document.querySelector("#commentCount");
const commentForm = document.querySelector("#commentForm");
const commentContent = document.querySelector("#commentContent");
const commentMessage = document.querySelector("#commentMessage");
const commentList = document.querySelector("#commentList");

// ========================================
// 5. 게시글 상세 조회
// ========================================

async function loadPostDetail() {
    // 게시글 ID 유효성 검사
    if (!postId || !/^\d+$/.test(postId)) {
        detailMessage.textContent = "올바르지 않은 게시글 번호입니다.";
        return;
    }

    try {
        // 서버에서 게시글 정보 조회
        const post = await apiRequest(
            `/posts/${encodeURIComponent(postId)}`,
            { method: "GET" }
        );

        // 게시글 제목과 본문 표시
        postTitle.textContent = post.title ?? "";
        postContent.textContent = post.content ?? "";

        // 게시글 작성일 표시
        postDate.textContent = post.createdAt
            ? `작성일: ${new Date(post.createdAt).toLocaleString("ko-KR")}`
            : "";

        // 게시글 작성자 표시
        postAuthor.textContent = post.nickname
            ? `작성자: ${post.nickname}`
            : "작성자 정보 없음";

        // 현재 로그인한 회원 정보 가져오기
        const currentUser = getCurrentUser();

        // 현재 회원이 게시글 작성자인지 확인
        const isAuthor =
            currentUser &&
            currentUser.memberId != null &&
            post.memberId != null &&
            String(currentUser.memberId) === String(post.memberId);

        // 작성자 여부를 이미지 업로드 권한에도 적용
        canUploadImages = Boolean(isAuthor);

        // 작성자에게만 수정 및 삭제 버튼 표시
        editButton.hidden = !isAuthor;
        deleteButton.hidden = !isAuthor;

        // 작성자에게만 사진 업로드 영역 표시
        imageUploadSection.hidden = !canUploadImages;

        // 수정 페이지에 게시글 ID 전달
        if (isAuthor) {
            editButton.href =
                `/write.html?id=${encodeURIComponent(postId)}`;
        }

        // 게시글 표시
        postDetail.hidden = false;
        detailMessage.textContent = "";

        // 좋아요, 이미지, 댓글을 독립적으로 조회
        // 한 기능의 실패가 다른 기능의 실행을 막지 않도록 처리
        await Promise.allSettled([
            loadLikeInfo(),
            loadPostImages(),
            loadComments()
        ]);

        // 게시글 조회가 성공하면 댓글 영역 표시
        commentSection.hidden = false;

    } catch (error) {
        // 게시글 자체를 불러오지 못한 경우
        console.error("게시글 상세 조회 실패:", error);

        detailMessage.textContent =
            `게시글을 불러오지 못했습니다: ${error.message}`;

        postDetail.hidden = true;
        commentSection.hidden = true;
        postImageSection.hidden = true;
    }
}

// ========================================
// 6. 게시글 이미지 조회
// ========================================

/**
 * 게시글 이미지 목록을 조회하고 화면에 표시한다.
 */
async function loadPostImages() {
    // 이전 이미지 목록과 안내 메시지 초기화
    postImageList.replaceChildren();
    postImageMessage.textContent = "";

    // 이미지가 없어도 작성자는 사진을 등록할 수 있도록 영역 표시
    postImageSection.hidden = !canUploadImages;

    postImageMessage.textContent = "이미지를 불러오는 중입니다.";

    try {
        // 해당 게시글에 등록된 이미지 목록 조회
        const images = await apiRequest(
            `/posts/${encodeURIComponent(postId)}/images`,
            { method: "GET" }
        );

        // 기존 이미지 목록 초기화
        postImageList.replaceChildren();

        // 등록된 이미지가 없는 경우
        if (!Array.isArray(images) || images.length === 0) {
            // 작성자는 업로드 영역을 유지하고 일반 방문자는 숨긴다.
            postImageSection.hidden = !canUploadImages;
            postImageMessage.textContent = "";
            return;
        }

        // 이미지가 있으면 갤러리 표시
        postImageSection.hidden = false;

        // 이미지 로딩 결과 기록
        let loadedCount = 0;
        let failedCount = 0;
        let settledCount = 0;

        // 이미지 로딩 상태 메시지 갱신
        function updateImageMessage() {
            // 아직 모든 이미지의 로딩 결과가 확인되지 않은 경우
            if (settledCount < images.length) {
                postImageMessage.textContent =
                    "이미지를 불러오는 중입니다.";
                return;
            }

            // 모든 이미지가 정상적으로 표시된 경우
            if (loadedCount > 0 && failedCount === 0) {
                postImageMessage.textContent = "";
            }
            // 일부 이미지만 표시된 경우
            else if (loadedCount > 0 && failedCount > 0) {
                postImageMessage.textContent =
                    "일부 이미지를 불러오지 못했습니다.";
            }
            // 표시 가능한 이미지가 없는 경우
            else {
                postImageMessage.textContent =
                    "이미지를 표시할 수 없습니다. 파일 경로와 서버 설정을 확인해 주세요.";
            }
        }

        // 이미지마다 HTML 요소 생성
        images.forEach(image => {
            // 이미지 경로가 없으면 실패로 기록
            if (!image.imagePath) {
                failedCount++;
                settledCount++;
                updateImageMessage();
                return;
            }

            const item = document.createElement("li");
            item.className = "post-image-item";

            const img = document.createElement("img");

            // 서버에서 반환한 이미지 경로를 URL로 변환
            // 예: uploads/post-images/abc.jpg
            const imagePath = image.imagePath.replace(/^\/+/, "");
            img.src = `/${imagePath}`;

            // 접근성을 위한 대체 텍스트 설정
            img.alt = image.originalFilename || "게시글 이미지";

            // 이미지 로딩 최적화
            img.loading = "lazy";
            img.decoding = "async";

            // 이미지 로딩 성공 처리
            img.addEventListener("load", () => {
                loadedCount++;
                settledCount++;
                updateImageMessage();
            }, { once: true });

            // 이미지 로딩 실패 처리
            img.addEventListener("error", () => {
                failedCount++;
                settledCount++;

                // 로딩에 실패한 이미지 제거
                item.remove();

                updateImageMessage();
            }, { once: true });

            // 이미지 요소를 목록에 추가
            item.appendChild(img);
            postImageList.appendChild(item);
        });

        // 표시 가능한 이미지가 하나도 없는 경우
        if (postImageList.children.length === 0) {
            // 작성자의 업로드 영역은 유지한다.
            postImageSection.hidden = !canUploadImages;
            postImageMessage.textContent = "";
        }

    } catch (error) {
        // 이미지 조회 실패가 다른 기능에 영향을 주지 않도록 처리
        console.error("게시글 이미지 조회 실패:", error);

        postImageList.replaceChildren();

        // 이미지 조회가 실패해도 작성자는 사진을 등록할 수 있다.
        postImageSection.hidden = !canUploadImages;

        postImageMessage.textContent =
            "이미지 목록을 불러오지 못했습니다.";
    }
}

// ========================================
// 7. 게시글 사진 등록
// ========================================

uploadImagesButton.addEventListener("click", async () => {
    // 로그인 확인
    if (!isLoggedIn()) {
        alert("사진을 등록하려면 로그인이 필요합니다.");
        window.location.href = "/login.html";
        return;
    }

    // 작성자 여부 확인
    if (!canUploadImages) {
        imageUploadMessage.textContent =
            "게시글 작성자만 사진을 등록할 수 있습니다.";
        return;
    }

    // 게시글 ID 유효성 검사
    if (!postId || !/^\d+$/.test(postId)) {
        imageUploadMessage.textContent =
            "올바르지 않은 게시글 번호입니다.";
        return;
    }

    // 선택한 사진 가져오기
    const files = Array.from(imageFiles.files || []);

    if (files.length === 0) {
        imageUploadMessage.textContent =
            "등록할 사진을 선택해 주세요.";
        return;
    }

    // 한 번에 선택할 수 있는 사진 수 확인
    if (files.length > 5) {
        imageUploadMessage.textContent =
            "사진은 한 번에 최대 5장까지 선택할 수 있습니다.";
        return;
    }

    // 파일별 크기와 형식 확인
    const allowedTypes = [
        "image/jpeg",
        "image/png",
        "image/webp"
    ];

    const maxFileSize = 5 * 1024 * 1024;

    for (const file of files) {
        // 허용된 이미지 형식인지 확인
        if (!allowedTypes.includes(file.type)) {
            imageUploadMessage.textContent =
                "JPG, PNG, WEBP 형식의 사진만 등록할 수 있습니다.";
            return;
        }

        // 파일 하나의 크기가 5MB를 초과하는지 확인
        if (file.size > maxFileSize) {
            imageUploadMessage.textContent =
                `${file.name}: 사진 한 장의 크기는 5MB 이하여야 합니다.`;
            return;
        }
    }

    // 중복 업로드 방지
    uploadImagesButton.disabled = true;
    imageUploadMessage.textContent = "사진을 등록하는 중입니다.";

    try {
        // multipart/form-data 요청 본문 생성
        const formData = new FormData();

        // 백엔드의 @RequestParam("files") 이름과 일치해야 한다.
        files.forEach(file => {
            formData.append("files", file);
        });

        // 사진 업로드 API 호출
        // api.js에서 FormData 요청의 Content-Type을 직접 설정하지 않도록 처리해야 한다.
        await apiRequest(
            `/posts/${encodeURIComponent(postId)}/images`,
            {
                method: "POST",
                body: formData
            }
        );

        // 업로드 성공 후 파일 선택 초기화
        imageFiles.value = "";

        // 업로드한 사진 목록 다시 조회
        await loadPostImages();

        imageUploadMessage.textContent =
            "사진 등록이 완료되었습니다.";

    } catch (error) {
        // 업로드 실패 처리
        console.error("사진 등록 실패:", error);

        imageUploadMessage.textContent =
            `사진 등록 실패: ${error.message}`;

    } finally {
        // 업로드 버튼 다시 활성화
        uploadImagesButton.disabled = false;
    }
});

// ========================================
// 8. 좋아요 정보 조회
// ========================================

/**
 * 서버에서 좋아요 개수와 현재 회원의 좋아요 여부를 조회한다.
 */
async function loadLikeInfo() {
    // 게시글 ID 유효성 검사
    if (!postId || !/^\d+$/.test(postId)) {
        likeButton.disabled = true;
        return false;
    }

    try {
        // 좋아요 정보 조회
        const data = await apiRequest(
            `/posts/${encodeURIComponent(postId)}/like`,
            { method: "GET" }
        );

        // 좋아요 개수 표시
        likeCount.textContent = data.likeCount ?? 0;

        // 현재 회원의 좋아요 여부 확인
        const liked = data.liked === true;

        // 좋아요 상태에 따라 버튼 표시 변경
        likeButton.classList.toggle("liked", liked);
        likeIcon.textContent = liked ? "♥" : "♡";
        likeText.textContent = liked ? "좋아요 취소" : "좋아요";

        // 버튼 활성화 및 메시지 초기화
        likeButton.disabled = false;
        likeMessage.textContent = "";

        return true;

    } catch (error) {
        console.error("좋아요 정보 조회 실패:", error);

        likeMessage.textContent =
            `좋아요 정보를 불러오지 못했습니다: ${error.message}`;

        likeButton.disabled = true;
        return false;
    }
}

// ========================================
// 9. 좋아요 등록 및 취소
// ========================================

likeButton.addEventListener("click", async () => {
    // 게시글 ID 유효성 검사
    if (!postId || !/^\d+$/.test(postId)) {
        likeMessage.textContent =
            "올바르지 않은 게시글 번호입니다.";
        return;
    }

    // 로그인 여부 확인
    if (!isLoggedIn()) {
        alert("좋아요 기능을 사용하려면 로그인이 필요합니다.");
        window.location.href = "/login.html";
        return;
    }

    // 중복 클릭 방지
    if (likeButton.disabled) {
        return;
    }

    likeButton.disabled = true;
    likeMessage.textContent = "처리 중입니다.";

    try {
        // 현재 좋아요 상태에 따라 요청 방식 결정
        const isLiked = likeButton.classList.contains("liked");
        const method = isLiked ? "DELETE" : "POST";

        // 좋아요 등록 또는 취소 요청
        await apiRequest(
            `/posts/${encodeURIComponent(postId)}/like`,
            { method }
        );

        // 변경된 좋아요 상태 다시 조회
        const refreshed = await loadLikeInfo();

        // 최신 상태 조회에 실패한 경우
        if (!refreshed) {
            likeMessage.textContent =
                "좋아요 요청은 처리되었지만 최신 상태를 불러오지 못했습니다. 새로고침해 주세요.";
            return;
        }

        // 요청 결과 메시지 표시
        likeMessage.textContent = isLiked
            ? "좋아요를 취소했습니다."
            : "좋아요를 등록했습니다.";

    } catch (error) {
        console.error("좋아요 처리 실패:", error);

        likeMessage.textContent =
            `좋아요 처리 실패: ${error.message}`;

        // 서버의 실제 상태 다시 조회
        await loadLikeInfo();

    } finally {
        // 요청 완료 후 버튼 상태 복구
        likeButton.disabled = false;
    }
});

// ========================================
// 10. 댓글 목록 조회
// ========================================

/**
 * 게시글에 등록된 댓글을 조회하고 화면에 표시한다.
 */
async function loadComments() {
    // 기존 댓글 목록 초기화
    commentList.replaceChildren();

    const loadingMessage = document.createElement("p");
    loadingMessage.textContent = "댓글을 불러오는 중입니다.";
    commentList.appendChild(loadingMessage);

    try {
        // 해당 게시글의 댓글 목록 조회
        const comments = await apiRequest(
            `/posts/${encodeURIComponent(postId)}/comments`,
            { method: "GET" }
        );

        // 기존 목록 초기화
        commentList.replaceChildren();

        // 댓글 개수 표시
        commentCount.textContent = Array.isArray(comments)
            ? comments.length
            : 0;

        // 댓글이 없는 경우 안내 메시지 표시
        if (!Array.isArray(comments) || comments.length === 0) {
            const emptyMessage = document.createElement("p");
            emptyMessage.textContent = "아직 댓글이 없습니다.";
            commentList.appendChild(emptyMessage);
            return;
        }

        // 현재 로그인한 회원 정보
        const currentUser = getCurrentUser();

        // 댓글 목록 생성
        comments.forEach(comment => {
            const commentItem = document.createElement("article");
            commentItem.className = "comment-item";

            // 댓글 작성자와 작성일 영역
            const header = document.createElement("div");
            header.className = "comment-header";

            // 댓글 작성자
            const nickname = document.createElement("strong");
            nickname.className = "comment-author";
            nickname.textContent =
                comment.nickname ?? "알 수 없는 회원";

            // 댓글 작성일
            const date = document.createElement("span");
            date.className = "comment-date";
            date.textContent = comment.createdAt
                ? new Date(comment.createdAt).toLocaleString("ko-KR")
                : "";

            header.append(nickname, date);

            // 댓글 내용
            // textContent를 사용하여 HTML이 실행되지 않도록 처리
            const content = document.createElement("p");
            content.className = "comment-content";
            content.textContent = comment.content ?? "";

            commentItem.append(header, content);

            // 현재 회원이 댓글 작성자인지 확인
            const isCommentAuthor =
                currentUser &&
                currentUser.memberId != null &&
                comment.memberId != null &&
                String(currentUser.memberId) === String(comment.memberId);

            // 댓글 작성자에게만 수정 및 삭제 버튼 표시
            if (isCommentAuthor) {
                const actions = document.createElement("div");
                actions.className = "comment-actions";

                // 댓글 수정 버튼
                const editCommentButton =
                    document.createElement("button");

                editCommentButton.type = "button";
                editCommentButton.className = "button";
                editCommentButton.textContent = "수정";

                editCommentButton.addEventListener("click", () => {
                    updateComment(comment);
                });

                // 댓글 삭제 버튼
                const deleteCommentButton =
                    document.createElement("button");

                deleteCommentButton.type = "button";
                deleteCommentButton.className = "button danger";
                deleteCommentButton.textContent = "삭제";

                deleteCommentButton.addEventListener("click", () => {
                    deleteComment(comment.id, deleteCommentButton);
                });

                actions.append(editCommentButton, deleteCommentButton);
                commentItem.appendChild(actions);
            }

            // 완성된 댓글을 목록에 추가
            commentList.appendChild(commentItem);
        });

    } catch (error) {
        // 댓글 조회 실패 처리
        console.error("댓글 목록 조회 실패:", error);

        commentList.replaceChildren();

        const errorMessage = document.createElement("p");
        errorMessage.textContent =
            `댓글을 불러오지 못했습니다: ${error.message}`;

        commentList.appendChild(errorMessage);
    }
}

// ========================================
// 11. 댓글 작성
// ========================================

commentForm.addEventListener("submit", async event => {
    // 폼 제출로 인한 페이지 새로고침 방지
    event.preventDefault();

    // 입력한 댓글의 앞뒤 공백 제거
    const content = commentContent.value.trim();

    // 댓글 내용 검사
    if (!content) {
        commentMessage.textContent =
            "댓글 내용을 입력해 주세요.";
        return;
    }

    if (content.length > 1000) {
        commentMessage.textContent =
            "댓글은 1000자 이하로 작성해 주세요.";
        return;
    }

    // 등록 버튼 가져오기
    const submitButton = commentForm.querySelector(
        'button[type="submit"]'
    );

    // 중복 제출 방지
    submitButton.disabled = true;
    commentMessage.textContent = "댓글을 등록하는 중입니다.";

    try {
        // 댓글 등록 요청
        await apiRequest(
            `/posts/${encodeURIComponent(postId)}/comments`,
            {
                method: "POST",
                body: JSON.stringify({ content })
            }
        );

        // 입력창 초기화
        commentContent.value = "";
        commentMessage.textContent = "댓글이 등록되었습니다.";

        // 최신 댓글 목록 조회
        await loadComments();

    } catch (error) {
        console.error("댓글 작성 실패:", error);

        commentMessage.textContent =
            `댓글 작성 실패: ${error.message}`;

    } finally {
        // 성공 여부와 관계없이 버튼 다시 활성화
        submitButton.disabled = false;
    }
});

// ========================================
// 12. 댓글 수정
// ========================================

async function updateComment(comment) {
    // 수정할 댓글 내용 입력
    const newContent = prompt(
        "수정할 댓글 내용을 입력해 주세요.",
        comment.content
    );

    // 취소한 경우 종료
    if (newContent === null) {
        return;
    }

    const content = newContent.trim();

    // 댓글 내용 검사
    if (!content) {
        alert("댓글 내용을 입력해 주세요.");
        return;
    }

    if (content.length > 1000) {
        alert("댓글은 1000자 이하로 작성해 주세요.");
        return;
    }

    try {
        // 댓글 수정 요청
        await apiRequest(
            `/comments/${encodeURIComponent(comment.id)}`,
            {
                method: "PUT",
                body: JSON.stringify({ content })
            }
        );

        alert("댓글이 수정되었습니다.");

        // 수정된 댓글 목록 다시 조회
        await loadComments();

    } catch (error) {
        console.error("댓글 수정 실패:", error);
        alert(`댓글 수정 실패: ${error.message}`);
    }
}

// ========================================
// 13. 댓글 삭제
// ========================================

async function deleteComment(commentId, button) {
    // 삭제 확인
    if (!confirm("이 댓글을 삭제하시겠습니까?")) {
        return;
    }

    // 중복 요청 방지
    button.disabled = true;

    try {
        // 댓글 삭제 요청
        await apiRequest(
            `/comments/${encodeURIComponent(commentId)}`,
            { method: "DELETE" }
        );

        commentMessage.textContent = "댓글이 삭제되었습니다.";

        // 최신 댓글 목록 조회
        await loadComments();

    } catch (error) {
        console.error("댓글 삭제 실패:", error);
        alert(`댓글 삭제 실패: ${error.message}`);

    } finally {
        // 버튼 다시 활성화
        button.disabled = false;
    }
}

// ========================================
// 14. 게시글 삭제
// ========================================

deleteButton.addEventListener("click", async () => {
    // 게시글 ID 유효성 검사
    if (!postId || !/^\d+$/.test(postId)) {
        alert("올바르지 않은 게시글 번호입니다.");
        return;
    }

    // 삭제 확인
    if (!confirm("정말 이 게시글을 삭제하시겠습니까?")) {
        return;
    }

    // 중복 요청 방지
    deleteButton.disabled = true;

    try {
        // 게시글 삭제 요청
        await apiRequest(
            `/posts/${encodeURIComponent(postId)}`,
            { method: "DELETE" }
        );

        alert("게시글이 삭제되었습니다.");

        // 게시글 목록으로 이동
        window.location.href = "/";

    } catch (error) {
        console.error("게시글 삭제 실패:", error);
        alert(`삭제 실패: ${error.message}`);

    } finally {
        // 페이지 이동이 실패한 경우 버튼 복구
        deleteButton.disabled = false;
    }
});

// ========================================
// 15. 페이지 최초 실행
// ========================================

loadPostDetail();
