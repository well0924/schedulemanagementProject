package com.example.service.member;

import com.example.enumerate.member.SearchType;
import com.example.events.kafka.MemberSignUpKafkaEvent;
import com.example.events.outbox.OutboxEventService;
import com.example.model.member.MemberModel;
import com.example.outbound.member.MemberOutConnector;
import lombok.AllArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;


@Service
@AllArgsConstructor
public class MemberService {

    private final MemberOutConnector memberOutConnector;

    private final OutboxEventService outboxEventService;

    private static final Logger logger = LoggerFactory.getLogger(MemberService.class);

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

        MemberSignUpKafkaEvent memberSignUpKafkaEvent = MemberSignUpKafkaEvent
                .builder()
                .receiverId(createdResult.getId())
                .email(createdResult.getUserEmail())
                .username(createdResult.getUserId())
                .message("🎉 회원가입을 환영합니다!")
                .notificationType("SIGN_UP")
                .createdTime(LocalDateTime.now())
                .build();

        //outbox 패턴 적용하기.
        outboxEventService.saveEvent(
                memberSignUpKafkaEvent,
                "MEMBER",
                createdResult.getId().toString(),
                "SIGNED_UP_WELCOME"
        );

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