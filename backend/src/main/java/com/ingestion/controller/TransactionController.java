package com.ingestion.controller;

import com.ingestion.dto.TransactionPageResponse;
import com.ingestion.dto.TransactionRow;
import com.ingestion.repository.TransactionRepository;
import com.ingestion.security.AppUserDetails;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.http.HttpStatus;

import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.util.Base64;
import java.util.List;

/**
 * Paginação keyset ("seek") no servidor, ordenada mais recente primeiro.
 * Escolhida em vez de OFFSET/LIMIT porque OFFSET força o Postgres a escanear
 * e descartar cada linha antes da página pedida — numa tabela de milhões de
 * linhas isso fica linearmente mais lento quanto mais fundo pagina. Trade-off:
 * cliente só anda pra frente/trás via cursor opaco, não pula pra uma página
 * arbitrária.
 */
@RestController
@RequestMapping("/api/transactions")
public class TransactionController {

    private static final int DEFAULT_LIMIT = 50;
    private static final int MAX_LIMIT = 500;

    private final TransactionRepository repository;

    public TransactionController(TransactionRepository repository) {
        this.repository = repository;
    }

    @GetMapping
    public TransactionPageResponse list(
            @RequestParam(required = false) String cursor,
            @RequestParam(required = false) Integer limit,
            @AuthenticationPrincipal AppUserDetails principal) {

        int effectiveLimit = normalizeLimit(limit);
        Cursor decoded = decodeCursor(cursor);

        List<TransactionRow> rows = repository.findPage(principal.getId(), decoded.date(), decoded.id(), effectiveLimit);

        boolean hasMore = rows.size() > effectiveLimit;
        List<TransactionRow> page = hasMore ? rows.subList(0, effectiveLimit) : rows;
        String nextCursor = hasMore ? encodeCursor(page.get(page.size() - 1)) : null;

        return new TransactionPageResponse(page, nextCursor, hasMore);
    }

    private int normalizeLimit(Integer requested) {
        if (requested == null || requested <= 0) {
            return DEFAULT_LIMIT;
        }
        return Math.min(requested, MAX_LIMIT);
    }

    private String encodeCursor(TransactionRow row) {
        String raw = row.transactionDate() + "_" + row.id();
        return Base64.getUrlEncoder().withoutPadding().encodeToString(raw.getBytes(StandardCharsets.UTF_8));
    }

    private Cursor decodeCursor(String cursor) {
        if (cursor == null || cursor.isBlank()) {
            return new Cursor(null, null);
        }
        try {
            String raw = new String(Base64.getUrlDecoder().decode(cursor), StandardCharsets.UTF_8);
            String[] parts = raw.split("_", 2);
            return new Cursor(LocalDate.parse(parts[0]), Long.parseLong(parts[1]));
        } catch (Exception e) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Invalid cursor");
        }
    }

    private record Cursor(LocalDate date, Long id) {
    }
}
