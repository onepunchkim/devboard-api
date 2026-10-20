package com.example.devboard.service;

import com.example.devboard.dto.request.MemberSignupRequest;
import com.example.devboard.entity.Member;
import com.example.devboard.repository.MemberRepository;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import com.example.devboard.dto.request.MemberLoginRequest;
import com.example.devboard.dto.response.MemberLoginResponse;
import org.springframework.security.authentication.BadCredentialsException;

@Service
public class MemberService {

    private final MemberRepository memberRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtService jwtService;

    public MemberService(MemberRepository memberRepository, PasswordEncoder passwordEncoder, JwtService jwtService) {
        this.memberRepository = memberRepository;
        this.passwordEncoder = passwordEncoder;
        this.jwtService = jwtService;
    }

    @Transactional
    public Member signup(MemberSignupRequest request) {

        //이메일 중복 확인
        if (memberRepository.existsByEmail(request.email())) {
            throw new IllegalArgumentException("이미 사용 중인 이메일 입니다.");
        }

        //비밀번호 암호화
        String encodedPassword = passwordEncoder.encode(request.password());

        //회원 엔티티 생성
        Member member = new Member(
                request.email(),
                encodedPassword, //암호화한 값을 생성하여 저장
                request.nickname()
        );

        return memberRepository.save(member);
    }

    @Transactional
    public MemberLoginResponse login(MemberLoginRequest request) {
        Member member = memberRepository.findByEmail(request.email())
                .orElseThrow(() -> new BadCredentialsException("이메일 또는 비밀번호가 올바르지 않습니다"));

        if (!passwordEncoder.matches(
                request.password(),
                member.getPassword()
        )) {
            throw new BadCredentialsException("이메일 또는 비밀번호가 올바르지 않습니다.");
        }

        String accessToken = jwtService.generateToken(
                member.getId(),
                member.getEmail()
        );

        return new MemberLoginResponse(
                member.getId(),
                member.getEmail(),
                member.getNickname(),
                accessToken,
                "Bearer"
        );
    }
}
