package com.example.outbound.member;

import com.example.enumerate.member.Roles;
import com.example.enumerate.member.SearchType;
import com.example.model.member.MemberModel;
import com.example.rdb.member.Member;
import com.example.rdb.member.MemberRepository;
import com.example.rdb.member.MemberRepositoryImpl;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.stereotype.Component;
import java.time.LocalDateTime;

@Component
@RequiredArgsConstructor
public class MemberOutConnector {

    private final MemberRepository memberRepository;

    private final MemberRepositoryImpl customMemberRepository;

    private final BCryptPasswordEncoder bCryptPasswordEncoder;

    public Page<MemberModel> findAll(Pageable pageable) {
        Page<MemberModel> memberModelPage = memberRepository
                .findAll(pageable)
                .map(this::toEntity);

        if(memberModelPage.isEmpty()) {
            throw new RuntimeException("회원이 없습니다.");
        }

        return memberModelPage;
    }

    public Page<MemberModel> findAllMemberSearch(String keyword, SearchType searchType, Pageable pageable) {
        return customMemberRepository
                .searchAll(keyword,searchType,pageable)
                .map(this::toEntity);
    }

    public MemberModel findById(Long id) {
        Member memberEntity = memberRepository
                .findById(id)
                .orElseThrow(()-> new RuntimeException("회원이 없습니다."));

        return toEntity(memberEntity);
    }

    public MemberModel createMember(MemberModel memberModel) {

        Member memberEntity = Member
                .builder()
                .userId(memberModel.getUserId())
                .password(bCryptPasswordEncoder.encode(memberModel.getPassword()))
                .userEmail(memberModel.getUserEmail())
                .userPhone(memberModel.getUserPhone())
                .userName(memberModel.getUserName())
                .roles(Roles.ROLE_USER)
                .createdBy(memberModel.getUserId())
                .createdTime(LocalDateTime.now())
                .updatedBy(memberModel.getUserId())
                .updatedTime(LocalDateTime.now())
                .build();

        return toEntity(memberRepository.save(memberEntity));
    }

    public MemberModel updateMember(Long id, MemberModel memberModel) {
        Member memberEntity = memberRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("회원을 찾을 수 없습니다."));

        memberEntity.update(memberModel.getUserId(),
                memberModel.getUserEmail(),
                memberModel.getUserPhone());

        return toEntity(memberRepository.save(memberEntity));
    }

    public void deleteMember(Long id) {
        memberRepository.deleteById(id);
    }

    //Entity -> Model
    private MemberModel toEntity(Member memberEntity) {
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
