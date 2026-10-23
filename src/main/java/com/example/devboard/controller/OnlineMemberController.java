package com.example.devboard.controller;

import com.example.devboard.entity.Member;
import com.example.devboard.repository.MemberRepository;

import org.springframework.messaging.simp.user.SimpUser;
import org.springframework.messaging.simp.user.SimpUserRegistry;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Comparator;
import java.util.List;
import java.util.Objects;

@RestController
@RequestMapping("/api/members")
public class OnlineMemberController {

    private final MemberRepository memberRepository;
    private final SimpUserRegistry simpUserRegistry;

    public OnlineMemberController(
            MemberRepository memberRepository,
            SimpUserRegistry simpUserRegistry
    ) {
        this.memberRepository = memberRepository;
        this.simpUserRegistry = simpUserRegistry;
    }

    // 현재 접속 중인 다른 회원 목록 조회
    @GetMapping("/online")
    public List<OnlineMemberResponse> getOnlineMembers(
            Authentication authentication
    ) {
        // 로그인한 회원의 이메일
        String currentEmail = authentication.getName();

        return simpUserRegistry.getUsers()
                .stream()
                // STOMP 연결 시 인증된 회원의 이메일을 사용
                .map(SimpUser::getName)
                // 본인은 목록에서 제외
                .filter(email -> !Objects.equals(email, currentEmail))
                // 이메일을 기준으로 DB에서 회원 조회
                .map(memberRepository::findByEmail)
                .flatMap(java.util.Optional::stream)
                // 필요한 정보만 응답
                .map(member -> new OnlineMemberResponse(
                        member.getId(),
                        member.getNickname()
                ))
                // 닉네임 순으로 정렬
                .sorted(Comparator.comparing(
                        OnlineMemberResponse::nickname,
                        Comparator.nullsLast(String.CASE_INSENSITIVE_ORDER)
                ))
                .toList();
    }

    // 비밀번호와 이메일 등 불필요한 정보는 반환하지 않음
    public record OnlineMemberResponse(
            Long memberId,
            String nickname
    ) {
    }
}
