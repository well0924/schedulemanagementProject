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
        List<Tuple> results = queryFactory
                .select(
                        qSchedules.id,
                        qSchedules.contents,
                        qSchedules.scheduleMonth,
                        qSchedules.scheduleDay,
                        qSchedules.isDeletedScheduled,
                        qSchedules.userId,
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
                .leftJoin(qAttach).on(qAttach.scheduledId.eq(qSchedules.id))
                .where(qSchedules.isDeletedScheduled.eq(false))
                .fetch();

        return  results.stream()
                .collect(Collectors.groupingBy(tuple -> tuple.get(qSchedules.id)))
                .values()
                .stream()
                .map(groupedTuples -> {
                    // 이 ID에 해당하는 모든 튜플

                    Tuple first = groupedTuples.get(0); // 일정 공통 정보는 첫 번째에서 뽑음

                    return new SchedulesModel(
                            first.get(qSchedules.id),
                            first.get(qSchedules.contents),
                            first.get(qSchedules.scheduleMonth),
                            first.get(qSchedules.scheduleDay),
                            first.get(qSchedules.startTime),
                            first.get(qSchedules.endTime),
                            first.get(qSchedules.userId),
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
                            groupedTuples.stream()
                                    .map(t -> t.get(qAttach.storedFileName))
                                    .filter(Objects::nonNull)
                                    .distinct()
                                    .collect(Collectors.toList()),
                            groupedTuples.stream()
                                    .map(t -> t.get(qAttach.id))
                                    .filter(Objects::nonNull)
                                    .distinct()
                                    .collect(Collectors.toList())
                    );
                })
                .collect(Collectors.toList());

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
                        qSchedules.userId,
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
                .where(qSchedules.id.eq(scheduleId),
                        qAttach.isDeletedAttach.eq(false))
                .fetch();

        if (results.isEmpty()) {
            return null; // 결과가 없는 경우 `null` 반환
        }

        SchedulesModel schedule = new SchedulesModel(
                results.get(0).get(qSchedules.id),
                results.get(0).get(qSchedules.contents),
                results.get(0).get(qSchedules.scheduleMonth),
                results.get(0).get(qSchedules.scheduleDay),
                results.get(0).get(qSchedules.startTime),
                results.get(0).get(qSchedules.endTime),
                results.get(0).get(qSchedules.userId),
                results.get(0).get(qSchedules.categoryId),
                results.get(0).get(qSchedules.progress_status),
                results.get(0).get(qSchedules.repeatType),
                results.get(0).get(qSchedules.repeatCount),
                results.get(0).get(qSchedules.repeatInterval),
                results.get(0).get(qSchedules.repeatGroupId),
                results.get(0).get(qSchedules.scheduleType),
                results.get(0).get(qSchedules.createdBy),
                results.get(0).get(qSchedules.updatedBy),
                results.get(0).get(qSchedules.createdTime),
                results.get(0).get(qSchedules.updatedTime),
                results.stream()
                        .map(tuple -> tuple.get(qAttach.storedFileName))
                        .filter(Objects::nonNull)
                        .distinct()
                        .collect(Collectors.toList()),
                results.stream()
                        .map(tuple -> tuple.get(qAttach.id))
                        .filter(Objects::nonNull)
                        .distinct()
                        .collect(Collectors.toList())
        );

        return schedule;
    }

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
                        qSchedules.userId,
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
                .leftJoin(qAttach).on(qSchedules.id.eq(qAttach.scheduledId))
                .join(qMember).on(qSchedules.userId.eq(qMember.id))
                .where(qMember.userId.eq(userId))
                .offset(pageable.getOffset())
                .limit(pageable.getPageSize())
                .fetch();

        List<SchedulesModel> scheduleList = new ArrayList<>();

        for (Tuple tuple : results) {
            Long scheduleId = tuple.get(qSchedules.id);

            SchedulesModel schedule = scheduleList
                    .stream()
                    .filter(s -> s.getId().equals(scheduleId))
                    .findFirst()
                    .orElse(null);

            if (schedule == null) {
                schedule = new SchedulesModel(
                        tuple.get(qSchedules.id),
                        tuple.get(qSchedules.contents),
                        tuple.get(qSchedules.scheduleMonth),
                        tuple.get(qSchedules.scheduleDay),
                        tuple.get(qSchedules.startTime),
                        tuple.get(qSchedules.endTime),
                        tuple.get(qSchedules.userId),
                        tuple.get(qSchedules.categoryId),
                        tuple.get(qSchedules.progress_status.stringValue()),
                        tuple.get(qSchedules.repeatType.stringValue()),
                        tuple.get(qSchedules.repeatCount),
                        tuple.get(qSchedules.repeatInterval),
                        tuple.get(qSchedules.repeatGroupId),
                        tuple.get(qSchedules.scheduleType),
                        tuple.get(qSchedules.createdBy),
                        tuple.get(qSchedules.updatedBy),
                        tuple.get(qSchedules.createdTime),
                        tuple.get(qSchedules.updatedTime),
                        new ArrayList<>(),
                        new ArrayList<>()
                );
                scheduleList.add(schedule);
            }

            String fileName = tuple.get(qAttach.storedFileName);
            Long fileId = tuple.get(qAttach.id);

            if (fileName != null && fileId != null) {
                schedule.getAttachThumbNailImagePath().add(fileName);
                schedule.getAttachIds().add(fileId);
            }
        }

        Long total = Optional.ofNullable(
                queryFactory
                        .select(qSchedules.count())
                        .from(qSchedules)
                        .join(qMember).on(qSchedules.userId.eq(qMember.id))
                        .where(qMember.userId.eq(userId))
                        .fetchOne()
        ).orElse(0L);

        return new PageImpl<>(scheduleList,pageable,total);
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
                        qSchedules.userId,
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
                .leftJoin(qAttach)
                .on(qSchedules.id.eq(qAttach.scheduledId))
                .join(qCategory)
                .on(qSchedules.categoryId.eq(qCategory.id))
                .where(qCategory.name.eq(categoryName))
                .offset(pageable.getOffset())
                .limit(pageable.getPageSize())
                .fetch();

        List<SchedulesModel> scheduleList = new ArrayList<>();

        for (Tuple tuple : results) {
            Long scheduleId = tuple.get(qSchedules.id);

            SchedulesModel schedule = scheduleList
                    .stream()
                    .filter(s -> s.getId().equals(scheduleId))
                    .findFirst()
                    .orElse(null);

            if (schedule == null) {
                schedule = new SchedulesModel(
                        tuple.get(qSchedules.id),
                        tuple.get(qSchedules.contents),
                        tuple.get(qSchedules.scheduleMonth),
                        tuple.get(qSchedules.scheduleDay),
                        tuple.get(qSchedules.startTime),
                        tuple.get(qSchedules.endTime),
                        tuple.get(qSchedules.userId),
                        tuple.get(qSchedules.categoryId),
                        tuple.get(qSchedules.progress_status.stringValue()),
                        tuple.get(qSchedules.repeatType.stringValue()),
                        tuple.get(qSchedules.repeatCount),
                        tuple.get(qSchedules.repeatInterval),
                        tuple.get(qSchedules.repeatGroupId),
                        tuple.get(qSchedules.scheduleType),
                        tuple.get(qSchedules.createdBy),
                        tuple.get(qSchedules.updatedBy),
                        tuple.get(qSchedules.createdTime),
                        tuple.get(qSchedules.updatedTime),
                        new ArrayList<>(),
                        new ArrayList<>()
                );
                scheduleList.add(schedule);
            }

            String fileName = tuple.get(qAttach.storedFileName);
            Long fileId = tuple.get(qAttach.id);

            if (fileName != null && fileId != null) {
                schedule.getAttachThumbNailImagePath().add(fileName);
                schedule.getAttachIds().add(fileId);
            }
        }

        Long total = Optional.ofNullable(
                queryFactory
                        .select(qSchedules.count())
                        .from(qSchedules)
                        .join(qCategory).on(qSchedules.categoryId.eq(qCategory.id))
                        .where(qCategory.name.eq(categoryName))
                        .fetchOne()
        ).orElse(0L);

        return new PageImpl<>(scheduleList,pageable,total);
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
                        qSchedules.userId,
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
                .join(qMember).on(qSchedules.userId.eq(qMember.id))
                .where(qMember.userId.eq(userId)
                        .and(qSchedules.progress_status.stringValue().eq(progressStatus)))
                .offset(pageable.getOffset())
                .limit(pageable.getPageSize())
                .fetch();

        List<SchedulesModel> scheduleList = results.stream()
                .collect(Collectors.groupingBy(tuple -> tuple.get(qSchedules.id)))
                .entrySet()
                .stream()
                .map(entry -> {
                    List<Tuple> scheduleTuples = entry.getValue();

                    return new SchedulesModel(
                            scheduleTuples.get(0).get(qSchedules.id),
                            scheduleTuples.get(0).get(qSchedules.contents),
                            scheduleTuples.get(0).get(qSchedules.scheduleMonth),
                            scheduleTuples.get(0).get(qSchedules.scheduleDay),
                            scheduleTuples.get(0).get(qSchedules.startTime),
                            scheduleTuples.get(0).get(qSchedules.endTime),
                            scheduleTuples.get(0).get(qSchedules.userId),
                            scheduleTuples.get(0).get(qSchedules.categoryId),
                            scheduleTuples.get(0).get(qSchedules.progress_status.stringValue()),
                            scheduleTuples.get(0).get(qSchedules.repeatType.stringValue()),
                            scheduleTuples.get(0).get(qSchedules.repeatCount),
                            scheduleTuples.get(0).get(qSchedules.repeatInterval),
                            scheduleTuples.get(0).get(qSchedules.repeatGroupId),
                            scheduleTuples.get(0).get(qSchedules.scheduleType),
                            scheduleTuples.get(0).get(qSchedules.createdBy),
                            scheduleTuples.get(0).get(qSchedules.updatedBy),
                            scheduleTuples.get(0).get(qSchedules.createdTime),
                            scheduleTuples.get(0).get(qSchedules.updatedTime),
                            Optional.of(scheduleTuples.stream()
                                            .map(t -> t.get(qAttach.storedFileName))
                                            .filter(Objects::nonNull)
                                            .distinct()
                                            .collect(Collectors.toList()))
                                    .orElse(Collections.emptyList()),
                            Optional.of(scheduleTuples.stream()
                                            .map(t -> t.get(qAttach.id))
                                            .filter(Objects::nonNull)
                                            .distinct()
                                            .collect(Collectors.toList()))
                                    .orElse(Collections.emptyList())
                    );
                })
                .collect(Collectors.toList());

        Long total = Optional.ofNullable(
                queryFactory
                        .select(qSchedules.count())
                        .from(qSchedules)
                        .join(qMember).on(qSchedules.userId.eq(qMember.id))
                        .where(qMember.userId.eq(userId)
                                .and(qSchedules.progress_status.stringValue().eq(progressStatus)))
                        .fetchOne()
        ).orElse(0L);

        return new PageImpl<>(scheduleList,pageable,total);
    }
}

