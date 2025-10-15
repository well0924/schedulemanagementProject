package com.example.interfaces.member;

import com.example.enumerate.member.SearchType;
import com.example.model.member.MemberModel;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

public interface MemberRepositoryPort {
    Page<MemberModel> findAll(Pageable pageable);
    Page<MemberModel> findAllMemberSearch(String keyword, SearchType searchType, Pageable pageable);
    MemberModel findById(Long id);
    MemberModel createMember(MemberModel memberModel);
    MemberModel updateMember(Long id, MemberModel memberModel);
    void deleteMember(Long id);
}
