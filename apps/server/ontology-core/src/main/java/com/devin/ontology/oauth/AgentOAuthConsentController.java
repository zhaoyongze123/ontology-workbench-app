package com.devin.ontology.oauth;

import jakarta.servlet.http.HttpServletRequest;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import org.springframework.context.annotation.Profile;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.web.csrf.CsrfToken;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

/** 显示明确的用户授权确认；客户端名称和 scope 都从服务端注册记录读取。 */
@RestController
@Profile("multiuser")
public class AgentOAuthConsentController {
    private final AgentClientRepository clients;

    public AgentOAuthConsentController(final AgentClientRepository clients) {
        this.clients = clients;
    }

    @GetMapping(value = "/oidc/app/consent", produces = MediaType.TEXT_HTML_VALUE)
    public ResponseEntity<String> consent(final HttpServletRequest request) {
        String clientId = request.getParameter("client_id");
        AgentClientRepository.AppClientView client = clients.viewByClientId(clientId);
        if (client == null || !client.enabled()) return ResponseEntity.notFound().build();

        List<String> requested = Arrays.stream(request.getParameterValues("scope") == null
                        ? new String[0] : request.getParameterValues("scope"))
                .flatMap(value -> Arrays.stream(value.trim().split("\\s+")))
                .filter(scope -> !scope.isBlank()).distinct().toList();
        if (!client.scopes().containsAll(requested)) return ResponseEntity.status(HttpStatus.BAD_REQUEST).body("Invalid scope");
        Object csrfValue = request.getAttribute(CsrfToken.class.getName());
        String csrf = csrfValue instanceof CsrfToken token ? token.getToken() : "";
        String state = request.getParameter("state");

        StringBuilder html = new StringBuilder("<!doctype html><html lang='zh-CN'><meta charset='utf-8'>"
                + "<meta name='viewport' content='width=device-width,initial-scale=1'><title>授权 Ontology</title>"
                + "<style>body{font:16px system-ui;margin:3rem auto;max-width:38rem;padding:0 1rem;color:#222}"
                + "main{border:1px solid #ddd;border-radius:12px;padding:2rem}label{display:block;padding:.6rem}"
                + "button{padding:.7rem 1rem;margin:.6rem .5rem 0 0}</style><main><h1>授权应用</h1><p>"
                + "应用 <strong>").append(escape(client.displayName())).append("</strong> 请求访问你的 Ontology 账户。</p>"
                + "<p>授权范围：</p><form method='post' action='/oidc/app/authorize'>"
                + "<input type='hidden' name='_csrf' value='").append(escape(csrf)).append("'>")
                .append("<input type='hidden' name='client_id' value='").append(escape(clientId)).append("'>")
                .append("<input type='hidden' name='state' value='").append(escape(state)).append("'>")
                .append("<input type='hidden' name='user_oauth_approval' value='true'>");
        for (String scope : requested) {
            html.append("<label><input type='checkbox' name='scope' value='").append(escape(scope))
                    .append("' checked> ").append(escape(scope)).append("</label>");
        }
        html.append("<button type='submit'>允许访问</button></form><form method='post' action='/oidc/app/authorize'>")
                .append("<input type='hidden' name='_csrf' value='").append(escape(csrf)).append("'>")
                .append("<input type='hidden' name='client_id' value='").append(escape(clientId)).append("'>")
                .append("<input type='hidden' name='state' value='").append(escape(state)).append("'>")
                .append("<input type='hidden' name='user_oauth_approval' value='false'>")
                .append("<button type='submit'>拒绝</button></form></main></html>");
        return ResponseEntity.ok().contentType(MediaType.TEXT_HTML).body(html.toString());
    }

    private String escape(final String input) {
        if (input == null) return "";
        return input.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;")
                .replace("\"", "&quot;").replace("'", "&#39;");
    }
}
