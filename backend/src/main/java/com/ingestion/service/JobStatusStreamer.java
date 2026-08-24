package com.ingestion.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.ingestion.config.SseProperties;
import com.ingestion.dto.JobStatusResponse;
import com.ingestion.repository.IngestionJobRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.util.Set;
import java.util.UUID;
import java.util.concurrent.Executor;

/**
 * Faz polling no status de um job numa thread dedicada e empurra pro emitter
 * SSE até o job chegar num status terminal ou o emitter estourar o timeout.
 * Ficou fora do JobController pra essa lógica ser testável separada do
 * transporte HTTP/SSE.
 */
@Service
public class JobStatusStreamer {

    private static final Logger log = LoggerFactory.getLogger(JobStatusStreamer.class);
    private static final Set<String> TERMINAL_STATUSES = Set.of("COMPLETED", "FAILED");

    private final IngestionJobRepository jobRepository;
    private final SseProperties sseProperties;
    private final Executor sseExecutor;
    private final ObjectMapper objectMapper;

    public JobStatusStreamer(IngestionJobRepository jobRepository,
                              SseProperties sseProperties,
                              Executor sseExecutor,
                              ObjectMapper objectMapper) {
        this.jobRepository = jobRepository;
        this.sseProperties = sseProperties;
        this.sseExecutor = sseExecutor;
        this.objectMapper = objectMapper;
    }

    // Começa a fazer polling do jobId (com escopo em ownerId) na sseExecutor e
    // escreve cada status no emitter até chegar num status terminal. ownerId
    // precisa ser capturado pelo caller na thread da request — SecurityContextHolder
    // é thread-local e não propaga sozinho pras threads da sseExecutor.
    public SseEmitter stream(UUID jobId, long ownerId) {
        SseEmitter emitter = new SseEmitter(sseProperties.timeoutMs());

        sseExecutor.execute(() -> {
            try {
                while (true) {
                    JobStatusResponse status = jobRepository.findByPublicId(jobId, ownerId);
                    emitter.send(SseEmitter.event()
                            .name("status")
                            .data(objectMapper.writeValueAsString(status)));

                    if (TERMINAL_STATUSES.contains(status.status())) {
                        emitter.complete();
                        return;
                    }
                    Thread.sleep(sseProperties.pollIntervalMs());
                }
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            } catch (Exception e) {
                log.debug("SSE stream for job {} ended: {}", jobId, e.getMessage());
                emitter.completeWithError(e);
            }
        });

        emitter.onTimeout(emitter::complete);
        return emitter;
    }
}
