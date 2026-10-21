package com.devin.ontology.identity;

import com.devin.ontology.common.utils.IdGenerator;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.nio.file.attribute.PosixFilePermission;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.nio.charset.StandardCharsets;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.context.annotation.Profile;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;

/** 仅供显式受控命令调用的一次性身份和成员初始化服务。 */
@Service
@Profile("bootstrap")
public class BootstrapService {
    private static final String SEED_KEY = "multiuser-bootstrap-v1";
    private static final long BOOTSTRAP_LOCK_KEY = 7_194_563_108_322_024L;
    private static final long MAX_INPUT_BYTES = 1_048_576L;
    private static final Set<PosixFilePermission> PRIVATE_DIR_PERMISSIONS = Set.of(
            PosixFilePermission.OWNER_READ, PosixFilePermission.OWNER_WRITE, PosixFilePermission.OWNER_EXECUTE);
    private static final Set<PosixFilePermission> PRIVATE_FILE_PERMISSIONS = Set.of(
            PosixFilePermission.OWNER_READ, PosixFilePermission.OWNER_WRITE);

    private final JdbcTemplate jdbcTemplate;
    private final TransactionTemplate transactionTemplate;
    private final PasswordEncoder passwordEncoder;
    private final ObjectMapper objectMapper;

    public BootstrapService(
            final JdbcTemplate jdbcTemplate,
            final TransactionTemplate transactionTemplate,
            final PasswordEncoder passwordEncoder,
            final ObjectMapper objectMapper) {
        this.jdbcTemplate = jdbcTemplate;
        this.transactionTemplate = transactionTemplate;
        this.passwordEncoder = passwordEncoder;
        this.objectMapper = objectMapper;
    }

    /** 从本机私有 JSON 读取待执行输入；原文和密码不会写入日志或审计表。 */
    public BootstrapResult apply(final Path inputFile) {
        final byte[] bytes;
        final BootstrapInput input;
        try {
            validatePrivateInputFile(inputFile);
            bytes = Files.readAllBytes(inputFile);
            input = objectMapper.readValue(bytes, BootstrapInput.class);
        } catch (IOException ignored) {
            // Jackson 解析异常可能包含非法 JSON 片段；不附带原始异常以免把口令写入日志。
            throw new IllegalStateException("无法读取或解析 bootstrap 输入文件");
        }
        validate(input);
        final String digest = sha256(bytes);
        return transactionTemplate.execute(status -> applyInTransaction(input, digest));
    }

