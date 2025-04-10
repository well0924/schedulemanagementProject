package com.example.service.member;

import com.example.enumerate.member.SearchType;
import com.example.events.MemberSignUpEvent;
import com.example.model.member.MemberModel;
import com.example.outconnector.member.MemberOutConnector;
import lombok.AllArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;


@Service
@AllArgsConstructor
public class MemberService {

    private final MemberOutConnector memberOutConnector;

    private static final Logger logger = LoggerFactory.getLogger(MemberService.class);

    private final ApplicationEventPublisher eventPublisher; // 이벤트 퍼블리셔 추가


    public Page<MemberModel> findAll(Pageable pageable) {
        Page<MemberModel> memberModelPage = memberOutConnector.findAll(pageable);
        logger.debug("memberList::" + memberModelPage);
        return memberModelPage;
    }

    public Page<MemberModel> findAllMemberSearch(String keyword, SearchType searchType, Pageable pageable) {
        Page<MemberModel> memberModelsSearchResult = memberOutConnector.findAllMemberSearch(keyword, searchType, pageable);
        logger.debug("memberSearchResult::"+memberModelsSearchResult);
        return memberModelsSearchResult;
    }

    public MemberModel findById(Long id) {
        MemberModel memberDetailResult = memberOutConnector.findById(id);
        logger.debug("memberDetailResult::"+memberDetailResult);
        return memberDetailResult;
    }

    public MemberModel createMember(MemberModel memberModel) {
        memberModel.isValidEmail();
        memberModel.isValidPhoneNumber();
        memberModel.isValidUserId();
        MemberModel createdResult = memberOutConnector.createMember(memberModel);
        logger.debug("createdResult::"+createdResult);
        MemberSignUpEvent memberSignUpEvent = new MemberSignUpEvent(
                createdResult.getId(),
                createdResult.getUserEmail(),
                createdResult.getUserId());
        logger.debug("eventResult::"+memberSignUpEvent.getMemberId());
        eventPublisher.publishEvent(memberSignUpEvent);
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