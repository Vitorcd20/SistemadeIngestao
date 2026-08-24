package com.ingestion.repository;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.ingestion.dto.JobHandle;
import com.ingestion.dto.JobStatusResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.EmptyResultDataAccessException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import java.sql.ResultSet;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.NoSuchElementException;
import java.util.UUID;

@Repository
public class IngestionJobRepository {

    private static final Logger log = LoggerFactory.getLogger(IngestionJobRepository.class);

    private final JdbcTemplate jdbcTemplate;
    private final ObjectMapper objectMapper;

    public IngestionJobRepository(JdbcTemplate jdbcTemplate, ObjectMapper objectMapper) {
        this.jdbcTemplate = jdbcTemplate;
        this.objectMapper = objectMapper;
    }

    // O id BIGSERIAL interno só é usado server-side (FK de transactions, nome do
    // temp file); public_id é o identificador não-adivinhável que vai pro
    // cliente, então job não dá pra enumerar andando pelos ids sequenciais.
    public JobHandle createJob(String fileName, long ownerId) {
        return jdbcTemplate.queryForObject("""
                INSERT INTO ingestion_jobs (file_name, status, started_at, owner_user_id)
                VALUES (?, 'PROCESSING', now(), ?)
                RETURNING id, public_id
                """, (rs, rowNum) -> new JobHandle(rs.getLong("id"), rs.getObject("public_id", UUID.class)),
                fileName, ownerId);
    }

    public void updateProgress(long jobId, long rowsProcessed, long rowsFailed) {
        jdbcTemplate.update("""
                UPDATE ingestion_jobs
                SET rows_processed = ?, rows_failed = ?
                WHERE id = ?
                """, rowsProcessed, rowsFailed, jobId);
    }

    public void markCompleted(long jobId, long rowsProcessed, long rowsFailed, List<String> errorSample) {
        jdbcTemplate.update("""
                UPDATE ingestion_jobs
                SET status = 'COMPLETED', rows_processed = ?, rows_failed = ?,
                    error_sample = ?, completed_at = now()
                WHERE id = ?
                """, rowsProcessed, rowsFailed, toJson(errorSample), jobId);
    }

    public void markFailed(long jobId, String errorMessage) {
        jdbcTemplate.update("""
                UPDATE ingestion_jobs
                SET status = 'FAILED', error_sample = ?, completed_at = now()
                WHERE id = ?
                """, toJson(List.of(errorMessage)), jobId);
    }

    // Filtra por owner além do id: quem não é dono recebe o mesmo 404 de um id
    // inexistente (nunca 403) — um job id vazado/adivinhado não confirma nem
    // que o job existe pra quem não é dono.
    public JobStatusResponse findByPublicId(UUID publicId, long ownerId) {
        try {
            return jdbcTemplate.queryForObject("""
                    SELECT public_id, file_name, status, rows_processed, rows_failed,
                           error_sample, started_at, completed_at, created_at
                    FROM ingestion_jobs
                    WHERE public_id = ? AND owner_user_id = ?
                    """, this::mapRow, publicId, ownerId);
        } catch (EmptyResultDataAccessException e) {
            throw new NoSuchElementException("No ingestion job with id " + publicId);
        }
    }

    private JobStatusResponse mapRow(ResultSet rs, int rowNum) throws java.sql.SQLException {
        return new JobStatusResponse(
                rs.getObject("public_id", UUID.class),
                rs.getString("file_name"),
                rs.getString("status"),
                rs.getLong("rows_processed"),
                rs.getLong("rows_failed"),
                fromJson(rs.getString("error_sample")),
                toOffsetDateTime(rs.getObject("started_at", OffsetDateTime.class)),
                toOffsetDateTime(rs.getObject("completed_at", OffsetDateTime.class)),
                toOffsetDateTime(rs.getObject("created_at", OffsetDateTime.class))
        );
    }

    private static OffsetDateTime toOffsetDateTime(OffsetDateTime value) {
        return value;
    }

    private String toJson(List<String> values) {
        if (values == null || values.isEmpty()) {
            return null;
        }
        try {
            return objectMapper.writeValueAsString(values);
        } catch (JsonProcessingException e) {
            // Em vez de falhar a escrita do status inteiro, salva sem error_sample
            // — mas isso descarta silenciosamente o diagnóstico que essa coluna
            // existe pra guardar. Vale saber que isso pode acontecer.
            log.warn("Failed to serialize {} error-sample entries to JSON; storing null instead", values.size(), e);
            return null;
        }
    }

    private List<String> fromJson(String json) {
        if (json == null || json.isBlank()) {
            return List.of();
        }
        try {
            return objectMapper.readValue(json, objectMapper.getTypeFactory()
                    .constructCollectionType(List.class, String.class));
        } catch (JsonProcessingException e) {
            // Mesma lógica do toJson: degrada em vez de falhar, mas aqui o risco
            // é parecer job limpo por engano.
            log.warn("Failed to deserialize stored error_sample JSON; returning empty list instead", e);
            return List.of();
        }
    }
}
