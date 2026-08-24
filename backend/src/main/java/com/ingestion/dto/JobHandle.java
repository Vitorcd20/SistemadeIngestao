package com.ingestion.dto;

import java.util.UUID;

/**
 * Id interno (FK, nunca exposto) junto com o id público (não-adivinhável,
 * devolvido pro cliente) de um job recém-criado.
 */
public record JobHandle(long id, UUID publicId) {
}
