package com.example.outbound.member;

import com.example.enumerate.member.Roles;
import com.example.enumerate.member.SearchType;
import com.example.exception.dto.MemberErrorCode;
import com.example.exception.exception.MemberCustomException;
import com.example.interfaces.member.MemberRepositoryPort;
import com.example.member.mapper.MemberEntityMapper;
import com.example.model.member.MemberModel;
import com.example.rdb.member.Member;
import com.example.rdb.member.MemberRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.cache.CacheManager;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.stereotype.Component;
import java.time.LocalDateTime;
import java.util.Optional;

@Component
@RequiredArgsConstructor
public class MemberOutConnector implements MemberRepositoryPort {

    private final MemberRepository memberRepository;

    private final BCryptPasswordEncoder bCryptPasswordEncoder;

    private final MemberEntityMapper entityMapper;

    private final CacheManager cacheManager;

    public Page<MemberModel> findAll(Pageable pageable) {
        Page<MemberModel> memberModelPage = memberRepository
                .findAll(pageable)
                .map(entityMapper::toEntity);

        if(memberModelPage.isEmpty()) {
            throw new MemberCustomException(MemberErrorCode.NOT_USER);
        }

        return memberModelPage;
    }

    public Page<MemberModel> findAllMemberSearch(String keyword, SearchType searchType, Pageable pageable) {
        return memberRepository
                .searchAll(keyword,searchType,pageable)
                .map(entityMapper::toEntity);
    }

    public MemberModel findById(Long id) {
        Member memberEntity = memberRepository
                .findById(id)
                .orElseThrow(()-> new MemberCustomException(MemberErrorCode.NOT_USER));

        return entityMapper.toEntity(memberEntity);
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

        return entityMapper.toEntity(memberRepository.save(memberEntity));
    }

    public MemberModel updateMember(Long id, MemberModel memberModel) {
        Member memberEntity = memberRepository.findById(id)
                .orElseThrow(() -> new MemberCustomException(MemberErrorCode.NOT_USER));

        // 캐시 키(username)로 쓰이는 값이라, 변경 전(구) 아이디로 evict해야 함
        String oldUserId = memberEntity.getUserId();

        memberEntity.update(memberModel.getUserId(),
                memberModel.getUserEmail(),
                memberModel.getUserPhone());

        MemberModel result = entityMapper.toEntity(memberRepository.save(memberEntity));
        evictUserCache(oldUserId);
        return result;
    }

    public void deleteMember(Long id) {
        memberRepository.findById(id).ifPresent(member -> evictUserCache(member.getUserId()));
        memberRepository.deleteById(id);
    }

    // AuthOutConnector.loadUserByUsername()의 "user" 캐시 무효화
    private void evictUserCache(String userId) {
        Optional.ofNullable(cacheManager.getCache("user")).ifPresent(cache -> cache.evict(userId));
    }

    public boolean existsById(Long id) {
        return memberRepository.existsById(id);
    }

    @Override
    public boolean existsByUserId(String userId) {
        return memberRepository.existsByUserId(userId);
    }

}
