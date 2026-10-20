package com.devin.ontology.project;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.devin.ontology.dao.OrganizationDao;
import com.devin.ontology.domain.vo.OrganizationQueryVO;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;

class OrganizationDaoAccessScopeTest {
    @SuppressWarnings("unchecked")
    @Test
    void appliesMemberScopeToCountAndPageSql() {
        JdbcTemplate jdbc = mock(JdbcTemplate.class);
        when(jdbc.queryForObject(anyString(), eq(Long.class), any(Object[].class))).thenReturn(2L);
        when(jdbc.query(anyString(), any(RowMapper.class), any(Object[].class))).thenReturn(List.of());
        OrganizationDao dao = new OrganizationDao(jdbc);
        OrganizationQueryVO query = new OrganizationQueryVO();
        query.setPageNum(2);
        query.setPageSize(5);

        dao.pageByQuery(query, "user-id", false);

        verify(jdbc).queryForObject(
                argThat(sql -> sql.contains("EXISTS (SELECT 1 FROM ontology.og_project_member")
                        && sql.contains("m.user_id = ?")),
                eq(Long.class), any(Object[].class));
        verify(jdbc).query(
                argThat(sql -> sql.contains("EXISTS (SELECT 1 FROM ontology.og_project_member")
                        && sql.contains("LIMIT ? OFFSET ?")),
                any(RowMapper.class), any(Object[].class));
    }
}
