package com.example.devboard.controller;

import com.example.devboard.dto.request.MemberLoginRequest;
import com.example.devboard.dto.request.MemberSignupRequest;
import com.example.devboard.dto.response.MemberLoginResponse;
import com.example.devboard.entity.Member;
import com.example.devboard.service.MemberService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

// MemberController만 대상으로 MVC 테스트를 실행한다.
@WebMvcTest(MemberController.class)

// 테스트에서는 Spring Security 필터를 비활성화하여 컨트롤러 동작에 집중한다.
@AutoConfigureMockMvc(addFilters = false)
class MemberControllerTest {

    @Autowired
    private MockMvc mockMvc;

    // 실제 MemberService 대신 Mockito 모의 객체를 사용한다.
    @MockitoBean
    private MemberService memberService;

    @Test
    void signup_success() throws Exception {
        // given: 회원가입 성공 시 반환할 회원 객체 설정
        Member member = mock(Member.class);

        when(member.getId()).thenReturn(1L);
        when(member.getEmail()).thenReturn("test@test.com");
        when(member.getNickname()).thenReturn("테스트회원");

        when(memberService.signup(any(MemberSignupRequest.class)))
                .thenReturn(member);

        // when & then: 회원가입 요청 후 HTTP 201 및 응답 데이터 검증
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

        // 회원가입 서비스가 호출되었는지 확인
        verify(memberService).signup(any(MemberSignupRequest.class));
    }

    @Test
    void signup_duplicateEmail_returnsConflict() throws Exception {
        // given: 중복 이메일이면 서비스에서 예외가 발생하도록 설정
        when(memberService.signup(any(MemberSignupRequest.class)))
                .thenThrow(new IllegalArgumentException("이미 사용 중인 이메일 입니다."));

        // when & then: 중복 이메일 요청에 HTTP 409가 반환되는지 검증
        mockMvc.perform(post("/api/members/signup")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                    "email": "test@test.com",
                                    "password": "password123",
                                    "nickname": "테스트회원"
                                }
                                """))
                .andExpect(status().isConflict());
    }

    @Test
    void login_success() throws Exception {
        // given: 로그인 성공 시 반환할 응답 객체 설정
        MemberLoginResponse response = new MemberLoginResponse(
                1L,
                "test@test.com",
                "테스트회원",
                "test-access-token",
                "Bearer"
        );

        when(memberService.login(any(MemberLoginRequest.class)))
                .thenReturn(response);

        // when & then: 로그인 요청 후 HTTP 200 및 토큰 정보 검증
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
    }

    @Test
    void login_wrongCredentials_returnsUnauthorized() throws Exception {
        // given: 잘못된 로그인 정보로 인증 예외가 발생하도록 설정
        when(memberService.login(any(MemberLoginRequest.class)))
                .thenThrow(new BadCredentialsException(
                        "이메일 또는 비밀번호가 올바르지 않습니다."
                ));

        // when & then: 인증 실패 시 HTTP 401이 반환되는지 검증
        mockMvc.perform(post("/api/members/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                    "email": "test@test.com",
                                    "password": "wrongPassword"
                                }
                                """))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void signup_invalidRequest_returnsBadRequest() throws Exception {
        // given: 이메일 형식이 잘못된 요청 데이터
        String invalidRequest = """
                {
                    "email": "invalid-email",
                    "password": "password123",
                    "nickname": "테스트회원"
                }
                """;

        // when & then: @Valid 검증 실패 시 HTTP 400이 반환되는지 확인
        mockMvc.perform(post("/api/members/signup")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(invalidRequest))
                .andExpect(status().isBadRequest());

        // 입력값 검증에 실패하면 서비스가 호출되지 않아야 한다.
        verifyNoInteractions(memberService);
    }
}