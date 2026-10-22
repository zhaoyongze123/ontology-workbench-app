#!/bin/sh
set -eu
source_dir=/run/secrets/app
runtime_dir=/run/ontology-config
install -d -o 10001 -g 10001 -m 0700 "$runtime_dir"
# 仅复制 API 所需的运行身份和公开 origin 配置；维护凭据从不挂载到此服务。
for key in spring.datasource.url spring.datasource.username spring.datasource.password \
  ontology.security.public-origin ontology.security.trusted-origins ontology.security.trusted-proxies \
  spring.flyway.enabled; do
  [ -r "$source_dir/$key" ] || { echo "required runtime config is missing: $key" >&2; exit 1; }
  install -o 10001 -g 10001 -m 0400 "$source_dir/$key" "$runtime_dir/$key"
done
exec gosu 10001:10001 java -XX:MaxRAMPercentage=75.0 -jar /opt/ontology/app.jar \
  --spring.profiles.active=multiuser \
  --spring.config.import=optional:configtree:/run/ontology-config/ "$@"
