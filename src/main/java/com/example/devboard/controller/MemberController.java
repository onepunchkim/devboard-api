package com.example.devboard.controller;

import com.example.devboard.dto.request.MemberLoginRequest;
import com.example.devboard.dto.response.MemberLoginResponse;
import com.example.devboard.dto.request.MemberSignupRequest;
import com.example.devboard.entity.Member;
import com.example.devboard.service.MemberService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

import java.util.Map;

@RestController
@RequestMapping("/api/members")
public class MemberController {

    private final MemberService memberService;

    public MemberController(MemberService memberService) {
        this.memberService = memberService;
    }

    @PostMapping("/signup")
    @ResponseStatus(HttpStatus.CREATED)
    public Map<String, Object> signup(
            @Valid @RequestBody MemberSignupRequest request
            ) {

        try {
            Member member = memberService.signup(request);

            //비밀번호는 응답에 포함하지 않음
            return Map.of(
                    "id", member.getId(),
                    "email", member.getEmail(),
                    "nickname", member.getNickname(),
                    "message", "회원가입이 완료되었습니다."
            );

        } catch (IllegalArgumentException e) {
            throw new ResponseStatusException(
                    HttpStatus.CONFLICT,
                    e.getMessage()
            );
        }
    }

    @PostMapping("/login")
    public MemberLoginResponse login(
            @Valid @RequestBody MemberLoginRequest request
    ) {
        return memberService.login(request);
    }
}
