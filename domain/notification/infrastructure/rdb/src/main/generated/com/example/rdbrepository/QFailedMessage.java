package com.example.rdbrepository;

import static com.querydsl.core.types.PathMetadataFactory.*;

import com.querydsl.core.types.dsl.*;

import com.querydsl.core.types.PathMetadata;
import javax.annotation.processing.Generated;
import com.querydsl.core.types.Path;


/**
 * QFailedMessage is a Querydsl query type for FailedMessage
 */
@Generated("com.querydsl.codegen.DefaultEntitySerializer")
public class QFailedMessage extends EntityPathBase<FailedMessage> {

    private static final long serialVersionUID = -1494511291L;

    public static final QFailedMessage failedMessage = new QFailedMessage("failedMessage");

    public final DateTimePath<java.time.LocalDateTime> createdAt = createDateTime("createdAt", java.time.LocalDateTime.class);

    public final BooleanPath dead = createBoolean("dead");

    public final StringPath exceptionMessage = createString("exceptionMessage");

    public final NumberPath<Long> id = createNumber("id", Long.class);

    public final DateTimePath<java.time.LocalDateTime> lastTriedAt = createDateTime("lastTriedAt", java.time.LocalDateTime.class);

    public final StringPath messageType = createString("messageType");

    public final StringPath payload = createString("payload");

    public final BooleanPath resolved = createBoolean("resolved");

    public final DateTimePath<java.time.LocalDateTime> resolvedAt = createDateTime("resolvedAt", java.time.LocalDateTime.class);

    public final NumberPath<Integer> retryCount = createNumber("retryCount", Integer.class);

    public final StringPath topic = createString("topic");

    public QFailedMessage(String variable) {
        super(FailedMessage.class, forVariable(variable));
    }

    public QFailedMessage(Path<? extends FailedMessage> path) {
        super(path.getType(), path.getMetadata());
    }

    public QFailedMessage(PathMetadata metadata) {
        super(FailedMessage.class, metadata);
    }

}

