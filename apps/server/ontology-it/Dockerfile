# 官方 PostgreSQL 16.15 Debian Bookworm 多架构索引 digest；AGE 固定官方 PG16/v1.6.0-rc0 测试标签和提交，pgvector 固定 v0.8.1 正式标签和提交。
FROM postgres:16.15-bookworm@sha256:0ea6700a3b4f0ae6ce746519073558aed4d88a79d8d07622a9a644946c7319c4

ARG AGE_TAG=PG16/v1.6.0-rc0
ARG AGE_COMMIT=2db2f06
ARG PGVECTOR_TAG=v0.8.1
ARG PGVECTOR_COMMIT=778dacf20c07caf904557a88705142631818d8cb

RUN apt-get update \
    && apt-get install -y --no-install-recommends build-essential cmake flex bison \
       postgresql-server-dev-16 libpq-dev ca-certificates git \
    && rm -rf /var/lib/apt/lists/*

# 使用 Apache AGE 与 pgvector 官方仓库的 PostgreSQL 16 发布标签。
RUN git clone --depth 1 --branch "${AGE_TAG}" https://github.com/apache/age.git /tmp/age \
    && test "$(git -C /tmp/age rev-parse --short=7 HEAD)" = "${AGE_COMMIT}" \
    && make -C /tmp/age PG_CONFIG=/usr/lib/postgresql/16/bin/pg_config install \
    && rm -rf /tmp/age \
    && git clone --depth 1 --branch "${PGVECTOR_TAG}" https://github.com/pgvector/pgvector.git /tmp/pgvector \
    && test "$(git -C /tmp/pgvector rev-parse HEAD)" = "${PGVECTOR_COMMIT}" \
    && make -C /tmp/pgvector PG_CONFIG=/usr/lib/postgresql/16/bin/pg_config \
    && make -C /tmp/pgvector PG_CONFIG=/usr/lib/postgresql/16/bin/pg_config install \
    && rm -rf /tmp/pgvector

LABEL org.opencontainers.image.title="Ontology integration-test PostgreSQL" \
      org.opencontainers.image.base.name="postgres:16.15-bookworm" \
      org.opencontainers.image.version="postgres-16.15-age-1.6.0-pgvector-0.8.1"
