package com.ingestion.repository;

import com.ingestion.dto.TransactionRow;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import java.sql.Types;
import java.time.LocalDate;
import java.util.List;

@Repository
public class TransactionRepository {

    private static final String INSERT_SQL = """
            INSERT INTO transactions (id, transaction_date, category, amount, description, ingestion_job_id, owner_user_id)
            VALUES (?, ?, ?, ?, ?, ?, ?)
            ON CONFLICT (id, owner_user_id) DO NOTHING
            """;

    private final JdbcTemplate jdbcTemplate;

    public TransactionRepository(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    public int[][] batchInsert(List<TransactionRow> batch, long jobId, long ownerId) {
        return jdbcTemplate.batchUpdate(INSERT_SQL, batch, batch.size(), (ps, row) -> {
            ps.setLong(1, row.id());
            ps.setObject(2, row.transactionDate());
            ps.setString(3, row.category());
            ps.setBigDecimal(4, row.amount());
            if (row.description() != null) {
                ps.setString(5, row.description());
            } else {
                ps.setNull(5, Types.VARCHAR);
            }
            ps.setLong(6, jobId);
            ps.setLong(7, ownerId);
        });
    }

    private static final String FIRST_PAGE_SQL = """
            SELECT id, transaction_date, category, amount, description
            FROM transactions
            WHERE owner_user_id = ?
            ORDER BY transaction_date DESC, id DESC
            LIMIT ?
            """;

    private static final String NEXT_PAGE_SQL = """
            SELECT id, transaction_date, category, amount, description
            FROM transactions
            WHERE owner_user_id = ? AND (transaction_date, id) < (?, ?)
            ORDER BY transaction_date DESC, id DESC
            LIMIT ?
            """;

    public List<TransactionRow> findPage(long ownerId, LocalDate cursorDate, Long cursorId, int limit) {
        int fetchSize = limit + 1;
        if (cursorDate == null || cursorId == null) {
            return jdbcTemplate.query(FIRST_PAGE_SQL, this::mapRow, ownerId, fetchSize);
        }
        return jdbcTemplate.query(NEXT_PAGE_SQL, this::mapRow, ownerId, cursorDate, cursorId, fetchSize);
    }

    private TransactionRow mapRow(java.sql.ResultSet rs, int rowNum) throws java.sql.SQLException {
        return new TransactionRow(
                rs.getLong("id"),
                rs.getObject("transaction_date", LocalDate.class),
                rs.getString("category"),
                rs.getBigDecimal("amount"),
                rs.getString("description")
        );
    }
}
