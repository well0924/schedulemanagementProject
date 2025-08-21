package com.example.logging.MDC;

import org.slf4j.MDC;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.task.TaskDecorator;
import org.springframework.scheduling.annotation.EnableAsync;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

import java.util.Map;
import java.util.concurrent.Executor;

@EnableAsync
@Configuration
public class AsyncConfig {

    @Bean
    public TaskDecorator mdcTaskDecorator() {
        return runnable -> {
            Map<String, String> contextMap = MDC.getCopyOfContextMap();
            return () -> {
                Map<String, String> backup = MDC.getCopyOfContextMap();
                if (contextMap != null) MDC.setContextMap(contextMap);
                if (contextMap != null) MDC.setContextMap(contextMap); else MDC.clear();
                try { runnable.run(); }
                finally { if (backup != null) MDC.setContextMap(backup); else MDC.clear();}
            };
        };
    }

    @Bean(name = "threadPoolTaskExecutor")
    public Executor threadPoolTaskExecutor(TaskDecorator mdcTaskDecorator) {
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        executor.setTaskDecorator(mdcTaskDecorator);
        executor.setCorePoolSize(5);
        executor.setMaxPoolSize(10);
        executor.setQueueCapacity(100);
        executor.setThreadNamePrefix("AsyncExecutor-");
        executor.initialize();
        return executor;
    }
}
