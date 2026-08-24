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
            ON CONFLICT (id) DO NOTHING
            """;

    private final JdbcTemplate jdbcTemplate;

    public TransactionRepository(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    // Insere um lote de linhas já parseadas numa única viagem por batchUpdate.
    // Quem chama é responsável por manter o lote pequeno (ver
    // app.ingestion.batch-size) — esse método não guarda nada além do que recebe.
    //
    // ownerId vem denormalizado em cada linha (em vez de só um join com
    // ingestion_jobs) pra paginação e agregação filtrarem por owner como index
    // seek direto — ver idx_transactions_owner_date_id / idx_transactions_owner_agg.
    public void batchInsert(List<TransactionRow> batch, long jobId, long ownerId) {
        jdbcTemplate.batchUpdate(INSERT_SQL, batch, batch.size(), (ps, row) -> {
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

    // A comparação row-wise deixa o Postgres usar idx_transactions_owner_date_id
    // direto como seek, em vez de um scan por OFFSET que fica mais lento quanto
    // mais fundo pagina.
    private static final String NEXT_PAGE_SQL = """
            SELECT id, transaction_date, category, amount, description
            FROM transactions
            WHERE owner_user_id = ? AND (transaction_date, id) < (?, ?)
            ORDER BY transaction_date DESC, id DESC
            LIMIT ?
            """;

    // Paginação keyset: busca até limit + 1 linhas pra saber se tem mais página
    // sem precisar de um COUNT separado. Sempre filtrado por ownerId primeiro —
    // ver idx_transactions_owner_date_id.
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
