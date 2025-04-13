package com.example.model.auth;

import com.example.enumerate.member.LoginType;
import com.example.model.member.MemberModel;
import lombok.Builder;
import lombok.Getter;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.oauth2.core.user.OAuth2User;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Map;

@Builder
@Getter
public class CustomMemberDetails implements UserDetails, OAuth2User {

    MemberModel memberModel;

    Map<String, Object> attributes;

    String attributeKey;

    LoginType loginType;

    public CustomMemberDetails(MemberModel memberModel,
                               Map<String,Object>attributes,
                               String attributeKey,
                               LoginType loginType) {
        this.memberModel = memberModel;
        this.attributes = attributes;
        this.attributeKey = attributeKey;
        this.loginType = loginType;
    }

    @Override
    public Map<String, Object> getAttributes() {
        return attributes;
    }

    @Override
    public String getName() {
        return attributes.get(attributeKey).toString();
    }

    @Override
    public boolean isAccountNonExpired() {
        return true;
    }

    @Override
    public boolean isAccountNonLocked() {
        return true;
    }

    @Override
    public boolean isCredentialsNonExpired() {
        return true;
    }

    @Override
    public boolean isEnabled() {
        return true;
    }

    @Override
    public Collection<? extends GrantedAuthority> getAuthorities() {
        Collection<GrantedAuthority> authorities = new ArrayList<>();
        authorities.add(() -> memberModel.getRoles().getValue());
        return authorities;
    }

    @Override
    public String getPassword() {
        if (loginType == LoginType.NORMAL) {
            return memberModel.getPassword(); // 일반 로그인이면 비밀번호 리턴
        } else {
            return null; // 소셜 로그인은 비밀번호 없음
        }
    }

    @Override
    public String getUsername() {
        return memberModel.getUserId();
    }
}
