package com.devin.ontology.project;

import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.devin.ontology.dao.GraphDao;
import com.devin.ontology.dao.OrganizationDao;
import com.devin.ontology.domain.entity.Organization;
import com.devin.ontology.domain.vo.OrganizationQueryVO;
import com.devin.ontology.identity.InternalUser;
import com.devin.ontology.security.CurrentIdentity;
import com.devin.ontology.service.impl.OrganizationServiceImpl;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;

class OrganizationPageEmptyTest {
    @Test
    void emptyAuthorizedPageDoesNotQueryGraphs() {
        GraphDao graphDao = mock(GraphDao.class);
        OrganizationDao organizationDao = mock(OrganizationDao.class);
        CurrentIdentity currentIdentity = mock(CurrentIdentity.class);
        when(currentIdentity.requireUser()).thenReturn(
                new InternalUser("u1", "user", "User", false, true, 1));
        Page<Organization> emptyPage = new Page<>(1, 10, 0);
        emptyPage.setRecords(List.of());
        when(organizationDao.pageByQuery(any(OrganizationQueryVO.class), eq("u1"), eq(false)))
                .thenReturn(emptyPage);
        ProjectAccessService access = new ProjectAccessService(mock(JdbcTemplate.class), currentIdentity);
        OrganizationServiceImpl service = new OrganizationServiceImpl(graphDao, organizationDao, access);

        assertTrue(service.pageOrganization(new OrganizationQueryVO()).getRecords().isEmpty());
        verify(graphDao, never()).getByOrganizationIds(any());
    }
}
