package com.example.inbound.member;

import com.example.apimodel.member.MemberApiModel;
import com.example.enumerate.member.Roles;
import com.example.enumerate.member.SearchType;
import com.example.interfaces.member.MemberInterfaces;
import com.example.model.member.MemberModel;
import com.example.service.member.MemberService;
import lombok.AllArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;

@Component
@AllArgsConstructor
public class MemberInConnector implements MemberInterfaces {

    private final MemberService memberService;

    @Override
    public Page<MemberApiModel.MemberResponse> findAll(Pageable pageable) {
        Page<MemberModel> memberModelPage = memberService.findAll(pageable);
        return memberModelPage
                .map(this::toResponse);
    }

    @Override
    public Page<MemberApiModel.MemberResponse> findAllMemberSearch(String keyword, SearchType searchType, Pageable pageable) {
        Page<MemberModel> memberSearchResult = memberService.findAllMemberSearch(keyword, searchType, pageable);
        return memberSearchResult
                .map(this::toResponse);
    }

    @Override
    public MemberApiModel.MemberResponse findById(Long id) {
        MemberModel memberModel = memberService.findById(id);
        return toResponse(memberModel);
    }

    @Override
    public MemberApiModel.MemberResponse createMember(MemberApiModel.CreateRequest memberModel) {
        MemberModel createMember = toCreated(memberModel);
        return toResponse(memberService.createMember(createMember));
    }

    @Override
    public MemberApiModel.MemberResponse updateMember(Long id, MemberApiModel.UpdateRequest updateRequest) {
        MemberModel updateModel = toUpdate(updateRequest);
        return toResponse(memberService.updateMember(id,updateModel));
    }

    @Override
    public void deleteMember(Long id) {
        memberService.deleteMember(id);
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

    //model -> response
    public MemberApiModel.MemberResponse toResponse(MemberModel memberModel) {
        return MemberApiModel.MemberResponse
                .builder()
                .id(memberModel.getId())
                .userId(memberModel.getUserId())
                .password(memberModel.getPassword())
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
}
