package com.example.devboard.controller;

import com.example.devboard.dto.request.MemberLoginRequest;
import com.example.devboard.dto.request.MemberSignupRequest;
import com.example.devboard.dto.request.MemberWithdrawalRequest;
import com.example.devboard.dto.response.MemberLoginResponse;

import com.example.devboard.entity.Member;
import com.example.devboard.exception.BusinessException;
import com.example.devboard.exception.ErrorCode;
import com.example.devboard.service.MemberService;

import org.junit.jupiter.api.Test;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

// MemberController만 대상으로 MVC 테스트 실행
@WebMvcTest(MemberController.class)

// Spring Security 필터를 비활성화하여 컨트롤러 동작에 집중
@AutoConfigureMockMvc(addFilters = false)
class MemberControllerTest {

    @Autowired
    private MockMvc mockMvc;

    // 실제 MemberService 대신 Mockito 객체 사용
    @MockitoBean
    private MemberService memberService;

    // =====================================================
    // 1. 회원가입 성공 테스트
    // =====================================================

    @Test
    void signup_success() throws Exception {

        // given: 회원가입 성공 시 반환할 회원 객체
        Member member = mock(Member.class);

        when(member.getId()).thenReturn(1L);
        when(member.getEmail()).thenReturn("test@test.com");
        when(member.getNickname()).thenReturn("테스트회원");

        when(memberService.signup(any(MemberSignupRequest.class)))
                .thenReturn(member);

        // when & then: 회원가입 요청 및 응답 검증
        mockMvc.perform(post("/api/members/signup")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                    "email": "test@test.com",
                                    "password": "password123",
                                    "nickname": "테스트회원"
                                }
                                """))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id").value(1))
                .andExpect(jsonPath("$.email").value("test@test.com"))
                .andExpect(jsonPath("$.nickname").value("테스트회원"))
                .andExpect(jsonPath("$.message").value("회원가입이 완료되었습니다."))
                .andExpect(jsonPath("$.password").doesNotExist());

        // 회원가입 서비스 호출 여부 확인
        verify(memberService).signup(any(MemberSignupRequest.class));
    }

    // =====================================================
    // 2. 회원가입 중복 이메일 테스트
    // =====================================================

    @Test
    void signup_duplicateEmail_returnsConflict() throws Exception {

        // given: 중복 이메일이면 BusinessException 발생
        when(memberService.signup(any(MemberSignupRequest.class)))
                .thenThrow(
                        new BusinessException(ErrorCode.DUPLICATE_EMAIL)
                );

        // when & then: HTTP 409 및 오류 코드 검증
        mockMvc.perform(post("/api/members/signup")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                    "email": "test@test.com",
                                    "password": "password123",
                                    "nickname": "테스트회원"
                                }
                                """))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code")
                        .value(ErrorCode.DUPLICATE_EMAIL.getCode()))
                .andExpect(jsonPath("$.message")
                        .value(ErrorCode.DUPLICATE_EMAIL.getMessage()));

        // 회원가입 서비스 호출 여부 확인
        verify(memberService).signup(any(MemberSignupRequest.class));
    }

    // =====================================================
    // 3. 로그인 성공 테스트
    // =====================================================

    @Test
    void login_success() throws Exception {

        // given: 로그인 성공 응답 객체
        MemberLoginResponse response = new MemberLoginResponse(
                1L,
                "test@test.com",
                "테스트회원",
                "test-access-token",
                "Bearer"
        );

        when(memberService.login(any(MemberLoginRequest.class)))
                .thenReturn(response);

        // when & then: 로그인 성공 및 JWT 정보 검증
        mockMvc.perform(post("/api/members/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                    "email": "test@test.com",
                                    "password": "password123"
                                }
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.memberId").value(1))
                .andExpect(jsonPath("$.email").value("test@test.com"))
                .andExpect(jsonPath("$.nickname").value("테스트회원"))
                .andExpect(jsonPath("$.accessToken").value("test-access-token"))
                .andExpect(jsonPath("$.tokenType").value("Bearer"));

        // 로그인 서비스 호출 여부 확인
        verify(memberService).login(any(MemberLoginRequest.class));
    }

    // =====================================================
    // 4. 로그인 실패 테스트
    // =====================================================

    @Test
    void login_wrongCredentials_returnsUnauthorized() throws Exception {

        // given: 잘못된 로그인 정보로 인증 실패 예외 발생
        when(memberService.login(any(MemberLoginRequest.class)))
                .thenThrow(
                        new BusinessException(ErrorCode.INVALID_CREDENTIALS)
                );

        // when & then: HTTP 401 및 오류 코드 검증
        mockMvc.perform(post("/api/members/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                    "email": "test@test.com",
                                    "password": "wrongPassword"
                                }
                                """))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code")
                        .value(ErrorCode.INVALID_CREDENTIALS.getCode()))
                .andExpect(jsonPath("$.message")
                        .value(ErrorCode.INVALID_CREDENTIALS.getMessage()));

        // 로그인 서비스 호출 여부 확인
        verify(memberService).login(any(MemberLoginRequest.class));
    }

    // =====================================================
    // 5. 회원가입 입력값 검증 실패 테스트
    // =====================================================

    @Test
    void signup_invalidRequest_returnsBadRequest() throws Exception {

        // given: 이메일 형식이 올바르지 않은 요청
        String invalidRequest = """
                {
                    "email": "invalid-email",
                    "password": "password123",
                    "nickname": "테스트회원"
                }
                """;

        // when & then: Bean Validation 실패 시 HTTP 400
        mockMvc.perform(post("/api/members/signup")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(invalidRequest))
                .andExpect(status().isBadRequest());

        // 입력값 검증 실패 시 서비스가 호출되지 않아야 함
        verifyNoInteractions(memberService);
    }

    // =====================================================
    // 6. 회원 탈퇴 성공 테스트
    // =====================================================

    @Test
    void withdraw_success() throws Exception {

        // given: 로그인한 회원의 인증 정보 생성
        UsernamePasswordAuthenticationToken authentication =
                new UsernamePasswordAuthenticationToken(
                        "test@test.com",
                        null,
                        List.of()
                );

        // when & then: 회원 탈퇴 요청
        mockMvc.perform(delete("/api/members/me")
                        .principal(authentication)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                    "password": "password123"
                                }
                                """))
                .andExpect(status().isNoContent())
                .andExpect(content().string(""));

        // 인증된 회원의 이메일과 탈퇴 요청이 전달되는지 확인
        verify(memberService).withdraw(
                eq("test@test.com"),
                any(MemberWithdrawalRequest.class)
        );
    }
}
