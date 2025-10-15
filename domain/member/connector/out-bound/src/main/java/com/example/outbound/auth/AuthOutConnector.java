package com.example.outbound.auth;

import com.example.enumerate.member.LoginType;
import com.example.model.auth.CustomMemberDetails;
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

    @Override
    public UserDetails loadUserByUsername(String username) throws UsernameNotFoundException {
        Member userDetails = memberRepository.findByUserId(username)
                .orElseThrow(()->new UsernameNotFoundException("회원을 찾을 수 없습니다."));
        MemberModel toModel = toMemberModel(userDetails);
        return toAuthModel(toModel);
    }

    //entity -> model
    private MemberModel toMemberModel(Member member) {
        return MemberModel
                .builder()
                .id(member.getId())
                .password(member.getPassword())
                .userId(member.getUserId())
                .userName(member.getUserName())
                .userEmail(member.getUserEmail())
                .userPhone(member.getUserPhone())
                .roles(member.getRoles())
                .createdBy(member.getCreatedBy())
                .createdTime(member.getCreatedTime())
                .updatedBy(member.getUpdatedBy())
                .updatedTime(member.getUpdatedTime())
                .build();
    }

    //authModel  -> Entity
    private CustomMemberDetails toAuthModel(MemberModel memberModel) {
        return CustomMemberDetails
                .builder()
                .memberModel(memberModel)
                .attributes(null)
                .attributeKey(null)
                .loginType(LoginType.NORMAL)
                .build();
    }
}


