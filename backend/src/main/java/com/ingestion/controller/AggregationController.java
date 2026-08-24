package com.ingestion.controller;

import com.ingestion.dto.CategoryMonthAggregate;
import com.ingestion.dto.SummaryResponse;
import com.ingestion.repository.AggregationRepository;
import com.ingestion.security.AppUserDetails;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDate;
import java.util.List;

@Tag(name = "Agregações", description = "Totalizações e resumos das transações do usuário")
@RestController
@RequestMapping("/api/aggregations")
public class AggregationController {

    private final AggregationRepository repository;

    public AggregationController(AggregationRepository repository) {
        this.repository = repository;
    }

    @Operation(summary = "Totais por categoria e mês",
            description = "Retorna o total de transações agrupado por categoria e mês. Filtros de data são opcionais.")
    @GetMapping("/by-category-month")
    public List<CategoryMonthAggregate> byCategoryMonth(
            @Parameter(description = "Data inicial do filtro (ISO 8601: yyyy-MM-dd)", example = "2024-01-01")
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @Parameter(description = "Data final do filtro (ISO 8601: yyyy-MM-dd)", example = "2024-12-31")
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to,
            @AuthenticationPrincipal AppUserDetails principal) {
        return repository.byCategoryAndMonth(principal.getId(), from, to);
    }

    @Operation(summary = "Resumo geral",
            description = "Retorna contagem total de transações, volume financeiro, número de categorias distintas e intervalo de datas.")
    @GetMapping("/summary")
    public SummaryResponse summary(@AuthenticationPrincipal AppUserDetails principal) {
        return repository.summary(principal.getId());
    }
}
