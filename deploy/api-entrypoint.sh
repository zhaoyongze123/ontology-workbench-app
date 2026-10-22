#!/bin/sh
set -eu
source_dir=/run/secrets/app
runtime_dir=/run/ontology-config
install -d -o 10001 -g 10001 -m 0700 "$runtime_dir"
# 仅复制 API 所需运行身份及可选 Workbench OIDC 客户端凭据；维护凭据不挂载到此服务。
for key in spring.datasource.url spring.datasource.username spring.datasource.password \
  ontology.security.public-origin ontology.security.trusted-origins ontology.security.trusted-proxies \
  spring.flyway.enabled graph.database.type graph.database.age.preloaded ontology.workbench.enabled \
  ontology.workbench.issuer ontology.workbench.authorization-endpoint ontology.workbench.token-endpoint \
  ontology.workbench.jwks-uri ontology.workbench.client-auth-method ontology.workbench.redirect-uri; do
  [ -r "$source_dir/$key" ] || { echo "required runtime config is missing: $key" >&2; exit 1; }
  install -o 10001 -g 10001 -m 0400 "$source_dir/$key" "$runtime_dir/$key"
done
for key in ontology.workbench.client-id ontology.workbench.client-secret ontology.workbench.scopes; do
  if [ -f "$source_dir/$key" ]; then
    install -o 10001 -g 10001 -m 0400 "$source_dir/$key" "$runtime_dir/$key"
  fi
done
exec gosu 10001:10001 java -XX:MaxRAMPercentage=75.0 -jar /opt/ontology/app.jar \
  --spring.profiles.active=multiuser \
  --spring.config.import=optional:configtree:/run/ontology-config/ "$@"
