package com.devin.ontology.oauth;

import java.util.List;
import org.springframework.http.CacheControl;
import org.springframework.http.ResponseEntity;
import org.springframework.context.annotation.Profile;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** OAuth 客户端 Secret 只在创建响应中返回，列表接口永不返回摘要或原值。 */
@RestController
@Profile("multiuser")
@RequestMapping("/admin/oauth/clients")
public class AppClientAdminController {
    private final AppClientManagementService service;

    public AppClientAdminController(final AppClientManagementService service) {
        this.service = service;
    }

    @GetMapping
    public List<AgentClientRepository.AppClientView> list() {
        return service.list();
    }

    @PostMapping
    public ResponseEntity<AgentClientRepository.CreatedAppClient> create(
            @RequestBody final AppClientManagementService.CreateClientRequest request) {
        return ResponseEntity.ok().cacheControl(CacheControl.noStore()).header("Pragma", "no-cache")
                .body(service.create(request));
    }

    @DeleteMapping("/{clientRecordId}")
    public java.util.Map<String, String> disable(@PathVariable final String clientRecordId) {
        service.disable(clientRecordId);
        return java.util.Map.of("status", "disabled");
    }
}