    private BootstrapResult applyInTransaction(final BootstrapInput input, final String digest) {
        // 固定事务级 advisory lock 串行化首次执行，避免两个进程同时观察到空 ledger。
        jdbcTemplate.execute("SELECT pg_advisory_xact_lock(" + BOOTSTRAP_LOCK_KEY + ")");
        List<String> completed = jdbcTemplate.query(
                "SELECT input_digest FROM ontology.og_bootstrap_run WHERE seed_key = ?",
                (rs, rowNum) -> rs.getString(1), SEED_KEY);
        if (!completed.isEmpty()) {
            if (!MessageDigest.isEqual(completed.get(0).getBytes(StandardCharsets.US_ASCII),
                    digest.getBytes(StandardCharsets.US_ASCII))) {
                throw new IllegalStateException("bootstrap 输入与已完成记录不一致，需人工复核");
            }
            verifyAppliedState(input, readLegacyProjectIds());
            return new BootstrapResult(false, input.users().size(), input.testProjects().size());
        }

        rejectExistingIdentityConflicts(input);
        // Bootstrap 是停服维护命令；上面的事务级 advisory lock 已串行化初始化，无需给维护角色行更新权限。
        List<Long> legacyProjectIds = jdbcTemplate.query(
                "SELECT id FROM ontology.og_organization o "
                        + "WHERE o.bootstrap_key IS NULL AND NOT EXISTS ("
                        + "SELECT 1 FROM ontology.og_project_member m "
                        + "WHERE m.project_id = o.id AND m.role = 'OWNER') ORDER BY o.id",
                (rs, rowNum) -> rs.getLong(1));
        String adminId = insertUsers(input.users());
        for (Long projectId : legacyProjectIds) {
            jdbcTemplate.update(
                    "INSERT INTO ontology.og_project_member(project_id, user_id, role) VALUES (?, ?, 'OWNER')",
                    projectId, adminId);
        }

        for (BootstrapProject project : input.testProjects()) {
            long projectId = IdGenerator.generateKey().longValueExact();
            jdbcTemplate.update(
                    "INSERT INTO ontology.og_organization(id, name, description, bootstrap_key) "
                            + "VALUES (?, ?, ?, ?)",
                    projectId, project.name(), "受控 bootstrap 初始化项目", project.key());
        }

        for (BootstrapUser user : input.users()) {
            String userId = userId(user.username());
            for (BootstrapMembership membership : user.memberships()) {
                Long projectId = jdbcTemplate.queryForObject(
                        "SELECT id FROM ontology.og_organization WHERE bootstrap_key = ?",
                        Long.class, membership.projectKey());
                jdbcTemplate.update(
                        "INSERT INTO ontology.og_project_member(project_id, user_id, role) "
                                + "VALUES (?, ?, ?)",
                        projectId, userId, membership.role().toUpperCase(Locale.ROOT));
            }
        }

        jdbcTemplate.update(
                "INSERT INTO ontology.og_audit_event(event_type, actor_user_id, details_json) "
                        + "VALUES (?, ?, ?::jsonb)",
                "IDENTITY_BOOTSTRAP_APPLIED", adminId,
                "{\"seedKey\":\"" + SEED_KEY + "\",\"users\":" + input.users().size()
                        + ",\"projects\":" + input.testProjects().size() + "}");
        jdbcTemplate.update(
                "INSERT INTO ontology.og_bootstrap_run(seed_key, input_digest, completed_at, details_json) "
                        + "VALUES (?, ?, ?, ?::jsonb)",
                SEED_KEY, digest, Timestamp.from(Instant.now()),
                objectMapper.valueToTree(Map.of("legacyProjectIds", legacyProjectIds)).toString());
        return new BootstrapResult(true, input.users().size(), input.testProjects().size());
    }

    private String insertUsers(final List<BootstrapUser> users) {
        String adminId = null;
        for (BootstrapUser user : users) {
            String id = java.util.UUID.randomUUID().toString();
            jdbcTemplate.update(
                    "INSERT INTO ontology.og_user(id, username, display_name, system_admin, enabled, auth_version) "
                            + "VALUES (?, ?, ?, ?, TRUE, 0)",
                    id, user.username(), user.displayName(), user.systemAdmin());
            jdbcTemplate.update(
                    "INSERT INTO ontology.og_password_credential(user_id, password_hash) VALUES (?, ?)",
                    id, passwordEncoder.encode(user.password()));
            if (user.systemAdmin()) {
                adminId = id;
            }
        }
        if (adminId == null) {
            throw new IllegalArgumentException("必须且只能初始化一个系统管理员");
        }
        return adminId;
    }

    private String userId(final String username) {
        return jdbcTemplate.queryForObject(
                "SELECT id FROM ontology.og_user WHERE username = ?", String.class, username);
    }

    private void rejectExistingIdentityConflicts(final BootstrapInput input) {
        // 聚合显式引用受限角色获准读取的列，避免为冲突检测授予整表 SELECT。
        Integer foreignOwnerCount = jdbcTemplate.queryForObject(
                "SELECT COUNT(o.id) FROM ontology.og_organization o "
                        + "JOIN ontology.og_project_member m ON m.project_id = o.id "
                        + "WHERE o.bootstrap_key IS NULL AND m.role = 'OWNER'",
                Integer.class);
        if (foreignOwnerCount != null && foreignOwnerCount > 0) {
            throw new IllegalStateException("旧项目已有 OWNER，需人工复核；未授予或覆盖归属");
        }
        for (BootstrapUser user : input.users()) {
            Integer count = jdbcTemplate.queryForObject(
                    "SELECT COUNT(username) FROM ontology.og_user WHERE username = ?", Integer.class,
                    user.username());
            if (count != null && count > 0) {
                throw new IllegalStateException("已有身份冲突，需人工复核: " + user.username());
            }
        }
        for (BootstrapProject project : input.testProjects()) {
            Integer count = jdbcTemplate.queryForObject(
                    "SELECT COUNT(bootstrap_key) FROM ontology.og_organization WHERE bootstrap_key = ?",
                    Integer.class, project.key());
            if (count != null && count > 0) {
                throw new IllegalStateException("已有项目身份冲突，需人工复核: " + project.key());
            }
        }
    }

