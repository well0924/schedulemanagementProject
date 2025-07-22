package com.example.rdbrepository;

import static com.querydsl.core.types.PathMetadataFactory.*;

import com.querydsl.core.types.dsl.*;

import com.querydsl.core.types.PathMetadata;
import javax.annotation.processing.Generated;
import com.querydsl.core.types.Path;


/**
 * QFailEmailEntity is a Querydsl query type for FailEmailEntity
 */
@Generated("com.querydsl.codegen.DefaultEntitySerializer")
public class QFailEmailEntity extends EntityPathBase<FailEmailEntity> {

    private static final long serialVersionUID = 152764956L;

    public static final QFailEmailEntity failEmailEntity = new QFailEmailEntity("failEmailEntity");

    public final StringPath content = createString("content");

    public final DateTimePath<java.time.LocalDateTime> createdAt = createDateTime("createdAt", java.time.LocalDateTime.class);

    public final NumberPath<Long> id = createNumber("id", Long.class);

    public final BooleanPath resolved = createBoolean("resolved");

    public final StringPath subject = createString("subject");

    public final StringPath toEmail = createString("toEmail");

    public QFailEmailEntity(String variable) {
        super(FailEmailEntity.class, forVariable(variable));
    }

    public QFailEmailEntity(Path<? extends FailEmailEntity> path) {
        super(path.getType(), path.getMetadata());
    }

    public QFailEmailEntity(PathMetadata metadata) {
        super(FailEmailEntity.class, metadata);
    }

}

