# Ontology Workbench App Source

This source tree is the public, credential-free template for `0.2.0-rc.1`. `manifest.yaml` preserves the official App schema, the OpenAPI-derived business Action catalog, string-preserved decimal IDs, and Java broker OAuth fields. `oauth-clients.yaml` must remain empty; only the finalizer creates a private administrator copy after a real Java App client is configured.

Workbench App OAuth credentials and the Java server's upstream Workbench OIDC credentials have separate owners and protocol roles. Only the App `clientId` and `clientSecret` belong in the private root `oauth-clients.yaml`; the upstream OIDC credentials stay in server-side secret configuration. Do not place either secret in member files, source, examples, logs, or public release artifacts.

The login Action opens the deployed application login route `/ontology-management/login`; the iframe loads the application at `/ontology-management/`. OAuth authorization, token/refresh, and userinfo use the Java `/api/v1/oidc/app/*` endpoints. Userinfo returns stable Ontology fields `sub`, `id`, `username`, and `name`. OAuth grants are bounded by the verified login's eight-hour absolute lifetime and the registered scopes. OAuth scope intersects current project membership in Java API authorization.

Offline `status` and contract tests never claim that a remote connection is verified. A real Workbench upload must still confirm host acceptance, login and consent, current-user mapping, refresh rotation, revocation, Bearer injection, and two-user/project boundaries. Database restoration, migration, least-privilege roles, and rollback have independent acceptance records.
