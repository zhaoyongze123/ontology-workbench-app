package com.devin.ontology.identity;

import java.util.Optional;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

/** 身份存储的只读查询入口。 */
@Repository
public class IdentityRepository {
    private final JdbcTemplate jdbcTemplate;

    public IdentityRepository(final JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    public Optional<InternalUser> findByUsername(final String username) {
        return jdbcTemplate.query(
                        "SELECT id, username, display_name, system_admin, enabled, auth_version "
                                + "FROM ontology.og_user WHERE username = ?",
                        (rs, rowNum) -> new InternalUser(
                                rs.getString("id"), rs.getString("username"),
                                rs.getString("display_name"), rs.getBoolean("system_admin"),
                                rs.getBoolean("enabled"), rs.getLong("auth_version")),
                        username)
                .stream().findFirst();
    }

    public Optional<InternalUser> findById(final String id) {
        return jdbcTemplate.query(
                        "SELECT id, username, display_name, system_admin, enabled, auth_version "
                                + "FROM ontology.og_user WHERE id = ?",
                        (rs, rowNum) -> new InternalUser(
                                rs.getString("id"), rs.getString("username"),
                                rs.getString("display_name"), rs.getBoolean("system_admin"),
                                rs.getBoolean("enabled"), rs.getLong("auth_version")),
                        id)
                .stream().findFirst();
    }

    public Optional<String> findPasswordHash(final String userId) {
        return jdbcTemplate.query(
                        "SELECT password_hash FROM ontology.og_password_credential WHERE user_id = ?",
                        (rs, rowNum) -> rs.getString("password_hash"), userId)
                .stream().findFirst();
    }
}
