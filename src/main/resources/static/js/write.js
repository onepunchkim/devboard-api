const postForm = document.querySelector("#postForm");
const formTitle = document.querySelector("#formTitle");
const submitButton = document.querySelector("#submitButton");
const formMessage = document.querySelector("#formMessage");

const titleInput = document.querySelector("#title");
const contentInput = document.querySelector("#content");

// 이미지 관련 HTML 요소
const imageFilesInput = document.querySelector("#imageFiles");
const imagePreviewList = document.querySelector("#imagePreviewList");
const imageMessage = document.querySelector("#imageMessage");
const existingImageSection = document.querySelector("#existingImageSection");
const existingImageList = document.querySelector("#existingImageList");
const existingImageMessage = document.querySelector("#existingImageMessage");

// URL의 게시글 ID를 확인해 작성 모드와 수정 모드를 구분
const params = new URLSearchParams(window.location.search);
const postId = params.get("id");
const isEditMode = postId !== null;

// 이미지 업로드 제한
const MAX_IMAGE_COUNT = 5;
const MAX_IMAGE_SIZE = 5 * 1024 * 1024;

// 브라우저에서 허용할 이미지 형식
const ALLOWED_IMAGE_TYPES = [
    "image/jpeg",
    "image/png",
    "image/webp"
];

// 현재 서버에 등록된 이미지 목록
let existingImages = [];

// 새로 선택한 이미지 파일 목록
let selectedImageFiles = [];

// 미리보기 URL을 정리하기 위한 목록
let previewUrls = [];

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

// 이미지 경로를 브라우저에서 사용할 URL로 변환
function getImageUrl(imagePath) {
    if (!imagePath) {
        return "";
    }

    // 서버가 반환한 이미지 경로가 /로 시작하면 그대로 사용
    if (imagePath.startsWith("/")) {
        return imagePath;
    }

    // 상대 경로인 경우 앞에 /를 추가
    return `/${imagePath}`;
}

// 새 이미지 미리보기 URL 정리
function clearPreviewUrls() {
    previewUrls.forEach((url) => {
        URL.revokeObjectURL(url);
    });

    previewUrls = [];
}

// 선택한 이미지의 미리보기 표시
function renderImagePreviews() {
    // 이전 미리보기와 URL 정리
    clearPreviewUrls();
    imagePreviewList.replaceChildren();

    selectedImageFiles.forEach((file, index) => {
        const item = document.createElement("div");
        item.className = "image-preview-item";

        const image = document.createElement("img");
        image.alt = file.name;

        // 파일을 서버에 업로드하기 전에 로컬 미리보기 생성
        const previewUrl = URL.createObjectURL(file);
        previewUrls.push(previewUrl);
        image.src = previewUrl;

        const filename = document.createElement("p");
        filename.textContent = file.name;

        // 선택한 파일만 목록에서 제거하는 버튼
        const removeButton = document.createElement("button");
        removeButton.type = "button";
        removeButton.textContent = "선택 취소";
        removeButton.className = "button secondary";

        removeButton.addEventListener("click", () => {
            selectedImageFiles.splice(index, 1);

            // 파일 입력 요소에도 변경 사항 반영
            updateImageFileInput();
            renderImagePreviews();
        });

        item.append(image, filename, removeButton);
        imagePreviewList.appendChild(item);
    });
}

// 선택한 파일 목록을 input 요소에 반영
function updateImageFileInput() {
    const dataTransfer = new DataTransfer();

    selectedImageFiles.forEach((file) => {
        dataTransfer.items.add(file);
    });

    imageFilesInput.files = dataTransfer.files;
}

