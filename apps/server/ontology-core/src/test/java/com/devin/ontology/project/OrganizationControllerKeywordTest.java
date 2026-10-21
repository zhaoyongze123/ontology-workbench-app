package com.devin.ontology.project;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.devin.ontology.common.utils.r.PageResult;
import com.devin.ontology.controller.OrganizationController;
import com.devin.ontology.domain.vo.OrganizationQueryVO;
import com.devin.ontology.service.OrganizationService;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

class OrganizationControllerKeywordTest {
    @Test
    void forwardsProjectSearchKeywordIntoTheAuthorizedPagedQuery() {
        OrganizationService service = mock(OrganizationService.class);
        when(service.pageOrganization(any())).thenReturn(PageResult.of(2, 25, 0, List.of()));
        OrganizationController controller = new OrganizationController(service);

        controller.organizationPage(2, 25, "设备项目");

        ArgumentCaptor<OrganizationQueryVO> query = ArgumentCaptor.forClass(OrganizationQueryVO.class);
        verify(service).pageOrganization(query.capture());
        assertThat(query.getValue().getPageNum()).isEqualTo(2);
        assertThat(query.getValue().getPageSize()).isEqualTo(25);
        assertThat(query.getValue().getKeyword()).isEqualTo("设备项目");
    }
}
