package com.devin.ontology.project;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.devin.ontology.identity.InternalUser;
import com.devin.ontology.security.CurrentIdentity;
import java.math.BigInteger;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.security.access.AccessDeniedException;

class ProjectAccessServiceTest {
    private final JdbcTemplate jdbcTemplate = mock(JdbcTemplate.class);
    private final CurrentIdentity currentIdentity = mock(CurrentIdentity.class);
    private final ProjectAccessService service = new ProjectAccessService(jdbcTemplate, currentIdentity);
    private final BigInteger projectId = BigInteger.valueOf(12);

    @BeforeEach
    void setUp() {
        when(currentIdentity.requireUser()).thenReturn(
                new InternalUser("u1", "user", "User", false, true, 1));
    }

    @Test
    void appliesRoleCapabilityMatrix() {
        assertAllowed("OWNER", ProjectCapability.DELETE_PROJECT, true);
        assertAllowed("ADMIN", ProjectCapability.MANAGE_MEMBERS, true);
        assertAllowed("ADMIN", ProjectCapability.DELETE_PROJECT, false);
        assertAllowed("EDITOR", ProjectCapability.EDIT, true);
        assertAllowed("EDITOR", ProjectCapability.DELETE_GRAPH, false);
        assertAllowed("VIEWER", ProjectCapability.READ, true);
        assertAllowed("VIEWER", ProjectCapability.EDIT, false);
    }

    @Test
    void systemAdminCanUseEveryCapabilityOnAnExistingProject() {
        when(currentIdentity.requireUser()).thenReturn(
                new InternalUser("root", "root", "Root", true, true, 1));
        when(jdbcTemplate.queryForObject(any(String.class), eq(Integer.class), eq(projectId)))
                .thenReturn(1);
        for (ProjectCapability capability : ProjectCapability.values()) {
            assertDoesNotThrow(() -> service.requireProject(projectId, capability));
        }
        ProjectAccessView view = service.describeProject(projectId);
        assertEquals("SYSTEM_ADMIN", view.role());
        assertEquals(java.util.Arrays.stream(ProjectCapability.values()).map(ProjectCapability::name)
                        .collect(Collectors.toSet()),
                view.capabilities());
    }

    @Test
    void describesActualMemberRoleUsingTheAuthorizationMatrix() {
        when(jdbcTemplate.query(any(String.class), any(RowMapper.class), eq("u1"), eq(projectId)))
                .thenReturn(List.of("EDITOR"));

        ProjectAccessView view = service.describeProject(projectId);

        assertEquals("EDITOR", view.role());
        assertEquals(Set.of("READ", "EDIT"), view.capabilities());
    }

    @Test
    void removedMemberIsDeniedAndDisabledIdentityCannotUseProject() {
        when(jdbcTemplate.query(any(String.class), any(RowMapper.class), eq("u1"), eq(projectId)))
                .thenReturn(List.of());
        assertThrows(AccessDeniedException.class,
                () -> service.requireProject(projectId, ProjectCapability.READ));
        verify(jdbcTemplate).query(
                org.mockito.ArgumentMatchers.argThat(sql -> sql.contains("JOIN ontology.og_organization")
                        && !sql.contains("LEFT JOIN")),
                any(RowMapper.class), eq("u1"), eq(projectId));

        when(currentIdentity.requireUser()).thenReturn(
                new InternalUser("u1", "user", "User", false, false, 1));
        assertThrows(AccessDeniedException.class,
                () -> service.requireProject(projectId, ProjectCapability.READ));
    }

    private void assertAllowed(final String role, final ProjectCapability capability, final boolean expected) {
        when(jdbcTemplate.query(any(String.class), any(RowMapper.class), eq("u1"), eq(projectId)))
                .thenReturn(List.of(role));
        if (expected) {
            assertDoesNotThrow(() -> service.requireProject(projectId, capability));
        } else {
            assertThrows(AccessDeniedException.class,
                    () -> service.requireProject(projectId, capability));
        }
    }
}
