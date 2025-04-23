package com.example.notification;

import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@NoArgsConstructor
public enum NotificationType {
    SIGN_UP_WELCOME,//회원가입
    SCHEDULE_CREATED,//일정생성
    SCHEDULE_REMINDER,//일정 리마인드
    SCHEDULE_UPDATED,//일정 수정
    SCHEDULE_DELETED,//일정 삭제
    SCHEDULE_OVERDUE,//일정겹칩
    SCHEDULE_COMPLETED,//일정 완료
    SCHEDULE_REPEATED,//일정 반복
    SYSTEM_ANNOUNCEMENT,//시스탬 공지
    CUSTOM_NOTIFICATION,//
    TAG_MENTION//태그추가
}
