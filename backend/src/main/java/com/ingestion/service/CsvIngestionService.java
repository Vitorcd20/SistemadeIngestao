package com.ingestion.service;

import com.ingestion.config.IngestionProperties;
import com.ingestion.dto.TransactionRow;
import com.ingestion.repository.IngestionJobRepository;
import com.ingestion.repository.TransactionRepository;
import org.apache.commons.csv.CSVFormat;
import org.apache.commons.csv.CSVParser;
import org.apache.commons.csv.CSVRecord;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

import java.io.BufferedReader;
import java.io.IOException;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDate;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.List;

/**
 * Lê o CSV enviado linha a linha via CSVParser em streaming e grava no
 * Postgres em lotes de tamanho fixo. Em nenhum momento essa classe segura o
 * arquivo inteiro, nem o resultado inteiro, em memória — só um lote de
 * linhas parseadas por vez (app.ingestion.batch-size, default 5.000).
 */
@Service
public class CsvIngestionService {

    private static final Logger log = LoggerFactory.getLogger(CsvIngestionService.class);

    private static final CSVFormat CSV_FORMAT = CSVFormat.DEFAULT.builder()
            .setHeader()
            .setSkipHeaderRecord(true)
            .setTrim(true)
            .build();

    private final TransactionRepository transactionRepository;
    private final IngestionJobRepository jobRepository;
    private final IngestionProperties properties;

    public CsvIngestionService(TransactionRepository transactionRepository,
                                IngestionJobRepository jobRepository,
                                IngestionProperties properties) {
        this.transactionRepository = transactionRepository;
        this.jobRepository = jobRepository;
        this.properties = properties;
    }

    @Async("ingestionExecutor")
    public void processFile(long jobId, long ownerId, Path filePath) {
        long processed = 0;
        long failed = 0;
        List<String> errorSample = new ArrayList<>();
        List<TransactionRow> batch = new ArrayList<>(properties.batchSize());

        try (BufferedReader reader = Files.newBufferedReader(filePath, StandardCharsets.UTF_8);
             CSVParser parser = CSVParser.parse(reader, CSV_FORMAT)) {

            for (CSVRecord record : parser) {
                try {
                    batch.add(parseRow(record));
                } catch (RowParseException e) {
                    failed++;
                    if (errorSample.size() < properties.errorSampleLimit()) {
                        errorSample.add("line " + record.getRecordNumber() + ": " + e.getMessage());
                    }
                    continue;
                }

                if (batch.size() >= properties.batchSize()) {
                    processed += flush(batch, jobId, ownerId);
                    jobRepository.updateProgress(jobId, processed, failed);
                }
            }

            if (!batch.isEmpty()) {
                processed += flush(batch, jobId, ownerId);
            }

            jobRepository.markCompleted(jobId, processed, failed, errorSample);
            log.info("Ingestion job {} completed: {} processed, {} failed", jobId, processed, failed);

        } catch (IOException e) {
            log.error("Ingestion job {} failed while reading file", jobId, e);
            jobRepository.markFailed(jobId, "Failed to read uploaded file: " + e.getMessage());
        } catch (Exception e) {
            log.error("Ingestion job {} failed unexpectedly", jobId, e);
            jobRepository.markFailed(jobId, "Unexpected error: " + e.getMessage());
        } finally {
            deleteQuietly(filePath);
        }
    }

    private long flush(List<TransactionRow> batch, long jobId, long ownerId) {
        transactionRepository.batchInsert(batch, jobId, ownerId);
        int count = batch.size();
        batch.clear();
        return count;
    }

    private TransactionRow parseRow(CSVRecord record) {
        long id;
        LocalDate date;
        BigDecimal amount;

        try {
            id = Long.parseLong(record.get("id"));
        } catch (IllegalArgumentException e) {
            throw new RowParseException("invalid id '" + safeGet(record, "id") + "'");
        }

        try {
            date = LocalDate.parse(record.get("date"));
        } catch (DateTimeParseException | IllegalArgumentException e) {
            throw new RowParseException("invalid date '" + safeGet(record, "date") + "'");
        }

        String category = record.get("category");
        if (category == null || category.isBlank()) {
            throw new RowParseException("missing category");
        }

        try {
            amount = new BigDecimal(record.get("amount"));
        } catch (IllegalArgumentException e) {
            throw new RowParseException("invalid amount '" + safeGet(record, "amount") + "'");
        }

        String description = record.isMapped("description") ? record.get("description") : null;

        return new TransactionRow(id, date, category, amount, description);
    }

    private String safeGet(CSVRecord record, String column) {
        try {
            return record.get(column);
        } catch (Exception e) {
            return "<missing>";
        }
    }

    private void deleteQuietly(Path path) {
        try {
            Files.deleteIfExists(path);
        } catch (IOException e) {
            log.warn("Failed to delete temp upload file {}", path, e);
        }
    }
}
