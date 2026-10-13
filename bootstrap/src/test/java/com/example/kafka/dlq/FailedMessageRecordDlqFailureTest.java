package com.example.kafka.dlq;

import com.example.notification.model.FailMessageModel;
import com.example.notification.service.FailedMessageService;
import com.example.outbound.notification.FailMessageOutConnector;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.*;

// DLQ로 들어온 실패를 어떻게 기록하는지 확인 (DB 없이 단위 테스트)
// 재발행한 메시지가 다시 실패해 돌아왔을 때 예전처럼 "이미 기록됨"으로 버리지 않고 같은 행을 다시 여는지가 핵심이다.
class FailedMessageRecordDlqFailureTest {

    private final FailMessageOutConnector connector = mock(FailMessageOutConnector.class);
    private final FailedMessageService service = new FailedMessageService(connector);

    @Test
    void 처음_들어온_실패는_새로_저장한다() {
        FailMessageModel failure = failure();
        given(connector.findByEventIdAndMessageType("evt-1", "NOTIFICATION")).willReturn(Optional.empty());

        service.recordDlqFailure(failure);

        verify(connector).createFailMessage(failure);
        verify(connector, never()).updateFailMessage(any());
    }

    @Test
    void 재발행_후_다시_실패하면_같은_행을_다시_열고_다음_단계로_예약한다() {
        FailMessageModel republished = row(1, true, false);
        given(connector.findByEventIdAndMessageType("evt-1", "NOTIFICATION")).willReturn(Optional.of(republished));

        service.recordDlqFailure(failure());

        verify(connector, never()).createFailMessage(any());
        verify(connector).updateFailMessage(republished);
        assertThat(republished.isResolved()).isFalse();
        assertThat(republished.getResolvedAt()).isNull();
        assertThat(republished.getRetryCount()).isEqualTo(2);
        assertThat(republished.getNextRetryTime()).isAfter(LocalDateTime.now());
        assertThat(republished.getMessageType()).isEqualTo("NOTIFICATION");
    }

    @Test
    void 아직_재처리_대기중인_행이면_중복_전달로_보고_무시한다() {
        FailMessageModel pending = row(0, false, false);
        given(connector.findByEventIdAndMessageType("evt-1", "NOTIFICATION")).willReturn(Optional.of(pending));

        service.recordDlqFailure(failure());

        verify(connector, never()).createFailMessage(any());
        verify(connector, never()).updateFailMessage(any());
        assertThat(pending.getRetryCount()).isZero();
    }

    @Test
    void 포기한_행이면_다시_열지_않는다() {
        FailMessageModel dead = row(4, false, true);
        given(connector.findByEventIdAndMessageType("evt-1", "NOTIFICATION")).willReturn(Optional.of(dead));

        service.recordDlqFailure(failure());

        verify(connector, never()).createFailMessage(any());
        verify(connector, never()).updateFailMessage(any());
        assertThat(dead.isDead()).isTrue();
    }

    private FailMessageModel failure() {
        return FailMessageModel.builder()
                .eventId("evt-1")
                .messageType("NOTIFICATION")
                .payload("{}")
                .retryCount(0)
                .resolved(false)
                .exceptionMessage("자동 DLQ 저장")
                .nextRetryTime(LocalDateTime.now().plusSeconds(5))
                .build();
    }

    private FailMessageModel row(int retryCount, boolean resolved, boolean dead) {
        return FailMessageModel.builder()
                .id(1L)
                .eventId("evt-1")
                .messageType("NOTIFICATION")
                .payload("{}")
                .retryCount(retryCount)
                .resolved(resolved)
                .dead(dead)
                .resolvedAt(resolved ? LocalDateTime.now().minusSeconds(30) : null)
                .build();
    }
}
