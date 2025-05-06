package com.example.interfaces.member;

import com.example.apimodel.member.MemberApiModel;
import com.example.enumerate.member.SearchType;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

public interface MemberInterfaces {

    Page<MemberApiModel.MemberResponse> findAll(Pageable pageable);
    Page<MemberApiModel.MemberResponse> findAllMemberSearch(String keyword, SearchType searchType, Pageable pageable);
    MemberApiModel.MemberResponse findById(Long id);
    MemberApiModel.MemberResponse createMember(MemberApiModel.CreateRequest memberModel);
    MemberApiModel.MemberResponse updateMember(Long id, MemberApiModel.UpdateRequest memberModel);
    void deleteMember(Long id);
}
