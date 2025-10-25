package com.example.inbound.member;

import com.example.apimodel.member.MemberApiModel;
import com.example.enumerate.member.SearchType;
import com.example.interfaces.member.MemberInterfaces;
import com.example.member.mapper.MemberModelMapper;
import com.example.model.member.MemberModel;
import com.example.service.member.MemberService;
import lombok.AllArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Component;

@Component
@AllArgsConstructor
public class MemberInConnector implements MemberInterfaces {

    private final MemberService memberService;

    private final MemberModelMapper memberModelMapper;

    @Override
    public Page<MemberApiModel.MemberResponse> findAll(Pageable pageable) {
        Page<MemberModel> memberModelPage = memberService.findAll(pageable);
        return memberModelPage
                .map(memberModelMapper::toResponse);
    }

    @Override
    public Page<MemberApiModel.MemberResponse> findAllMemberSearch(String keyword, SearchType searchType, Pageable pageable) {
        Page<MemberModel> memberSearchResult = memberService.findAllMemberSearch(keyword, searchType, pageable);
        return memberSearchResult
                .map(memberModelMapper::toResponse);
    }

    @Override
    public MemberApiModel.MemberResponse findById(Long id) {
        MemberModel memberModel = memberService.findById(id);
        return memberModelMapper.toResponse(memberModel);
    }

    @Override
    public MemberApiModel.MemberResponse createMember(MemberApiModel.CreateRequest memberModel) {
        MemberModel createMember = memberModelMapper.toCreated(memberModel);
        return memberModelMapper.toResponse(memberService.createMember(createMember));
    }

    @Override
    public MemberApiModel.MemberResponse updateMember(Long id, MemberApiModel.UpdateRequest updateRequest) {
        MemberModel updateModel = memberModelMapper.toUpdate(updateRequest);
        return memberModelMapper.toResponse(memberService.updateMember(id,updateModel));
    }

    @Override
    public void deleteMember(Long id) {
        memberService.deleteMember(id);
    }

}
