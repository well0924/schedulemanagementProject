package com.example.member.mapper;

import com.example.apimodel.member.MemberApiModel;
import com.example.enumerate.member.LoginType;
import com.example.enumerate.member.Roles;
import com.example.model.auth.CustomMemberDetails;
import com.example.model.member.MemberModel;
import com.example.rdb.member.Member;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;

@Component
public class MemberModelMapper {

    public MemberModel toMemberModel(Member member) {
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

    public CustomMemberDetails toAuthModel(MemberModel memberModel) {
        return CustomMemberDetails
                .builder()
                .memberModel(memberModel)
                .attributes(null)
                .attributeKey(null)
                .loginType(LoginType.NORMAL)
                .build();
    }

    public MemberApiModel.MemberResponse toResponse(MemberModel memberModel) {
        return MemberApiModel.MemberResponse
                .builder()
                .id(memberModel.getId())
                .userId(memberModel.getUserId())
                .userEmail(memberModel.getUserEmail())
                .userPhone(memberModel.getUserPhone())
                .userName(memberModel.getUserName())
                .roles(memberModel.getRoles())
                .createdBy(memberModel.getCreatedBy())
                .updatedBy(memberModel.getUpdatedBy())
                .updatedTime(memberModel.getUpdatedTime())
                .createdTime(memberModel.getCreatedTime())
                .build();
    }

    //createRequest -> model
    public MemberModel toCreated(MemberApiModel.CreateRequest createRequest) {
        return MemberModel
                .builder()
                .userId(createRequest.userId())
                .password(createRequest.password())
                .userPhone(createRequest.userPhone())
                .userEmail(createRequest.userEmail())
                .userName(createRequest.userName())
                .roles(Roles.ROLE_USER)
                .createdBy(createRequest.userId())
                .updatedBy(createRequest.userId())
                .createdTime(LocalDateTime.now())
                .updatedTime(LocalDateTime.now())
                .build();
    }

    //updateRequest -> model
    public MemberModel toUpdate(MemberApiModel.UpdateRequest updateRequest) {
        return MemberModel
                .builder()
                .userId(updateRequest.userId())
                .userEmail(updateRequest.userEmail())
                .updatedBy(updateRequest.userId())
                .userPhone(updateRequest.userPhone())
                .userName(updateRequest.userName())
                .build();
    }

}
