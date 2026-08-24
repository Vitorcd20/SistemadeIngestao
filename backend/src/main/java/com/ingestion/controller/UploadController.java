package com.ingestion.controller;

import com.ingestion.config.IngestionProperties;
import com.ingestion.dto.JobHandle;
import com.ingestion.dto.UploadResponse;
import com.ingestion.repository.IngestionJobRepository;
import com.ingestion.security.AppUserDetails;
import com.ingestion.service.CsvIngestionService;
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

@RestController
@RequestMapping("/api/uploads")
public class UploadController {

    private final IngestionJobRepository jobRepository;
    private final CsvIngestionService ingestionService;
    private final Path tempDir;

    public UploadController(IngestionJobRepository jobRepository,
                             CsvIngestionService ingestionService,
                             IngestionProperties properties) throws IOException {
        this.jobRepository = jobRepository;
        this.ingestionService = ingestionService;
        this.tempDir = Path.of(properties.tempDir());
        Files.createDirectories(tempDir);
    }

    @PostMapping
    public ResponseEntity<UploadResponse> upload(@RequestParam("file") MultipartFile file,
                                                  @AuthenticationPrincipal AppUserDetails principal) {
        if (file.isEmpty()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Uploaded file is empty");
        }
        String originalName = file.getOriginalFilename() != null ? file.getOriginalFilename() : "upload.csv";
        if (!originalName.toLowerCase().endsWith(".csv")) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Only .csv files are accepted");
        }

        long ownerId = principal.getId();
        JobHandle job = jobRepository.createJob(originalName, ownerId);
        Path target = tempDir.resolve("job-" + job.id() + ".csv");

        // Stream do upload (já spooled em disco, ver multipart.file-size-threshold)
        // direto pro nosso temp file gerenciado — nunca vira byte[].
        try (InputStream in = file.getInputStream()) {
            Files.copy(in, target, StandardCopyOption.REPLACE_EXISTING);
        } catch (IOException e) {
            jobRepository.markFailed(job.id(), "Failed to persist uploaded file: " + e.getMessage());
            throw new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR, "Failed to store uploaded file", e);
        }

        try {
            ingestionService.processFile(job.id(), ownerId, target);
        } catch (TaskRejectedException e) {
            // Fila do ingestionExecutor (AsyncConfig) cheia — recua em vez de
            // deixar isso virar um 500 sem tratamento.
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
            // limpeza best-effort; temp dir não é crítico pra um arquivo só
        }
    }
}
