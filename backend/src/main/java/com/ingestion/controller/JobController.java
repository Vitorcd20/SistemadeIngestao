package com.ingestion.controller;

import com.ingestion.dto.JobStatusResponse;
import com.ingestion.repository.IngestionJobRepository;
import com.ingestion.security.AppUserDetails;
import com.ingestion.service.JobStatusStreamer;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.util.UUID;

@Tag(name = "Jobs de Ingestão", description = "Consulta de status dos jobs de processamento de CSV")
@RestController
@RequestMapping("/api/jobs")
public class JobController {

    private final IngestionJobRepository jobRepository;
    private final JobStatusStreamer jobStatusStreamer;

    public JobController(IngestionJobRepository jobRepository, JobStatusStreamer jobStatusStreamer) {
        this.jobRepository = jobRepository;
        this.jobStatusStreamer = jobStatusStreamer;
    }

    @Operation(summary = "Status do job",
            description = "Retorna o status atual de um job de ingestão. Pertence ao usuário autenticado.",
            responses = {
                @ApiResponse(responseCode = "200", description = "Status retornado"),
                @ApiResponse(responseCode = "404", description = "Job não encontrado ou não pertence ao usuário")
            })
    @GetMapping("/{id}")
    public JobStatusResponse getStatus(
            @Parameter(description = "ID público do job (UUID retornado pelo upload)") @PathVariable UUID id,
            @AuthenticationPrincipal AppUserDetails principal) {
        return jobRepository.findByPublicId(id, principal.getId());
    }

    @Operation(summary = "Stream SSE de status do job",
            description = "Abre um stream Server-Sent Events que emite atualizações de status até o job atingir `COMPLETED` ou `FAILED`.")
    @GetMapping("/{id}/events")
    public SseEmitter streamStatus(
            @Parameter(description = "ID público do job (UUID)") @PathVariable UUID id,
            @AuthenticationPrincipal AppUserDetails principal) {
        return jobStatusStreamer.stream(id, principal.getId());
    }
}
