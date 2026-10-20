package com.devin.ontology.project;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.devin.ontology.identity.InternalUser;
import org.junit.jupiter.api.Test;

class ProjectMembershipPolicyTest {
    @Test
    void ownerAndAdminHaveDifferentMemberManagementScopes() {
        InternalUser owner = user(false);
        InternalUser admin = user(false);
        InternalUser systemAdmin = user(true);

        assertTrue(ProjectMembershipService.canManage(owner, ProjectRole.OWNER, ProjectRole.ADMIN, ProjectRole.VIEWER));
        assertFalse(ProjectMembershipService.canManage(owner, ProjectRole.OWNER, ProjectRole.OWNER, ProjectRole.ADMIN));
        assertTrue(ProjectMembershipService.canManage(admin, ProjectRole.ADMIN, ProjectRole.EDITOR, ProjectRole.VIEWER));
        assertFalse(ProjectMembershipService.canManage(admin, ProjectRole.ADMIN, ProjectRole.ADMIN, ProjectRole.VIEWER));
        assertFalse(ProjectMembershipService.canManage(admin, ProjectRole.ADMIN, ProjectRole.EDITOR, ProjectRole.ADMIN));
        assertTrue(ProjectMembershipService.canManage(systemAdmin, null, ProjectRole.ADMIN, ProjectRole.VIEWER));
        assertFalse(ProjectMembershipService.canManage(systemAdmin, null, ProjectRole.OWNER, ProjectRole.ADMIN));
    }

    @Test
    void searchEscapesLikeWildcardsAndEscapeCharacter() {
        assertEquals("a\\%b\\_c\\\\d", ProjectMembershipService.escapeLike("a%b_c\\d"));
    }

    private static InternalUser user(final boolean systemAdmin) {
        return new InternalUser("u", "user", "User", systemAdmin, true, 1);
    }
}
