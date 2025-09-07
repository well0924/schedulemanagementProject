package com.example.rdbrepository;

import static com.querydsl.core.types.PathMetadataFactory.*;

import com.querydsl.core.types.dsl.*;

import com.querydsl.core.types.PathMetadata;
import javax.annotation.processing.Generated;
import com.querydsl.core.types.Path;


/**
 * QPushSubscription is a Querydsl query type for PushSubscription
 */
@Generated("com.querydsl.codegen.DefaultEntitySerializer")
public class QPushSubscription extends EntityPathBase<PushSubscription> {

    private static final long serialVersionUID = 1985903420L;

    public static final QPushSubscription pushSubscription = new QPushSubscription("pushSubscription");

    public final BooleanPath active = createBoolean("active");

    public final StringPath auth = createString("auth");

    public final DateTimePath<java.time.LocalDateTime> createdAt = createDateTime("createdAt", java.time.LocalDateTime.class);

    public final StringPath endpoint = createString("endpoint");

    public final NumberPath<Long> expirationTime = createNumber("expirationTime", Long.class);

    public final NumberPath<Long> id = createNumber("id", Long.class);

    public final NumberPath<Long> memberId = createNumber("memberId", Long.class);

    public final StringPath p256dh = createString("p256dh");

    public final DateTimePath<java.time.LocalDateTime> revokedAt = createDateTime("revokedAt", java.time.LocalDateTime.class);

    public final StringPath userAgent = createString("userAgent");

    public QPushSubscription(String variable) {
        super(PushSubscription.class, forVariable(variable));
    }

    public QPushSubscription(Path<? extends PushSubscription> path) {
        super(path.getType(), path.getMetadata());
    }

    public QPushSubscription(PathMetadata metadata) {
        super(PushSubscription.class, metadata);
    }

}

