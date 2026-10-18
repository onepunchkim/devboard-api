
const signupForm = document.querySelector("#signupForm");
const loginForm = document.querySelector("#loginForm");
const message = document.querySelector("#message");

// 회원가입
if (signupForm) {
    signupForm.addEventListener("submit", async (event) => {
        event.preventDefault();
        message.textContent = "회원가입 처리 중입니다.";

        const request = {
            email: document.querySelector("#email").value.trim(),
            nickname: document.querySelector("#nickname").value.trim(),
            password: document.querySelector("#password").value
        };

        try {
            await apiRequest("/members/signup", {
                method: "POST",
                body: JSON.stringify(request)
            });

            alert("회원가입이 완료되었습니다. 로그인해 주세요.");
            window.location.href = "/login.html";
        } catch (error) {
            message.textContent = error.message;
        }
    });
}

// 로그인
if (loginForm) {
    loginForm.addEventListener("submit", async (event) => {
        event.preventDefault();
        message.textContent = "로그인 처리 중입니다.";

        const request = {
            email: document.querySelector("#email").value.trim(),
            password: document.querySelector("#password").value
        };

        try {
            const result = await apiRequest("/members/login", {
                method: "POST",
                body: JSON.stringify(request)
            });

            // 서버가 반환한 실제 응답 필드에 맞춰 저장
            saveAuth(result);

            alert("로그인에 성공했습니다.");
            window.location.href = "/";
        } catch (error) {
            message.textContent = error.message;
        }
    });
}
