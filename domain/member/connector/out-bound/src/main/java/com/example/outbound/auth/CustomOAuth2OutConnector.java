package com.example.outbound.auth;

import com.example.enumerate.member.LoginType;
import com.example.enumerate.member.Roles;
import com.example.model.auth.CustomMemberDetails;
import com.example.model.auth.OAuth2UserInfo;
import com.example.model.member.MemberModel;
import com.example.rdb.member.Member;
import com.example.rdb.member.MemberRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.security.oauth2.client.userinfo.DefaultOAuth2UserService;
import org.springframework.security.oauth2.client.userinfo.OAuth2UserRequest;
import org.springframework.security.oauth2.core.OAuth2AuthenticationException;
import org.springframework.security.oauth2.core.user.OAuth2User;
import org.springframework.stereotype.Service;

import java.util.Map;

@Service
@RequiredArgsConstructor
public class CustomOAuth2OutConnector extends DefaultOAuth2UserService {

    private final MemberRepository memberRepository;

    @Override
    public OAuth2User loadUser(OAuth2UserRequest userRequest) throws OAuth2AuthenticationException {
        // 1. OAuth2 서버에서 유저 정보 가져오기
        Map<String, Object> attributes = super.loadUser(userRequest).getAttributes();

        // 2. registrationId 구하기 (google, kakao 등)
        String registrationId = userRequest.getClientRegistration().getRegistrationId();

        // 3. OAuth2UserInfo 파싱
        OAuth2UserInfo userInfo = OAuth2UserInfo.of(registrationId, attributes);

        // 4. DB에서 사용자 조회
        Member member = memberRepository.findByUserEmail(userInfo.getEmail())
                .orElseGet(() -> {
                    // 5. 없으면 신규 회원가입
                    Member newMember = Member.builder()
                            .userEmail(userInfo.getEmail())
                            .userName(userInfo.getName())
                            .userId("social_" + System.currentTimeMillis()) // 소셜 전용 ID
                            .roles(Roles.ROLE_USER) // 기본 권한
                            .build();
                    return memberRepository.save(newMember);
                });

        // 6. Member -> MemberModel 변환
        MemberModel toModel = toMemberModel(member);

        // 7. CustomMemberDetails로 리턴
        return CustomMemberDetails.builder()
                .memberModel(toModel)
                .attributes(attributes)
                .attributeKey("sub") // 구글 기준 subject (다른 플랫폼은 다를 수 있음)
                .loginType(LoginType.SOCIAL)
                .build();
    }

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
}


