package com.pawtrail.extract.application.service;

import com.pawtrail.common.exception.CustomException;
import com.pawtrail.extract.application.dto.output.TriggerOutput;
import com.pawtrail.extract.domain.exception.ExtractErrorCode;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * 동시 실행 막기를 봅니다. 실행기는 흉내라 실제로 돌지 않고 끝내기 신호만 쥡니다.
 */
class ExtractTriggerServiceTest {

    @Test
    @DisplayName("돌고 있는 동안의 두 번째 요청은 409 로 거절하고, 끝나면 다시 받는다")
    void 동시_실행_막기() {
        HoldingExecutor executor = new HoldingExecutor();
        ExtractTriggerService service = new ExtractTriggerService(executor);

        TriggerOutput first = service.start(10);

        assertThat(first.limit()).isEqualTo(10);
        assertThat(service.isRunning()).isTrue();
        assertThatThrownBy(() -> service.start(null))
                .isInstanceOf(CustomException.class)
                .extracting(e -> ((CustomException) e).getErrorCode())
                .isEqualTo(ExtractErrorCode.EXTRACT_ALREADY_RUNNING);

        executor.finish();

        assertThat(service.isRunning()).isFalse();
        assertThat(service.start(null).limit()).isNull();
    }

    @Test
    @DisplayName("실행기가 일을 받지 못하면 표시를 풀어 다음 요청이 막히지 않는다")
    void 실행기가_거절() {
        ExtractTriggerService service = new ExtractTriggerService(new RejectingExecutor());

        assertThatThrownBy(() -> service.start(null)).isInstanceOf(IllegalStateException.class);
        assertThat(service.isRunning()).isFalse();
    }

    // 끝내기 신호를 쥐고 있다가 테스트가 부를 때 끝내는 흉내
    private static final class HoldingExecutor extends ExtractExecutor {

        private Runnable onFinish;

        HoldingExecutor() {
            super(null);
        }

        @Override
        public void execute(LocalDateTime startedAt, Integer limit, Runnable onFinish) {
            this.onFinish = onFinish;
        }

        void finish() {
            onFinish.run();
        }
    }

    private static final class RejectingExecutor extends ExtractExecutor {

        RejectingExecutor() {
            super(null);
        }

        @Override
        public void execute(LocalDateTime startedAt, Integer limit, Runnable onFinish) {
            throw new IllegalStateException("스레드 풀이 가득 참");
        }
    }
}
