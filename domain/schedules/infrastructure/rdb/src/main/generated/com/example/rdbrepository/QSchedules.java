package com.example.rdbrepository;

import static com.querydsl.core.types.PathMetadataFactory.*;

import com.querydsl.core.types.dsl.*;

import com.querydsl.core.types.PathMetadata;
import javax.annotation.processing.Generated;
import com.querydsl.core.types.Path;


/**
 * QSchedules is a Querydsl query type for Schedules
 */
@Generated("com.querydsl.codegen.DefaultEntitySerializer")
public class QSchedules extends EntityPathBase<Schedules> {

    private static final long serialVersionUID = 526807671L;

    public static final QSchedules schedules = new QSchedules("schedules");

    public final com.example.jpa.config.base.QBaseEntity _super = new com.example.jpa.config.base.QBaseEntity(this);

    public final NumberPath<Long> categoryId = createNumber("categoryId", Long.class);

    public final StringPath contents = createString("contents");

    //inherited
    public final StringPath createdBy = _super.createdBy;

    //inherited
    public final DateTimePath<java.time.LocalDateTime> createdTime = _super.createdTime;

    public final DateTimePath<java.time.LocalDateTime> endTime = createDateTime("endTime", java.time.LocalDateTime.class);

    public final NumberPath<Long> id = createNumber("id", Long.class);

    public final BooleanPath isDeletedScheduled = createBoolean("isDeletedScheduled");

    public final StringPath progress_status = createString("progress_status");

    public final NumberPath<Integer> repeatCount = createNumber("repeatCount", Integer.class);

    public final StringPath repeatGroupId = createString("repeatGroupId");

    public final NumberPath<Integer> repeatInterval = createNumber("repeatInterval", Integer.class);

    public final StringPath repeatType = createString("repeatType");

    public final NumberPath<Integer> scheduleDay = createNumber("scheduleDay", Integer.class);

    public final NumberPath<Integer> scheduleMonth = createNumber("scheduleMonth", Integer.class);

    public final DateTimePath<java.time.LocalDateTime> startTime = createDateTime("startTime", java.time.LocalDateTime.class);

    //inherited
    public final StringPath updatedBy = _super.updatedBy;

    //inherited
    public final DateTimePath<java.time.LocalDateTime> updatedTime = _super.updatedTime;

    public final NumberPath<Long> userId = createNumber("userId", Long.class);

    public QSchedules(String variable) {
        super(Schedules.class, forVariable(variable));
    }

    public QSchedules(Path<? extends Schedules> path) {
        super(path.getType(), path.getMetadata());
    }

    public QSchedules(PathMetadata metadata) {
        super(Schedules.class, metadata);
    }

}

