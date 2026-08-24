package com.ingestion.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

import java.util.concurrent.Executor;

@Configuration
public class AsyncConfig {

    // Pool limitado pros jobs de ingestão CSV. Pequeno de propósito — ingestão
    // é limitada por escrita em banco, não CPU, então mais threads só significa
    // mais escritores em lote disputando a mesma tabela/índices.
    @Bean(name = "ingestionExecutor")
    public Executor ingestionExecutor() {
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        executor.setCorePoolSize(2);
        executor.setMaxPoolSize(2);
        executor.setQueueCapacity(20);
        executor.setThreadNamePrefix("ingestion-");
        executor.initialize();
        return executor;
    }

    // Pool separado pros loops de polling do SSE, pra uma rajada de conexões
    // do dashboard nunca sufocar o ingestionExecutor acima.
    @Bean(name = "sseExecutor")
    public Executor sseExecutor() {
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        executor.setCorePoolSize(4);
        executor.setMaxPoolSize(16);
        executor.setQueueCapacity(50);
        executor.setThreadNamePrefix("sse-");
        executor.initialize();
        return executor;
    }
}
