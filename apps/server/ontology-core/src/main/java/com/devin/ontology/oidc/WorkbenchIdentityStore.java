package com.devin.ontology.oidc;

import com.devin.ontology.identity.InternalUser;
import java.util.Optional;

/** OIDC 绑定事务使用的最小持久化契约。 */
interface WorkbenchIdentityStore {
    void lockExternalIdentity(String issuer, String subject);
    Optional<InternalUser> findByExternalIdentity(String issuer, String subject);
    Optional<InternalUser> findUserForUpdate(String userId);
    Optional<String> findExternalOwner(String issuer, String subject);
    void insertUser(InternalUser user);
    void insertExternalIdentity(String issuer, String subject, String userId);
    void audit(String eventType, String actorUserId, String issuer, String operation);
}
