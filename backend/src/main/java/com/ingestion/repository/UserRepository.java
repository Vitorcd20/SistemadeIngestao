package com.ingestion.repository;

import com.ingestion.security.AppUserDetails;
import org.springframework.dao.EmptyResultDataAccessException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import java.util.Locale;
import java.util.Objects;
import java.util.Optional;

/**
 * Username é normalizado (trim, lowercase) só aqui — assim toda chamada,
 * lookup ou insert, casa de forma consistente sem precisar lembrar de
 * normalizar em cada call site.
 */
@Repository
public class UserRepository {

    private final JdbcTemplate jdbcTemplate;

    public UserRepository(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    public Optional<AppUserDetails> findByUsername(String username) {
        try {
            return Optional.of(jdbcTemplate.queryForObject("""
                    SELECT id, username, password_hash FROM users WHERE username = ?
                    """, (rs, rowNum) -> new AppUserDetails(
                            rs.getLong("id"), rs.getString("username"), rs.getString("password_hash")),
                    normalize(username)));
        } catch (EmptyResultDataAccessException e) {
            return Optional.empty();
        }
    }

    public boolean existsByUsername(String username) {
        Boolean exists = jdbcTemplate.queryForObject(
                "SELECT EXISTS(SELECT 1 FROM users WHERE username = ?)", Boolean.class, normalize(username));
        return Boolean.TRUE.equals(exists);
    }

    public long createUser(String username, String passwordHash) {
        Long id = jdbcTemplate.queryForObject("""
                INSERT INTO users (username, password_hash) VALUES (?, ?) RETURNING id
                """, Long.class, normalize(username), passwordHash);
        return Objects.requireNonNull(id);
    }

    private static String normalize(String username) {
        return username == null ? "" : username.trim().toLowerCase(Locale.ROOT);
    }
}
