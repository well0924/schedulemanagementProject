package com.example.rdbrepository;

import static com.querydsl.core.types.PathMetadataFactory.*;

import com.querydsl.core.types.dsl.*;

import com.querydsl.core.types.PathMetadata;
import javax.annotation.processing.Generated;
import com.querydsl.core.types.Path;


/**
 * QAttach is a Querydsl query type for Attach
 */
@Generated("com.querydsl.codegen.DefaultEntitySerializer")
public class QAttach extends EntityPathBase<Attach> {

    private static final long serialVersionUID = -561967510L;

    public static final QAttach attach = new QAttach("attach");

    public final com.example.jpa.config.base.QBaseEntity _super = new com.example.jpa.config.base.QBaseEntity(this);

    //inherited
    public final StringPath createdBy = _super.createdBy;

    //inherited
    public final DateTimePath<java.time.LocalDateTime> createdTime = _super.createdTime;

    public final StringPath filePath = createString("filePath");

    public final NumberPath<Long> fileSize = createNumber("fileSize", Long.class);

    public final NumberPath<Long> id = createNumber("id", Long.class);

    public final BooleanPath isDeletedAttach = createBoolean("isDeletedAttach");

    public final StringPath originFileName = createString("originFileName");

    public final NumberPath<Long> scheduledId = createNumber("scheduledId", Long.class);

    public final StringPath storedFileName = createString("storedFileName");

    public final StringPath thumbnailFilePath = createString("thumbnailFilePath");

    //inherited
    public final StringPath updatedBy = _super.updatedBy;

    //inherited
    public final DateTimePath<java.time.LocalDateTime> updatedTime = _super.updatedTime;

    public QAttach(String variable) {
        super(Attach.class, forVariable(variable));
    }

    public QAttach(Path<? extends Attach> path) {
        super(path.getType(), path.getMetadata());
    }

    public QAttach(PathMetadata metadata) {
        super(Attach.class, metadata);
    }

}

