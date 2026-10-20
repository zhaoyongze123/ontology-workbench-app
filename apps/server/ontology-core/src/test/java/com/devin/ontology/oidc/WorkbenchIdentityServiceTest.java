package com.devin.ontology.oidc;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.devin.ontology.identity.InternalUser;
import java.util.Optional;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.transaction.TransactionStatus;
import org.springframework.transaction.support.TransactionCallback;
import org.springframework.transaction.support.TransactionOperations;

class WorkbenchIdentityServiceTest {
    private static final String ISSUER = "https://app.workbench.aos.work";

    @AfterEach
    void clearContext() {
        org.springframework.security.core.context.SecurityContextHolder.clearContext();
    }

    @Test
    void createsOrdinaryInternalUsersPerIssuerAndSubjectWithoutEmailMerging() {
        WorkbenchIdentityStore store = org.mockito.Mockito.mock(WorkbenchIdentityStore.class);
        TransactionOperations transactions = immediateTransactions();
        when(store.findByExternalIdentity(eq(ISSUER), any())).thenReturn(Optional.empty());
        WorkbenchIdentityService service = service(store, transactions);

        // 上游 email 相同也不能合并；映射只依赖 issuer + subject。
        InternalUser first = service.loginOrCreate(
                new VerifiedWorkbenchIdentity(ISSUER, "subject-a", "Same Name", "same@example.invalid"));
        InternalUser second = service.loginOrCreate(
                new VerifiedWorkbenchIdentity(ISSUER, "subject-b", "Same Name", "same@example.invalid"));

        assertNotEquals(first.id(), second.id());
        assertTrue(first.username().startsWith("wb_"));
        assertFalse(first.systemAdmin());
        assertTrue(first.enabled());
        assertEquals(0L, first.authVersion());
        verify(store).insertExternalIdentity(ISSUER, "subject-a", first.id());
        verify(store).insertExternalIdentity(ISSUER, "subject-b", second.id());
    }

    @Test
    void returnsMappedUserWithoutResettingExistingIdentity() {
        WorkbenchIdentityStore store = org.mockito.Mockito.mock(WorkbenchIdentityStore.class);
        InternalUser mapped = new InternalUser("internal-id", "existing", "Existing", true, true, 4);
        when(store.findByExternalIdentity(ISSUER, "subject")).thenReturn(Optional.of(mapped));

        InternalUser result = service(store, immediateTransactions())
                .loginOrCreate(new VerifiedWorkbenchIdentity(ISSUER, "subject", "Untrusted new display"));

        assertEquals(mapped, result);
        verify(store, never()).insertUser(any());
        verify(store, never()).insertExternalIdentity(any(), any(), any());
    }

    @Test
    void explicitBindRejectsIdentityAlreadyOwnedByAnotherInternalUser() {
        WorkbenchIdentityStore store = org.mockito.Mockito.mock(WorkbenchIdentityStore.class);
        InternalUser current = new InternalUser("current", "alice", "Alice", false, true, 3);
        when(store.findUserForUpdate("current")).thenReturn(Optional.of(current));
        when(store.findExternalOwner(ISSUER, "subject")).thenReturn(Optional.of("other-user"));

        assertThrows(WorkbenchIdentityExceptions.BindingConflict.class,
                () -> service(store, immediateTransactions()).bind(
                        new VerifiedWorkbenchIdentity(ISSUER, "subject", "Alice"), "current", 3));
        verify(store, never()).insertExternalIdentity(any(), any(), any());
    }

    @Test
    void explicitBindRejectsDisabledOrVersionChangedInternalUser() {
        WorkbenchIdentityStore store = org.mockito.Mockito.mock(WorkbenchIdentityStore.class);
        when(store.findUserForUpdate("current"))
                .thenReturn(Optional.of(new InternalUser("current", "alice", "Alice", false, false, 2)))
                .thenReturn(Optional.of(new InternalUser("current", "alice", "Alice", false, true, 3)));
        WorkbenchIdentityService service = service(store, immediateTransactions());
        VerifiedWorkbenchIdentity external = new VerifiedWorkbenchIdentity(ISSUER, "subject", "Alice");

        assertThrows(WorkbenchIdentityExceptions.StaleInternalUser.class, () -> service.bind(external, "current", 2));
        assertThrows(WorkbenchIdentityExceptions.StaleInternalUser.class, () -> service.bind(external, "current", 2));
        verify(store, never()).findExternalOwner(any(), any());
    }

    @Test
    void sameUserBindIsIdempotentAndDoesNotRewriteMapping() {
        WorkbenchIdentityStore store = org.mockito.Mockito.mock(WorkbenchIdentityStore.class);
        InternalUser current = new InternalUser("current", "alice", "Alice", false, true, 2);
        when(store.findUserForUpdate("current")).thenReturn(Optional.of(current));
        when(store.findExternalOwner(ISSUER, "subject")).thenReturn(Optional.of("current"));

        InternalUser result = service(store, immediateTransactions()).bind(
                new VerifiedWorkbenchIdentity(ISSUER, "subject", "Alice"), "current", 2);

        assertEquals(current, result);
        verify(store, never()).insertExternalIdentity(any(), any(), any());
    }

    @Test
    void rejectsIssuerMismatchAndInvalidSubjectsBeforeOpeningTransaction() {
        WorkbenchIdentityStore store = org.mockito.Mockito.mock(WorkbenchIdentityStore.class);
        TransactionOperations transactions = org.mockito.Mockito.mock(TransactionOperations.class);
        WorkbenchIdentityService service = service(store, transactions);

        assertThrows(IllegalArgumentException.class, () -> service.loginOrCreate(
                new VerifiedWorkbenchIdentity("https://attacker.invalid", "subject", "name")));
        assertThrows(IllegalArgumentException.class, () -> service.loginOrCreate(
                new VerifiedWorkbenchIdentity(ISSUER, "bad\nsubject", "name")));
        verify(transactions, never()).execute(any(TransactionCallback.class));
    }

    private static WorkbenchIdentityService service(final WorkbenchIdentityStore store,
            final TransactionOperations transactions) {
        WorkbenchOidcProperties properties = new WorkbenchOidcProperties();
        properties.setIssuer(ISSUER);
        return new WorkbenchIdentityService(store, transactions, properties);
    }

    @SuppressWarnings("unchecked")
    private static TransactionOperations immediateTransactions() {
        TransactionOperations transactions = org.mockito.Mockito.mock(TransactionOperations.class);
        try {
            when(transactions.execute(any(TransactionCallback.class))).thenAnswer(invocation ->
                    ((TransactionCallback<Object>) invocation.getArgument(0))
                            .doInTransaction(org.mockito.Mockito.mock(TransactionStatus.class)));
        } catch (RuntimeException exception) {
            throw exception;
        }
        return transactions;
    }
}
