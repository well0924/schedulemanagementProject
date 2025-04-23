package com.example.outconnector.auth;

import com.example.enumerate.member.LoginType;
import com.example.enumerate.member.Roles;
import com.example.model.auth.CustomMemberDetails;
import com.example.model.auth.OAuth2UserInfo;
import com.example.model.member.MemberModel;
import com.example.rdb.member.Member;
import com.example.rdb.member.MemberRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.security.oauth2.client.userinfo.DefaultOAuth2UserService;
import org.springframework.security.oauth2.client.userinfo.OAuth2UserRequest;
import org.springframework.security.oauth2.core.OAuth2AuthenticationException;
import org.springframework.security.oauth2.core.user.OAuth2User;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.Map;

@Component
@RequiredArgsConstructor
public class AuthOutConnector implements UserDetailsService {

    private final MemberRepository memberRepository;

    @Transactional
    @Override
    public UserDetails loadUserByUsername(String username) throws UsernameNotFoundException {
        Member userDetails = memberRepository.findByUserId(username)
                .orElseThrow(()->new UsernameNotFoundException("회원을 찾을 수 없습니다."));
        MemberModel toModel = toMemberModel(userDetails);
        return toAuthModel(toModel);
    }

    //entity -> model
    private MemberModel toMemberModel(Member member){
        return MemberModel.builder()
                .id(member.getId())
                .password(member.getPassword())
                .userId(member.getUserId())
                .userName(member.getUserName())
                .userEmail(member.getUserEmail())
                .userPhone(member.getUserPhone())
                .roles(member.getRoles())
                .createdBy(member.getCreatedBy())
                .updatedBy(member.getUpdatedBy())
                .createdTime(member.getCreatedTime())
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

    private CustomMemberDetails toOAuth2Model(MemberModel memberModel) {
        return CustomMemberDetails
                .builder()
                .memberModel(memberModel)
                .attributes(null)
                .attributeKey(null)
                .loginType(LoginType.NORMAL)
                .build();
    }
}
