package com.example.rdb;

import static com.querydsl.core.types.PathMetadataFactory.*;

import com.querydsl.core.types.dsl.*;

import com.querydsl.core.types.PathMetadata;
import javax.annotation.processing.Generated;
import com.querydsl.core.types.Path;


/**
 * QFailedThumbnail is a Querydsl query type for FailedThumbnail
 */
@Generated("com.querydsl.codegen.DefaultEntitySerializer")
public class QFailedThumbnail extends EntityPathBase<FailedThumbnail> {

    private static final long serialVersionUID = -598739424L;

    public static final QFailedThumbnail failedThumbnail = new QFailedThumbnail("failedThumbnail");

    public final NumberPath<Long> id = createNumber("id", Long.class);

    public final DateTimePath<java.time.LocalDateTime> lastTriedAt = createDateTime("lastTriedAt", java.time.LocalDateTime.class);

    public final StringPath reason = createString("reason");

    public final BooleanPath resolved = createBoolean("resolved");

    public final NumberPath<Integer> retryCount = createNumber("retryCount", Integer.class);

    public final StringPath storedFileName = createString("storedFileName");

    public QFailedThumbnail(String variable) {
        super(FailedThumbnail.class, forVariable(variable));
    }

    public QFailedThumbnail(Path<? extends FailedThumbnail> path) {
        super(path.getType(), path.getMetadata());
    }

    public QFailedThumbnail(PathMetadata metadata) {
        super(FailedThumbnail.class, metadata);
    }

}

