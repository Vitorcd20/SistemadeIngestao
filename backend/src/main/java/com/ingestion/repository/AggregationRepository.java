package com.ingestion.repository;

import com.ingestion.dto.CategoryMonthAggregate;
import com.ingestion.dto.SummaryResponse;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

@Repository
public class AggregationRepository {

    // idx_transactions_owner_agg lidera com owner_user_id (sempre presente),
    // depois transaction_date pro filtro de range opcional, com category e
    // amount (via INCLUDE) cobrindo o resto — o Postgres serve isso como Index
    // Only Scan (0 heap fetches) com ou sem from/to, já que owner_user_id
    // sozinho já restringe pras linhas de um usuário.
    private static final String BY_CATEGORY_MONTH_SQL = """
            SELECT category,
                   to_char(date_trunc('month', transaction_date), 'YYYY-MM') AS month,
                   SUM(amount) AS total_amount,
                   COUNT(*) AS txn_count
            FROM transactions
            WHERE owner_user_id = ?
            %s
            GROUP BY category, date_trunc('month', transaction_date)
            ORDER BY date_trunc('month', transaction_date), category
            """;

    private static final String SUMMARY_SQL = """
            SELECT COUNT(*) AS total_transactions,
                   COALESCE(SUM(amount), 0) AS total_volume,
                   COUNT(DISTINCT category) AS distinct_categories,
                   MIN(transaction_date) AS earliest_date,
                   MAX(transaction_date) AS latest_date
            FROM transactions
            WHERE owner_user_id = ?
            """;

    private final JdbcTemplate jdbcTemplate;

    public AggregationRepository(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    public List<CategoryMonthAggregate> byCategoryAndMonth(long ownerId, LocalDate from, LocalDate to) {
        List<Object> args = new ArrayList<>();
        args.add(ownerId);
        StringBuilder extra = new StringBuilder();
        if (from != null) {
            extra.append("AND transaction_date >= ? ");
            args.add(from);
        }
        if (to != null) {
            extra.append("AND transaction_date <= ? ");
            args.add(to);
        }

        String sql = BY_CATEGORY_MONTH_SQL.formatted(extra);
        return jdbcTemplate.query(sql, (rs, rowNum) -> new CategoryMonthAggregate(
                rs.getString("category"),
                rs.getString("month"),
                rs.getBigDecimal("total_amount"),
                rs.getLong("txn_count")
        ), args.toArray());
    }

    public SummaryResponse summary(long ownerId) {
        return jdbcTemplate.queryForObject(SUMMARY_SQL, (rs, rowNum) -> new SummaryResponse(
                rs.getLong("total_transactions"),
                rs.getBigDecimal("total_volume"),
                rs.getLong("distinct_categories"),
                rs.getObject("earliest_date", LocalDate.class),
                rs.getObject("latest_date", LocalDate.class)
        ), ownerId);
    }
}
