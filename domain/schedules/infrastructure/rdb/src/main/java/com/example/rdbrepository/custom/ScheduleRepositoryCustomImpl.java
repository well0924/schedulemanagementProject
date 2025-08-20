package com.example.rdbrepository.custom;

import com.example.model.schedules.SchedulesModel;
import com.example.rdb.member.QMember;
import com.example.rdb.QAttach;
import com.example.rdb.QCategory;
import com.example.rdbrepository.QSchedules;
import com.querydsl.core.Tuple;
import com.querydsl.jpa.impl.JPAQueryFactory;
import jakarta.persistence.EntityManager;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Repository;

import java.time.LocalDate;
import java.util.*;
import java.util.stream.Collectors;

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

    @Override
    public List<SchedulesModel> findAllSchedule() {
        LocalDate from = LocalDate.now().withDayOfMonth(1);
        LocalDate toEx = from.plusMonths(1);

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
                .on(qAttach.scheduledId.eq(qSchedules.id).and(qAttach.isDeletedAttach.eq(false)))
                .where(qSchedules.isDeletedScheduled.eq(false))
                .fetch();

        return  mapTuples(results);

    }

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

        if (results.isEmpty()) {
            return null; // 결과가 없는 경우 `null` 반환
        }
        List<SchedulesModel> list = mapTuples(results);

        return list.get(0);
    }

    @Override
    public Page<SchedulesModel> findAllByUserId(String userId, Pageable pageable) {
        // 1) ID만 정확히 페이징
        List<Long> pageIds = queryFactory
                .select(qSchedules.id)
                .from(qSchedules)
                .join(qMember).on(qSchedules.memberId.eq(qMember.id))
                .where(
                        qMember.userId.eq(userId),
                        qSchedules.isDeletedScheduled.eq(false)
                )
                .orderBy(qSchedules.id.desc())
                .offset(pageable.getOffset())
                .limit(pageable.getPageSize())
                .fetch();

        if (pageIds.isEmpty()) {
            Long total0 = Optional.ofNullable(
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
            return new PageImpl<>(Collections.emptyList(), pageable, total0);
        }

        // 2) IN 조회 + LEFT JOIN(첨부)
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
                .leftJoin(qAttach).on(
                        qSchedules.id.eq(qAttach.scheduledId)
                )
                .where(qSchedules.id.in(pageIds),qAttach.isDeletedAttach.eq(false))
                .fetch();

        List<SchedulesModel> scheduleList = sortByIdOrder(mapTuples(results), pageIds);

        Long total = Optional.ofNullable(
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

        return new PageImpl<>(scheduleList,pageable,total);
    }

    @Override
    public Page<SchedulesModel> findAllByCategoryName(String categoryName, Pageable pageable) {
        // 1) ID 페이징
        List<Long> pageIds = queryFactory
                .select(qSchedules.id)
                .from(qSchedules)
                .join(qCategory).on(qSchedules.categoryId.eq(qCategory.id))
                .where(
                        qCategory.name.eq(categoryName),
                        qSchedules.isDeletedScheduled.eq(false)
                )
                .orderBy(qSchedules.id.desc())
                .offset(pageable.getOffset())
                .limit(pageable.getPageSize())
                .fetch();

        if (pageIds.isEmpty()) {
            Long total0 = Optional.ofNullable(
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
            return new PageImpl<>(Collections.emptyList(), pageable, total0);
        }

        // 2) 본 조회
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
                .where(qSchedules.id.in(pageIds))
                .fetch();

        List<SchedulesModel> scheduleList = sortByIdOrder(mapTuples(results), pageIds);

        Long total = Optional.ofNullable(
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

        return new PageImpl<>(scheduleList,pageable,total);
    }

    @Override
    public Page<SchedulesModel> findAllByProgressStatus(String userId, String progressStatus, Pageable pageable) {
        // 1) ID 페이징
        List<Long> pageIds = queryFactory
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
                .limit(pageable.getPageSize())
                .fetch();

        if (pageIds.isEmpty()) {
            Long total0 = Optional.ofNullable(
                    queryFactory
                            .select(qSchedules.count())
                            .from(qSchedules)
                            .join(qMember).on(qSchedules.memberId.eq(qMember.id))
                            .where(
                                    qMember.userId.eq(userId),
                                    qSchedules.isDeletedScheduled.eq(false),
                                    qSchedules.progress_status.stringValue().eq(progressStatus)
                            )
                            .fetchOne()
            ).orElse(0L);
            return new PageImpl<>(Collections.emptyList(), pageable, total0);
        }

        // 2) 본 조회
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
                .where(qSchedules.id.in(pageIds))
                .fetch();

        List<SchedulesModel> scheduleList = sortByIdOrder(mapTuples(results), pageIds);

        Long total = Optional.ofNullable(
                queryFactory
                        .select(qSchedules.count())
                        .from(qSchedules)
                        .join(qMember).on(qSchedules.memberId.eq(qMember.id))
                        .where(qMember.userId.eq(userId)
                                .and(qSchedules.progress_status.stringValue().eq(progressStatus)))
                        .fetchOne()
        ).orElse(0L);

        return new PageImpl<>(scheduleList,pageable,total);
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

    private List<SchedulesModel> sortByIdOrder(List<SchedulesModel> list, List<Long> orderIds) {
        Map<Long, Integer> order = new HashMap<>();
        for (int i = 0; i < orderIds.size(); i++) order.put(orderIds.get(i), i);
        list.sort(Comparator.comparingInt(m -> order.getOrDefault(m.getId(), Integer.MAX_VALUE)));
        return list;
    }
}