    /** 重复运行只读核验已落库身份、项目和成员；不更新任何密码或权限。 */
    private void verifyAppliedState(final BootstrapInput input, final List<Long> legacyProjectIds) {
        for (BootstrapUser user : input.users()) {
            Integer userCount = jdbcTemplate.queryForObject(
                "SELECT COUNT(c.user_id) FROM ontology.og_user u "
                            + "JOIN ontology.og_password_credential c ON c.user_id = u.id "
                            + "WHERE u.username = ?",
                    Integer.class, user.username());
            if (userCount == null || userCount != 1) {
                throw new IllegalStateException("已初始化用户或凭证不存在，需人工复核: " + user.username());
            }
        }
        for (BootstrapProject project : input.testProjects()) {
            Integer projectCount = jdbcTemplate.queryForObject(
                    "SELECT COUNT(bootstrap_key) FROM ontology.og_organization WHERE bootstrap_key = ?",
                    Integer.class, project.key());
            if (projectCount == null || projectCount != 1) {
                throw new IllegalStateException("已初始化项目不存在，需人工复核: " + project.key());
            }
        }
        for (Long projectId : legacyProjectIds) {
            Integer projectCount = jdbcTemplate.queryForObject(
                    "SELECT COUNT(id) FROM ontology.og_organization WHERE id = ?", Integer.class, projectId);
            if (projectCount == null || projectCount != 1) {
                throw new IllegalStateException("初始化时记录的旧项目不存在，需人工复核: " + projectId);
            }
        }
    }

    private List<Long> readLegacyProjectIds() {
        return jdbcTemplate.query(
                "SELECT jsonb_array_elements_text(details_json->'legacyProjectIds') "
                        + "FROM ontology.og_bootstrap_run WHERE seed_key = ?",
                (rs, rowNum) -> Long.parseLong(rs.getString(1)), SEED_KEY);
    }

