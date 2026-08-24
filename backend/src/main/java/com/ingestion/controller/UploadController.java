package com.ingestion.controller;

import com.ingestion.config.IngestionProperties;
import com.ingestion.dto.JobHandle;
import com.ingestion.dto.UploadResponse;
import com.ingestion.repository.IngestionJobRepository;
import com.ingestion.security.AppUserDetails;
import com.ingestion.service.CsvIngestionService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.core.task.TaskRejectedException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.server.ResponseStatusException;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;

@Tag(name = "Upload", description = "Envio de arquivos CSV para ingestão assíncrona")
@RestController
@RequestMapping("/api/uploads")
public class UploadController {

    private final IngestionJobRepository jobRepository;
    private final CsvIngestionService ingestionService;
    private final Path tempDir;
    private final IngestionProperties ingestionProperties;

    public UploadController(IngestionJobRepository jobRepository,
                             CsvIngestionService ingestionService,
                             IngestionProperties properties) throws IOException {
        this.jobRepository = jobRepository;
        this.ingestionService = ingestionService;
        this.ingestionProperties = properties;
        this.tempDir = Path.of(properties.tempDir());
        Files.createDirectories(tempDir);
    }

    @Operation(summary = "Enviar arquivo CSV",
            description = "Aceita um arquivo CSV com colunas `id,date,category,amount,description` e inicia o processamento em background. Retorna imediatamente com o ID do job.",
            responses = {
                @ApiResponse(responseCode = "202", description = "Arquivo aceito, processando em background"),
                @ApiResponse(responseCode = "400", description = "Arquivo vazio ou não é CSV"),
                @ApiResponse(responseCode = "413", description = "Arquivo excede o tamanho máximo permitido"),
                @ApiResponse(responseCode = "429", description = "Muitas requisições — aguarde e tente novamente"),
                @ApiResponse(responseCode = "503", description = "Fila de ingestão cheia")
            })
    @PostMapping(consumes = "multipart/form-data")
    public ResponseEntity<UploadResponse> upload(
            @Parameter(description = "Arquivo CSV (máx 100 MB)", required = true,
                    content = @Content(mediaType = "multipart/form-data"))
            @RequestParam("file") MultipartFile file,
                                                  @AuthenticationPrincipal AppUserDetails principal) {
        if (file.isEmpty()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Uploaded file is empty");
        }
        if (file.getSize() > ingestionProperties.maxFileSizeBytes()) {
            throw new ResponseStatusException(HttpStatus.PAYLOAD_TOO_LARGE, "File exceeds maximum allowed size");
        }
        String originalName = file.getOriginalFilename() != null ? file.getOriginalFilename() : "upload.csv";
        String safeName = Path.of(originalName).getFileName().toString();
        if (!safeName.toLowerCase().endsWith(".csv")) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Only .csv files are accepted");
        }

        long ownerId = principal.getId();
        JobHandle job = jobRepository.createJob(safeName, ownerId);
        Path target = tempDir.resolve("job-" + job.id() + ".csv");

        try (InputStream in = file.getInputStream()) {
            Files.copy(in, target, StandardCopyOption.REPLACE_EXISTING);
        } catch (IOException e) {
            jobRepository.markFailed(job.id(), "Failed to persist uploaded file — see server logs for details");
            throw new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR, "Failed to store uploaded file", e);
        }

        try {
            ingestionService.processFile(job.id(), ownerId, target);
        } catch (TaskRejectedException e) {
            deleteQuietly(target);
            jobRepository.markFailed(job.id(), "Ingestion queue is full, try again shortly");
            throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE,
                    "Server is busy processing other uploads, try again shortly");
        }

        return ResponseEntity.status(HttpStatus.ACCEPTED)
                .body(new UploadResponse(job.publicId(), "PROCESSING", "File accepted, processing in background"));
    }

    private void deleteQuietly(Path path) {
        try {
            Files.deleteIfExists(path);
        } catch (IOException ignored) {
        }
    }
}