// 이미지 파일 선택 이벤트
imageFilesInput.addEventListener("change", () => {
    imageMessage.textContent = "";

    const newlySelectedFiles = Array.from(imageFilesInput.files);

    // 기존에 선택한 파일과 새로 선택한 파일을 합침
    const combinedFiles = [
        ...selectedImageFiles,
        ...newlySelectedFiles
    ];

    // 기존 이미지와 새 이미지의 총 개수 검사
    if (existingImages.length + combinedFiles.length > MAX_IMAGE_COUNT) {
        imageMessage.textContent =
            `이미지는 게시글당 최대 ${MAX_IMAGE_COUNT}장까지 등록할 수 있습니다.`;

        updateImageFileInput();
        return;
    }

    // 선택한 파일의 형식과 크기 검사
    for (const file of newlySelectedFiles) {
        if (!ALLOWED_IMAGE_TYPES.includes(file.type)) {
            imageMessage.textContent =
                `${file.name}: JPG, PNG, WEBP 파일만 선택할 수 있습니다.`;

            updateImageFileInput();
            return;
        }

        if (file.size > MAX_IMAGE_SIZE) {
            imageMessage.textContent =
                `${file.name}: 이미지 파일은 5MB 이하여야 합니다.`;

            updateImageFileInput();
            return;
        }

        if (file.size === 0) {
            imageMessage.textContent =
                `${file.name}: 빈 파일은 업로드할 수 없습니다.`;

            updateImageFileInput();
            return;
        }
    }

    // 검증이 완료된 파일 목록 저장
    selectedImageFiles = combinedFiles;

    updateImageFileInput();
    renderImagePreviews();
});

// 수정 모드에서 기존 이미지 조회
async function loadExistingImages() {
    if (!isEditMode || !/^\d+$/.test(postId)) {
        return;
    }

    existingImageSection.hidden = false;
    existingImageMessage.textContent = "기존 이미지를 불러오는 중입니다.";

    try {
        // 게시글에 등록된 이미지 목록 조회
        const images = await apiRequest(
            `/posts/${encodeURIComponent(postId)}/images`,
            { method: "GET" }
        );

        existingImages = Array.isArray(images) ? images : [];

        renderExistingImages();

        existingImageMessage.textContent =
            existingImages.length > 0
                ? `등록된 이미지 ${existingImages.length}장`
                : "등록된 이미지가 없습니다.";
    } catch (error) {
        existingImageMessage.textContent =
            `이미지를 불러오지 못했습니다: ${error.message}`;
    }
}

// 기존 이미지 표시
function renderExistingImages() {
    // 기존 이미지 목록 초기화
    existingImageList.replaceChildren();

    existingImages.forEach((imageData) => {
        // 이미지 카드 생성
        const item = document.createElement("div");
        item.className = "image-preview-item";

        // 이미지 미리보기
        const image = document.createElement("img");
        image.src = getImageUrl(imageData.imagePath);
        image.alt = imageData.originalFilename || "게시글 이미지";

        // 파일명 표시
        const filename = document.createElement("p");
        filename.textContent =
            imageData.originalFilename || "이미지";

        // 이미지 삭제 버튼
        const deleteButton = document.createElement("button");
        deleteButton.type = "button";
        deleteButton.textContent = "사진 삭제";
        deleteButton.className = "button secondary";

        // 삭제 버튼 클릭 이벤트
        deleteButton.addEventListener("click", async () => {
            // 삭제 전 사용자 확인
            const confirmed = confirm(
                "이 사진을 삭제하시겠습니까?\n삭제한 사진은 복구할 수 없습니다."
            );

            if (!confirmed) {
                return;
            }

            // 중복 요청 방지
            deleteButton.disabled = true;
            existingImageMessage.textContent = "이미지를 삭제하는 중입니다.";

            try {
                // 서버에 이미지 삭제 요청
                await apiRequest(
                    `/posts/${encodeURIComponent(postId)}/images/${encodeURIComponent(imageData.id)}`,
                    {
                        method: "DELETE"
                    }
                );

                // 화면에 표시 중인 이미지 목록에서도 제거
                existingImages = existingImages.filter(
                    (item) => item.id !== imageData.id
                );

                // 기존 이미지 화면 갱신
                renderExistingImages();

                // 이미지 개수 및 안내 메시지 갱신
                existingImageMessage.textContent =
                    existingImages.length > 0
                        ? `등록된 이미지 ${existingImages.length}장`
                        : "등록된 이미지가 없습니다.";

                // 이미지 삭제 후 새 이미지 선택 가능 개수 안내
                imageMessage.textContent =
                    `현재 등록된 이미지: ${existingImages.length}장 / 최대 ${MAX_IMAGE_COUNT}장`;

            } catch (error) {
                // 삭제 실패 메시지 표시
                existingImageMessage.textContent =
                    `이미지 삭제에 실패했습니다: ${error.message}`;
            } finally {
                // 실패하더라도 버튼을 다시 활성화
                deleteButton.disabled = false;
            }
        });

        // 이미지 카드에 구성 요소 추가
        item.append(image, filename, deleteButton);
        existingImageList.appendChild(item);
    });
}