    static void validate(final BootstrapInput input) {
        if (input == null || input.schemaVersion() != 1
                || !"local-development".equals(input.environment())
                || !"username_as_password_for_local_tests".equals(input.initialPasswordPolicy())
                || !"not_applied".equals(input.executionStatus())
                || input.applyAutomatically() || input.publicBootstrapEnabled()
                || !input.neverResetExistingPasswords()
                || !"fail_and_review".equals(input.onExistingIdentityConflict())
                || !"admin".equals(input.legacyProjectOwnerUsername())) {
            throw new IllegalArgumentException("bootstrap 输入开关或版本不符合受控初始化契约");
        }
        if (input.users() == null || input.users().size() != 7
                || input.testProjects() == null || input.testProjects().size() != 2) {
            throw new IllegalArgumentException("bootstrap 输入必须包含 7 个用户和 2 个测试项目");
        }
        Set<String> usernames = new HashSet<>();
        Set<String> projectKeys = new HashSet<>();
        Set<String> memberships = new HashSet<>();
        Set<String> owners = new HashSet<>();
        int admins = 0;
        boolean designatedAdminIsSystemAdmin = false;
        for (BootstrapUser user : input.users()) {
            if (user == null || blank(user.username()) || blank(user.displayName())
                    || blank(user.password()) || user.memberships() == null
                    || !usernames.add(user.username())) {
                throw new IllegalArgumentException("用户数据缺失或用户名重复");
            }
            if (!user.username().equals(user.password())) {
                throw new IllegalArgumentException("本地测试初始化要求密码与用户名相同");
            }
            if (user.systemAdmin()) {
                admins++;
                designatedAdminIsSystemAdmin = "admin".equals(user.username());
            }
        }
        if (admins != 1 || !usernames.contains("admin") || !designatedAdminIsSystemAdmin) {
            throw new IllegalArgumentException("必须包含唯一系统管理员 admin");
        }
        for (BootstrapProject project : input.testProjects()) {
            if (project == null || blank(project.key()) || blank(project.name())
                    || blank(project.ownerUsername()) || !usernames.contains(project.ownerUsername())
                    || !projectKeys.add(project.key())) {
                throw new IllegalArgumentException("测试项目数据缺失、重复或所有者无效");
            }
        }
        for (BootstrapUser user : input.users()) {
            for (BootstrapMembership membership : user.memberships()) {
                if (membership == null || !projectKeys.contains(membership.projectKey())
                        || membership.role() == null
                        || !Set.of("OWNER", "ADMIN", "EDITOR", "VIEWER").contains(membership.role())
                        || !memberships.add(membership.projectKey() + "\u0000" + user.username())) {
                    throw new IllegalArgumentException("成员关系无效或重复");
                }
                if ("OWNER".equals(membership.role()) && !owners.add(membership.projectKey())) {
                    throw new IllegalArgumentException("每个测试项目只能有一个 OWNER");
                }
            }
        }
        for (BootstrapProject project : input.testProjects()) {
            BootstrapUser owner = input.users().stream()
                    .filter(user -> user.username().equals(project.ownerUsername()))
                    .findFirst().orElseThrow();
            boolean ownerMembership = owner.memberships().stream().anyMatch(membership ->
                    project.key().equals(membership.projectKey()) && "OWNER".equals(membership.role()));
            if (!ownerMembership || !owners.contains(project.key())) {
                throw new IllegalArgumentException("项目 ownerUsername 必须与 OWNER 成员关系一致");
            }
        }
    }

    private static boolean blank(final String value) {
        return value == null || value.isBlank();
    }

    private static void validatePrivateInputFile(final Path inputFile) throws IOException {
        if (inputFile == null || Files.isSymbolicLink(inputFile)
                || !Files.isRegularFile(inputFile, LinkOption.NOFOLLOW_LINKS)
                || Files.size(inputFile) > MAX_INPUT_BYTES) {
            throw new IllegalArgumentException("bootstrap 输入必须是至多 1 MiB 的普通文件");
        }
        Path parent = inputFile.toAbsolutePath().normalize().getParent();
        try {
            if (parent == null || !Files.getPosixFilePermissions(parent).equals(PRIVATE_DIR_PERMISSIONS)
                    || !Files.getPosixFilePermissions(inputFile, LinkOption.NOFOLLOW_LINKS)
                            .equals(PRIVATE_FILE_PERMISSIONS)) {
                throw new IllegalArgumentException("bootstrap 输入目录/文件权限必须分别为 0700/0600");
            }
        } catch (UnsupportedOperationException exception) {
            throw new IllegalArgumentException("bootstrap 输入要求支持 POSIX 权限的本机文件系统", exception);
        }
    }

    private static String sha256(final byte[] value) {
        try {
            byte[] hash = MessageDigest.getInstance("SHA-256").digest(value);
            return java.util.HexFormat.of().formatHex(hash);
        } catch (NoSuchAlgorithmException impossible) {
            throw new IllegalStateException("SHA-256 不可用", impossible);
        }
    }

    public record BootstrapResult(boolean applied, int userCount, int projectCount) { }
    @JsonIgnoreProperties(ignoreUnknown = true)
    public record BootstrapInput(
            int schemaVersion, String environment, String initialPasswordPolicy,
            String executionStatus, boolean applyAutomatically,
            boolean publicBootstrapEnabled, boolean neverResetExistingPasswords,
            String onExistingIdentityConflict, String legacyProjectOwnerUsername,
            List<BootstrapUser> users, List<BootstrapProject> testProjects) { }
    public record BootstrapUser(
            String username, String displayName, String password, boolean systemAdmin,
            List<BootstrapMembership> memberships) { }
    public record BootstrapMembership(String projectKey, String role) { }
    public record BootstrapProject(String key, String name, String ownerUsername) { }
}
