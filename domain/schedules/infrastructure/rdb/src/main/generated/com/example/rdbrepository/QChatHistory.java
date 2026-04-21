package com.example.rdbrepository;

import static com.querydsl.core.types.PathMetadataFactory.*;

import com.querydsl.core.types.dsl.*;

import com.querydsl.core.types.PathMetadata;
import javax.annotation.processing.Generated;
import com.querydsl.core.types.Path;


/**
 * QChatHistory is a Querydsl query type for ChatHistory
 */
@Generated("com.querydsl.codegen.DefaultEntitySerializer")
public class QChatHistory extends EntityPathBase<ChatHistory> {

    private static final long serialVersionUID = -2117393129L;

    public static final QChatHistory chatHistory = new QChatHistory("chatHistory");

    public final StringPath assistantResponse = createString("assistantResponse");

    public final DateTimePath<java.time.LocalDateTime> createdAt = createDateTime("createdAt", java.time.LocalDateTime.class);

    public final NumberPath<Long> id = createNumber("id", Long.class);

    public final NumberPath<Long> memberId = createNumber("memberId", Long.class);

    public final StringPath userMessage = createString("userMessage");

    public QChatHistory(String variable) {
        super(ChatHistory.class, forVariable(variable));
    }

    public QChatHistory(Path<? extends ChatHistory> path) {
        super(path.getType(), path.getMetadata());
    }

    public QChatHistory(PathMetadata metadata) {
        super(ChatHistory.class, metadata);
    }

}

