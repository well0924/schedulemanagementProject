package com.example.rdbrepository;

import static com.querydsl.core.types.PathMetadataFactory.*;

import com.querydsl.core.types.dsl.*;

import com.querydsl.core.types.PathMetadata;
import javax.annotation.processing.Generated;
import com.querydsl.core.types.Path;


/**
 * QNotificationSetting is a Querydsl query type for NotificationSetting
 */
@Generated("com.querydsl.codegen.DefaultEntitySerializer")
public class QNotificationSetting extends EntityPathBase<NotificationSetting> {

    private static final long serialVersionUID = 1334334176L;

    public static final QNotificationSetting notificationSetting = new QNotificationSetting("notificationSetting");

    public final BooleanPath emailEnabled = createBoolean("emailEnabled");

    public final NumberPath<Long> id = createNumber("id", Long.class);

    public final BooleanPath pushEnabled = createBoolean("pushEnabled");

    public final BooleanPath scheduleCreatedEnabled = createBoolean("scheduleCreatedEnabled");

    public final BooleanPath scheduleDeletedEnabled = createBoolean("scheduleDeletedEnabled");

    public final BooleanPath scheduleRemindEnabled = createBoolean("scheduleRemindEnabled");

    public final BooleanPath scheduleUpdatedEnabled = createBoolean("scheduleUpdatedEnabled");

    public final StringPath userId = createString("userId");

    public final BooleanPath webEnabled = createBoolean("webEnabled");

    public QNotificationSetting(String variable) {
        super(NotificationSetting.class, forVariable(variable));
    }

    public QNotificationSetting(Path<? extends NotificationSetting> path) {
        super(path.getType(), path.getMetadata());
    }

    public QNotificationSetting(PathMetadata metadata) {
        super(NotificationSetting.class, metadata);
    }

}

