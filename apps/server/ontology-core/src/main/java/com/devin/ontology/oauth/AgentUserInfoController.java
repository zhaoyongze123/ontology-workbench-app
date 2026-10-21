package com.devin.ontology.oauth;

import com.devin.ontology.identity.InternalUser;
import com.devin.ontology.security.CurrentIdentity;
import java.util.Map;
import org.springframework.context.annotation.Profile;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

/** App 身份接口只返回 Ontology 稳定内部身份，不透传 Workbench 上游 Token 或邮箱。 */
@RestController
@Profile("multiuser")
public class AgentUserInfoController {
    private final CurrentIdentity currentIdentity;

    public AgentUserInfoController(final CurrentIdentity currentIdentity) {
        this.currentIdentity = currentIdentity;
    }

    @GetMapping("/oidc/app/userinfo")
    public Map<String, String> userInfo() {
        InternalUser user = currentIdentity.requireUser();
        return Map.of("sub", user.id(), "id", user.id(), "username", user.username(), "name", user.displayName());
    }
}
