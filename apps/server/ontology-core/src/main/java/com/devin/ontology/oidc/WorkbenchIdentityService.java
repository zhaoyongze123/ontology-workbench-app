package com.devin.ontology.oidc;

import com.devin.ontology.identity.InternalUser;
import java.util.Objects;
import java.util.UUID;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionOperations;

/** 在已验证的 (issuer, subject) 与内部用户之间建立唯一映射。 */
@Service
@ConditionalOnProperty(prefix = "ontology.workbench", name = "enabled", havingValue = "true")
public class WorkbenchIdentityService {
    private final WorkbenchIdentityStore store;
    private final TransactionOperations transactions;
    private final String trustedIssuer;

    public WorkbenchIdentityService(final WorkbenchIdentityStore store,
            final TransactionOperations transactions, final WorkbenchOidcProperties properties) {
        this.store = store;
        this.transactions = transactions;
        this.trustedIssuer = properties.getIssuer();
    }

    /** 首次登录自动建普通账号；绝不使用 email 作为合并条件。 */
    public InternalUser loginOrCreate(final VerifiedWorkbenchIdentity identity) {
        validate(identity);
        return Objects.requireNonNull(transactions.execute(status -> {
            store.lockExternalIdentity(identity.issuer(), identity.subject());
            InternalUser existing = store.findByExternalIdentity(identity.issuer(), identity.subject()).orElse(null);
            if (existing != null) {
                if (!existing.enabled()) throw new WorkbenchIdentityExceptions.DisabledUser();
                store.audit("WORKBENCH_OIDC_LOGIN", existing.id(), identity.issuer(), "login");
                return existing;
            }

            String id = UUID.randomUUID().toString();
            InternalUser created = new InternalUser(id, "wb_" + id,
                    displayName(identity.displayName()), false, true, 0L);
            store.insertUser(created);
            store.insertExternalIdentity(identity.issuer(), identity.subject(), id);
            store.audit("WORKBENCH_OIDC_USER_CREATED", id, identity.issuer(), "first_login");
            return created;
        }));
    }

    /** 显式绑定必须携带当前内部会话的用户版本快照。 */
    public InternalUser bind(final VerifiedWorkbenchIdentity identity,
            final String userId, final long expectedAuthVersion) {
        validate(identity);
        if (userId == null || userId.isBlank() || expectedAuthVersion < 0) {
            throw new WorkbenchIdentityExceptions.StaleInternalUser();
        }
        return Objects.requireNonNull(transactions.execute(status -> {
            store.lockExternalIdentity(identity.issuer(), identity.subject());
            InternalUser internal = store.findUserForUpdate(userId)
                    .filter(InternalUser::enabled)
                    .filter(user -> user.authVersion() == expectedAuthVersion)
                    .orElseThrow(WorkbenchIdentityExceptions.StaleInternalUser::new);
            String owner = store.findExternalOwner(identity.issuer(), identity.subject()).orElse(null);
            if (owner != null && !owner.equals(userId)) {
                throw new WorkbenchIdentityExceptions.BindingConflict();
            }
            if (owner == null) {
                store.insertExternalIdentity(identity.issuer(), identity.subject(), userId);
            }
            store.audit("WORKBENCH_OIDC_BOUND", userId, identity.issuer(), "bind");
            return internal;
        }));
    }

    private void validate(final VerifiedWorkbenchIdentity identity) {
        if (identity == null || trustedIssuer == null || !trustedIssuer.equals(identity.issuer())
                || !hasSafeValue(identity.subject(), 500)) {
            throw new IllegalArgumentException("Workbench 身份声明无效");
        }
    }

    private static boolean hasSafeValue(final String value, final int maxLength) {
        return value != null && !value.isBlank() && value.length() <= maxLength
                && value.chars().noneMatch(Character::isISOControl);
    }

    private static String displayName(final String value) {
        if (value == null || value.isBlank()) return "Workbench 用户";
        String safe = value.strip().replaceAll("[\\p{Cntrl}]", "");
        if (safe.isEmpty()) return "Workbench 用户";
        return safe.length() <= 200 ? safe : safe.substring(0, 200);
    }
}
