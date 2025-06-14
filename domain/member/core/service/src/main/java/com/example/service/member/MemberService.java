package com.example.service.member;

import com.example.enumerate.member.SearchType;
import com.example.events.spring.MemberSignUpEvent;
import com.example.model.member.MemberModel;
import com.example.outbound.member.MemberOutConnector;
import lombok.AllArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;


@Service
@Transactional
@AllArgsConstructor
public class MemberService {

    private final MemberOutConnector memberOutConnector;

    private final ApplicationEventPublisher applicationEventPublisher;

    private static final Logger logger = LoggerFactory.getLogger(MemberService.class);

    @Transactional(readOnly = true)
    public Page<MemberModel> findAll(Pageable pageable) {
        Page<MemberModel> memberModelPage = memberOutConnector.findAll(pageable);
        logger.debug("memberList::" + memberModelPage);
        return memberModelPage;
    }

    @Transactional(readOnly = true)
    public Page<MemberModel> findAllMemberSearch(String keyword, SearchType searchType, Pageable pageable) {
        Page<MemberModel> memberModelsSearchResult = memberOutConnector.findAllMemberSearch(keyword, searchType, pageable);
        logger.debug("memberSearchResult::"+memberModelsSearchResult);
        return memberModelsSearchResult;
    }

    @Transactional(readOnly = true)
    public MemberModel findById(Long id) {
        MemberModel memberDetailResult = memberOutConnector.findById(id);
        logger.debug("memberDetailResult::"+memberDetailResult);
        return memberDetailResult;
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public MemberModel createMember(MemberModel memberModel) {
        memberModel.isValidEmail();
        memberModel.isValidPhoneNumber();
        memberModel.isValidUserId();
        MemberModel createdResult = memberOutConnector.createMember(memberModel);
        logger.debug("createdResult::"+createdResult);

        applicationEventPublisher.publishEvent(MemberSignUpEvent
                .builder()
                        .memberId(createdResult.getId())
                        .email(createdResult.getUserEmail())
                        .username(createdResult.getUserId())
                .build());
        logger.info("event???");
        return createdResult;
    }

    public MemberModel updateMember(Long id, MemberModel memberModel) {
        MemberModel updatedResult = memberOutConnector.updateMember(id, memberModel);
        logger.debug("createdResult::"+updatedResult);
        return updatedResult;
    }

    public void deleteMember(Long id) {
        logger.debug("member Deleted");
        memberOutConnector.deleteMember(id);
    }
}