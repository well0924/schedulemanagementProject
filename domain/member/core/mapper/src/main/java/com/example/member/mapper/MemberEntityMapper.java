package com.example.member.mapper;

import com.example.model.member.MemberModel;
import com.example.rdb.member.Member;
import org.springframework.stereotype.Component;

@Component
public class MemberEntityMapper {

    public MemberModel toEntity(Member memberEntity) {
        return MemberModel.builder()
                .id(memberEntity.getId())
                .userId(memberEntity.getUserId())
                .password(memberEntity.getPassword())
                .userEmail(memberEntity.getUserEmail())
                .userPhone(memberEntity.getUserPhone())
                .userName(memberEntity.getUserName())
                .roles(memberEntity.getRoles())
                .createdBy(memberEntity.getCreatedBy())
                .createdTime(memberEntity.getCreatedTime())
                .updatedBy(memberEntity.getUpdatedBy())
                .updatedTime(memberEntity.getUpdatedTime())
                .build();
    }

}
