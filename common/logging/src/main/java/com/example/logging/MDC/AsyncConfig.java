package com.example.logging.MDC;

import org.slf4j.MDC;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.task.TaskDecorator;
import org.springframework.scheduling.annotation.EnableAsync;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;
import io.micrometer.context.ContextSnapshot;
import java.util.Map;
import java.util.concurrent.Executor;

@EnableAsync
@Configuration
public class AsyncConfig {

    /**
     * 비동기 스레드 실행 시, 메인 스레드의 MDC와 관측(Observation) 컨텍스트를
     * 안전하게 복사하여 자식 스레드로 전파하는 TaskDecorator.
     */
    @Bean
    public TaskDecorator mdcTaskDecorator() {
        return runnable -> {
            // 메인 스레드에서 현재의 Trace/Span 정보(Observation)와 로깅 컨텍스트(MDC) 캡처
            var contextSnapshot = ContextSnapshot.captureAll();
            Map<String, String> contextMap = MDC.getCopyOfContextMap();
            return () -> {
                // 비동기 스레드 실행 시 캡처했던 컨텍스트 복구
                // setThreadLocals()는 try-with-resources 종료 시 자동으로 자원을 정리함
                try (var scope = contextSnapshot.setThreadLocals()) {
                    if (contextMap != null) MDC.setContextMap(contextMap);
                    // 실제 비즈니스 로직 실행
                    runnable.run();
                } finally {
                    // 스레드 로컬 자원 초기화 (스레드 풀 재사용 시 오염 방지)
                    MDC.clear();
                }
            };
        };
    }

    @Bean(name = "threadPoolTaskExecutor")
    public Executor threadPoolTaskExecutor(TaskDecorator mdcTaskDecorator) {
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        executor.setTaskDecorator(mdcTaskDecorator);
        executor.setCorePoolSize(20);
        executor.setMaxPoolSize(50);
        executor.setQueueCapacity(500);
        executor.setThreadNamePrefix("AsyncExecutor-");
        executor.setRejectedExecutionHandler(new java.util.concurrent.ThreadPoolExecutor.CallerRunsPolicy()); // 풀이 꽉 차면 호출한 쓰레드에서 처리 (안정성)
        executor.initialize();
        return executor;
    }
}
