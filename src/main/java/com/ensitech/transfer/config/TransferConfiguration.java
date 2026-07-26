package com.ensitech.transfer.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.time.Clock;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;

@Configuration
public class TransferConfiguration {
    @Bean
    Clock clock() {
        return Clock.systemUTC();
    }

    @Bean(name = "transferExecutor", destroyMethod = "shutdownNow")
    ExecutorService transferExecutor(
            @Value("${transfer.executor.workers:4}") int workers,
            @Value("${transfer.executor.queue-capacity:100}") int queueCapacity
    ) {
        return new ThreadPoolExecutor(
                workers,
                workers,
                0L,
                TimeUnit.MILLISECONDS,
                new ArrayBlockingQueue<>(queueCapacity),
                Thread.ofPlatform().name("transfer-worker-", 0).factory(),
                new ThreadPoolExecutor.AbortPolicy()
        );
    }
}
