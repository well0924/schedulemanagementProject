package com.example.rdbrepository.custom;

import com.example.model.schedules.SchedulesModel;
import com.example.rdb.member.QMember;
import com.example.rdb.QAttach;
import com.example.rdb.QCategory;
import com.example.rdbrepository.QSchedules;
import com.querydsl.core.Tuple;
import com.querydsl.jpa.JPAExpressions;
import com.querydsl.jpa.impl.JPAQueryFactory;
import jakarta.persistence.EntityManager;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Repository;

import java.util.*;
import java.util.stream.Collectors;

@Slf4j
@Repository
public class ScheduleRepositoryCustomImpl implements ScheduleRepositoryCustom {

    private final JPAQueryFactory queryFactory;

    private final QSchedules qSchedules;
    private final QAttach qAttach;
    private final QMember qMember;
    private final QCategory qCategory;

    public ScheduleRepositoryCustomImpl(EntityManager entityManager) {
        this.queryFactory = new JPAQueryFactory(entityManager);
        this.qSchedules = QSchedules.schedules;
        this.qAttach = QAttach.attach;
        this.qMember = QMember.member;
        this.qCategory = QCategory.category;
    }
    
    // 메인 캘린더에 사용되는 일정 전체 목록
    @Override
    public List<SchedulesModel> findAllSchedule() {
        
        List<Tuple> results = queryFactory
                .select(
                        qSchedules.id,
                        qSchedules.contents,
                        qSchedules.scheduleMonth,
                        qSchedules.scheduleDay,
                        qSchedules.isDeletedScheduled,
                        qSchedules.memberId,
                        qSchedules.categoryId,
                        qSchedules.progress_status,
                        qSchedules.repeatType,
                        qSchedules.repeatCount,
                        qSchedules.repeatInterval,
                        qSchedules.repeatGroupId,
                        qSchedules.startTime,
                        qSchedules.endTime,
                        qAttach.id,
                        qAttach.storedFileName
                )
                .from(qSchedules)
                .leftJoin(qAttach)
                .on(qAttach.scheduledId.eq(qSchedules.id)
                        .and(qAttach.isDeletedAttach.eq(false)))
                .where(qSchedules.isDeletedScheduled.eq(false))
                .fetch();

        return  mapTuples(results);

    }

    // 일정 단일 조회
    @Override
    public SchedulesModel findByScheduleId(Long scheduleId) {

        List<Tuple> results = queryFactory
                .select(
                        qSchedules.id,
                        qSchedules.contents,
                        qSchedules.scheduleMonth,
                        qSchedules.scheduleDay,
                        qSchedules.startTime,
                        qSchedules.endTime,
                        qSchedules.progress_status,
                        qSchedules.memberId,
                        qSchedules.categoryId,
                        qSchedules.repeatType,
                        qSchedules.repeatCount,
                        qSchedules.repeatInterval,
                        qSchedules.repeatGroupId,
                        qAttach.id,
                        qAttach.storedFileName
                )
                .from(qSchedules)
                .leftJoin(qAttach).on(
                        qSchedules.id.eq(qAttach.scheduledId)
                        .and(qAttach.isDeletedAttach.eq(false)))
                .where(qSchedules.id.eq(scheduleId))
                .fetch();

        // 결과가 없는 경우 `null` 반환
        if (results.isEmpty()) {
            return null;
        }

        return  mapTuples(results).get(0);
    }
    
    // 회원 아이디로 일정 목록 조회
    @Override
    public Page<SchedulesModel> findAllByUserId(String userId, Pageable pageable) {

        List<Tuple> results = queryFactory
                .select(
                        qSchedules.id,
                        qSchedules.contents,
                        qSchedules.startTime,
                        qSchedules.endTime,
                        qSchedules.scheduleMonth,
                        qSchedules.scheduleDay,
                        qSchedules.memberId,
                        qSchedules.categoryId,
                        qSchedules.progress_status,
                        qSchedules.repeatType,
                        qSchedules.repeatCount,
                        qSchedules.repeatGroupId,
                        qSchedules.repeatInterval,
                        qSchedules.createdTime,
                        qSchedules.createdBy,
                        qSchedules.updatedBy,
                        qSchedules.updatedTime,
                        qAttach.storedFileName,
                        qAttach.id
                )
                .from(qSchedules)
                .leftJoin(qAttach)
                .on(
                        qSchedules.id.eq(qAttach.scheduledId)
                )
                .where(qSchedules.id.in(
                        JPAExpressions
                                .select(qSchedules.id)
                                .from(qSchedules)
                                .join(qMember).on(qSchedules.memberId.eq(qMember.id))
                                .where(
                                        qMember.userId.eq(userId),
                                        qSchedules.isDeletedScheduled.isFalse()
                                )
                                .orderBy(qSchedules.id.desc())
                                .limit(pageable.getPageSize())
                                .offset(pageable.getOffset())
                ))
                .fetch();

        Long total = countByUserId(userId);

        return new PageImpl<>(mapTuples(results),pageable,total);
    }