// 게시글 수정 모드에서 기존 게시글 정보 불러오기
async function loadPostForEdit() {
    if (!isEditMode || !/^\d+$/.test(postId)) {
        return;
    }

    formTitle.textContent = "게시글 수정";
    submitButton.textContent = "수정 완료";
    formMessage.textContent = "게시글을 불러오는 중입니다.";

    try {
        // 기존 게시글 정보 조회
        const post = await apiRequest(
            `/posts/${encodeURIComponent(postId)}`,
            { method: "GET" }
        );

        titleInput.value = post.title ?? "";
        contentInput.value = post.content ?? "";
        formMessage.textContent = "";

        // 기존 이미지도 함께 조회
        await loadExistingImages();
    } catch (error) {
        formMessage.textContent =
            `게시글을 불러오지 못했습니다: ${error.message}`;

        submitButton.disabled = true;
    }
}

// 새로 선택한 이미지 파일 업로드
async function uploadSelectedImages(targetPostId) {
    if (selectedImageFiles.length === 0) {
        return;
    }

    // 여러 이미지 파일을 multipart/form-data로 전송
    const formData = new FormData();

    selectedImageFiles.forEach((file) => {
        formData.append("files", file);
    });

    // api.js에서 FormData 요청의 Content-Type을 자동 지정하는지 확인 필요
    await apiRequest(
        `/posts/${encodeURIComponent(targetPostId)}/images`,
        {
            method: "POST",
            body: formData
        }
    );
}

// 게시글 등록 또는 수정
postForm.addEventListener("submit", async (event) => {
    event.preventDefault();

    // 로그인 상태 재확인
    if (!isLoggedIn()) {
        alert("로그인이 필요합니다.");
        window.location.href = "/login.html";
        return;
    }

    if (isEditMode && !/^\d+$/.test(postId)) {
        formMessage.textContent = "올바르지 않은 게시글 번호입니다.";
        return;
    }

    // 입력값 공백 제거
    const request = {
        title: titleInput.value.trim(),
        content: contentInput.value.trim()
    };

    if (!request.title || !request.content) {
        formMessage.textContent = "제목과 내용을 입력해 주세요.";
        return;
    }

    // 기존 이미지와 새 이미지의 총 개수 검사
    if (existingImages.length + selectedImageFiles.length > MAX_IMAGE_COUNT) {
        formMessage.textContent =
            `이미지는 게시글당 최대 ${MAX_IMAGE_COUNT}장까지 등록할 수 있습니다.`;
        return;
    }

    submitButton.disabled = true;
    imageFilesInput.disabled = true;
    formMessage.textContent = "게시글을 저장하는 중입니다.";

    try {
        let targetPostId;

        if (isEditMode) {
            // 기존 게시글 제목과 내용 수정
            await apiRequest(
                `/posts/${encodeURIComponent(postId)}`,
                {
                    method: "PUT",
                    body: JSON.stringify(request)
                }
            );

            targetPostId = postId;
        } else {
            // 게시글을 먼저 생성해 ID 확보
            const result = await apiRequest("/posts", {
                method: "POST",
                body: JSON.stringify(request)
            });

            if (!result || result.id == null) {
                throw new Error(
                    "게시글은 생성되었지만 ID를 확인할 수 없습니다. 게시글 목록에서 확인해 주세요."
                );
            }

            targetPostId = result.id;
        }

        // 새로 선택한 이미지가 있다면 업로드
        if (selectedImageFiles.length > 0) {
            formMessage.textContent = "이미지를 업로드하는 중입니다.";

            await uploadSelectedImages(targetPostId);
        }

        alert(
            isEditMode
                ? "게시글이 수정되었습니다."
                : "게시글이 등록되었습니다."
        );

        // 저장 완료 후 상세 페이지로 이동
        window.location.href =
            `/post-detail.html?id=${encodeURIComponent(targetPostId)}`;

    } catch (error) {
        formMessage.textContent =
            `저장에 실패했습니다: ${error.message}`;
    } finally {
        submitButton.disabled = false;
        imageFilesInput.disabled = false;
    }
});

// 작성 모드와 수정 모드에 맞게 화면 초기화
if (isEditMode) {
    loadPostForEdit();
} else {
    // 새 게시글 작성 시 이미지 영역 초기화
    existingImageSection.hidden = true;
}