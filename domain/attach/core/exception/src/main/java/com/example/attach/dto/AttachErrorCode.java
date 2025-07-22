package com.example.attach.dto;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

@Getter
@RequiredArgsConstructor
public enum AttachErrorCode {

    NOT_FOUND_ATTACH_LIST(40012,"첨부파일의 목록이 없습니다."),
    NOT_FOUND_ATTACH(40013,"첨부파일을 찾을 수 없습니다."),
    THUMBNAIL_CREATE_FAIL(50014, "썸네일 생성에 실패했습니다."),
    S3_DELETE_FAIL(50015,"S3파일을 삭제하는데 실패했습니다."),
    ATTACH_BIND_ERROR(50016,"첨부파일을 바인딩하는데 실패했습니다."),
    S3_OPERATION_FAIL(50017,"S3를 수행하는데 실패했습니다.");


    private final int status;

    private final String message;
}