    @Override
    public Page<SchedulesModel> findAllByCategoryName(String categoryName, Pageable pageable) {

        List<Tuple> results = queryFactory
                .select(
                        qSchedules.id,
                        qSchedules.contents,
                        qSchedules.scheduleMonth,
                        qSchedules.scheduleDay,
                        qSchedules.startTime,
                        qSchedules.endTime,
                        qSchedules.memberId,
                        qSchedules.categoryId,
                        qSchedules.progress_status.stringValue(),
                        qSchedules.repeatType,
                        qSchedules.repeatCount,
                        qSchedules.repeatInterval,
                        qSchedules.repeatGroupId,
                        qSchedules.createdTime,
                        qSchedules.createdBy,
                        qSchedules.updatedBy,
                        qSchedules.updatedTime,
                        qAttach.storedFileName,
                        qAttach.id
                )
                .from(qSchedules)
                .leftJoin(qAttach).on(qSchedules.id.eq(qAttach.scheduledId))
                .where(qSchedules.id.in(queryFactory
                        .select(qSchedules.id)
                        .from(qSchedules)
                        .join(qCategory).on(qSchedules.categoryId.eq(qCategory.id))
                        .where(
                                qCategory.name.eq(categoryName),
                                qSchedules.isDeletedScheduled.eq(false)
                        )
                        .orderBy(qSchedules.id.desc())
                        .offset(pageable.getOffset())
                        .limit(pageable.getPageSize())))
                .fetch();

        Long total = countByCategory(categoryName);

        return new PageImpl<>(mapTuples(results),pageable,total);
    }

    @Override
    public Page<SchedulesModel> findAllByProgressStatus(String userId, String progressStatus, Pageable pageable) {

        List<Tuple> results = queryFactory
                .select(
                        qSchedules.id,
                        qSchedules.contents,
                        qSchedules.scheduleMonth,
                        qSchedules.scheduleDay,
                        qSchedules.startTime,
                        qSchedules.endTime,
                        qSchedules.memberId,
                        qSchedules.categoryId,
                        qSchedules.progress_status.stringValue(),
                        qSchedules.repeatType,
                        qSchedules.repeatCount,
                        qSchedules.repeatInterval,
                        qSchedules.repeatGroupId,
                        qSchedules.createdTime,
                        qSchedules.createdBy,
                        qSchedules.updatedBy,
                        qSchedules.updatedTime,
                        qAttach.storedFileName,
                        qAttach.id
                )
                .from(qSchedules)
                .leftJoin(qAttach).on(
                        qSchedules.id.eq(qAttach.scheduledId)
                                .and(qAttach.isDeletedAttach.eq(false))
                )
                .where(qSchedules.id.in(queryFactory
                        .select(qSchedules.id)
                        .from(qSchedules)
                        .join(qMember).on(qSchedules.memberId.eq(qMember.id))
                        .where(
                                qMember.userId.eq(userId),
                                qSchedules.isDeletedScheduled.eq(false),
                                qSchedules.progress_status.stringValue().eq(progressStatus)
                        )
                        .orderBy(qSchedules.id.desc())
                        .offset(pageable.getOffset())
                        .limit(pageable.getPageSize())))
                .fetch();

        Long total = countByPROGRESS_STATUS(progressStatus,userId);

        return new PageImpl<>(mapTuples(results),pageable,total);
    }

    //회원 아이디별 일정 목록 count
    private Long countByUserId(String userId){
        return Optional.ofNullable(
                queryFactory
                        .select(qSchedules.count())
                        .from(qSchedules)
                        .join(qMember).on(qSchedules.memberId.eq(qMember.id))
                        .where(
                                qMember.userId.eq(userId),
                                qSchedules.isDeletedScheduled.eq(false)
                        )
                        .fetchOne()
        ).orElse(0L);
    }

    //카테고리별 일정목록 갯수
    private Long countByCategory(String categoryName) {
        return Optional.ofNullable(
                queryFactory
                        .select(qSchedules.count())
                        .from(qSchedules)
                        .join(qCategory).on(qSchedules.categoryId.eq(qCategory.id))
                        .where(
                                qCategory.name.eq(categoryName),
                                qSchedules.isDeletedScheduled.eq(false)
                        )
                        .fetchOne()
        ).orElse(0L);
    }

    // 회원 일정상태별 일정목록 갯수
    private Long countByPROGRESS_STATUS(String progressStatus,String userId){
        return Optional.ofNullable(
                queryFactory
                        .select(qSchedules.count())
                        .from(qSchedules)
                        .join(qMember).on(qSchedules.memberId.eq(qMember.id))
                        .where(qMember.userId.eq(userId)
                                .and(qSchedules.progress_status.stringValue().eq(progressStatus)))
                        .fetchOne()
        ).orElse(0L);
    }

    // 맵핑 로직
    private List<SchedulesModel> mapTuples(List<Tuple> results) {
        return results.stream()
                .collect(Collectors.groupingBy(t -> t.get(qSchedules.id)))
                .values().stream()
                .map(group -> {
                    Tuple first = group.get(0);
                    return new SchedulesModel(
                            first.get(qSchedules.id),
                            first.get(qSchedules.contents),
                            first.get(qSchedules.scheduleDay),
                            first.get(qSchedules.scheduleMonth),
                            first.get(qSchedules.startTime),
                            first.get(qSchedules.endTime),
                            first.get(qSchedules.memberId),
                            first.get(qSchedules.categoryId),
                            first.get(qSchedules.progress_status),
                            first.get(qSchedules.repeatType),
                            first.get(qSchedules.repeatCount),
                            first.get(qSchedules.repeatInterval),
                            first.get(qSchedules.repeatGroupId),
                            first.get(qSchedules.scheduleType),
                            first.get(qSchedules.createdBy),
                            first.get(qSchedules.updatedBy),
                            first.get(qSchedules.createdTime),
                            first.get(qSchedules.updatedTime),
                            group.stream().map(t -> t.get(qAttach.storedFileName)).filter(Objects::nonNull).distinct().toList(),
                            group.stream().map(t -> t.get(qAttach.id)).filter(Objects::nonNull).distinct().toList()
                    );
                }).toList();
    }
}

