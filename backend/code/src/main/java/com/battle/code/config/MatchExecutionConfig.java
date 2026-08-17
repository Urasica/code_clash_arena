package com.battle.code.config;

import com.battle.code.observability.MatchLogContext;
import io.micrometer.core.instrument.Gauge;
import io.micrometer.core.instrument.MeterRegistry;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

import java.util.concurrent.Executor;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;

@Configuration
public class MatchExecutionConfig {

    @Bean(name = "matchExecutionExecutor")
    public Executor matchExecutionExecutor(
            @Value("${cca.match.executor.core-size:2}") int coreSize,
            @Value("${cca.match.executor.max-size:4}") int maxSize,
            @Value("${cca.match.executor.queue-capacity:20}") int queueCapacity,
            MeterRegistry meterRegistry
    ) {
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        executor.setThreadNamePrefix("match-exec-");
        executor.setCorePoolSize(coreSize);
        executor.setMaxPoolSize(maxSize);
        executor.setQueueCapacity(queueCapacity);
        executor.setWaitForTasksToCompleteOnShutdown(true);
        executor.setAwaitTerminationSeconds(45);
        executor.setTaskDecorator(MatchLogContext::copy);
        executor.initialize();
        Gauge.builder("cca.match.executor.active", executor, ThreadPoolTaskExecutor::getActiveCount)
                .description("Currently active match execution workers")
                .register(meterRegistry);
        Gauge.builder("cca.match.executor.queued", executor,
                        value -> value.getThreadPoolExecutor().getQueue().size())
                .description("Match executions waiting for a worker")
                .register(meterRegistry);
        return executor;
    }

    @Bean(destroyMethod = "shutdown")
    public ScheduledExecutorService reconnectScheduler() {
        return Executors.newSingleThreadScheduledExecutor(runnable -> {
            Thread thread = new Thread(runnable, "match-reconnect-grace");
            thread.setDaemon(true);
            return thread;
        });
    }
}
