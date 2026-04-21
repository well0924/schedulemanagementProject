package com.example.apimodel.schedule;

import lombok.Builder;

@Builder
public record ChatTokenResponse(
        String token,   // 스트리밍 토큰 or 메시지
        boolean done    // 스트리밍 완료 여부
) {
}
