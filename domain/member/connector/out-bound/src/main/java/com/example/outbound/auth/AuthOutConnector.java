package com.example.outbound.auth;

import com.example.member.mapper.MemberModelMapper;
import com.example.model.member.MemberModel;
import com.example.rdb.member.Member;
import com.example.rdb.member.MemberRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class AuthOutConnector implements UserDetailsService{

    private final MemberRepository memberRepository;

    private final MemberModelMapper memberModelMapper;

    @Override
    public UserDetails loadUserByUsername(String username) throws UsernameNotFoundException {
        Member userDetails = memberRepository.findByUserId(username)
                .orElseThrow(()->new UsernameNotFoundException("회원을 찾을 수 없습니다."));
        MemberModel toModel = memberModelMapper.toMemberModel(userDetails);
        return memberModelMapper.toAuthModel(toModel);
    }

}


