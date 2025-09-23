package com.example.attach.dto;

import com.example.exception.BaseErrorCode;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;

@Getter
@RequiredArgsConstructor
public enum AttachErrorCode implements BaseErrorCode {

    NOT_FOUND_ATTACH_LIST(HttpStatus.NOT_FOUND,40012,"첨부파일의 목록이 없습니다."),
    NOT_FOUND_ATTACH(HttpStatus.NOT_FOUND,40013,"첨부파일을 찾을 수 없습니다."),
    THUMBNAIL_CREATE_FAIL(HttpStatus.INTERNAL_SERVER_ERROR,50014, "썸네일 생성에 실패했습니다."),
    S3_DELETE_FAIL(HttpStatus.INTERNAL_SERVER_ERROR,50015,"S3파일을 삭제하는데 실패했습니다."),
    ATTACH_BIND_ERROR(HttpStatus.INTERNAL_SERVER_ERROR,50016,"첨부파일을 바인딩하는데 실패했습니다."),
    S3_OPERATION_FAIL(HttpStatus.INTERNAL_SERVER_ERROR,50017,"S3를 수행하는데 실패했습니다.");

    private final HttpStatus httpStatus;

    private final int code;

    private final String message;
}
