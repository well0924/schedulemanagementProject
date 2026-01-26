package com.example.rdbrepository;

import static com.querydsl.core.types.PathMetadataFactory.*;

import com.querydsl.core.types.dsl.*;

import com.querydsl.core.types.PathMetadata;
import javax.annotation.processing.Generated;
import com.querydsl.core.types.Path;


/**
 * QNotification is a Querydsl query type for Notification
 */
@Generated("com.querydsl.codegen.DefaultEntitySerializer")
public class QNotification extends EntityPathBase<Notification> {

    private static final long serialVersionUID = -250922672L;

    public static final QNotification notification = new QNotification("notification");

    public final com.example.jpa.config.base.QBaseEntity _super = new com.example.jpa.config.base.QBaseEntity(this);

    //inherited
    public final StringPath createdBy = _super.createdBy;

    //inherited
    public final DateTimePath<java.time.LocalDateTime> createdTime = _super.createdTime;

    public final NumberPath<Long> id = createNumber("id", Long.class);

    public final BooleanPath isRead = createBoolean("isRead");

    public final BooleanPath isReminderSent = createBoolean("isReminderSent");

    public final BooleanPath isSent = createBoolean("isSent");

    public final StringPath message = createString("message");

    public final StringPath notificationType = createString("notificationType");

    public final DateTimePath<java.time.LocalDateTime> scheduledAt = createDateTime("scheduledAt", java.time.LocalDateTime.class);

    public final NumberPath<Long> scheduleId = createNumber("scheduleId", Long.class);

    //inherited
    public final StringPath updatedBy = _super.updatedBy;

    //inherited
    public final DateTimePath<java.time.LocalDateTime> updatedTime = _super.updatedTime;

    public final NumberPath<Long> userId = createNumber("userId", Long.class);

    public QNotification(String variable) {
        super(Notification.class, forVariable(variable));
    }

    public QNotification(Path<? extends Notification> path) {
        super(path.getType(), path.getMetadata());
    }

    public QNotification(PathMetadata metadata) {
        super(Notification.class, metadata);
    }

}

