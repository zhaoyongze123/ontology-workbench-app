#!/bin/sh
set -eu
if [ -z "${EDGE_TRUSTED_PROXY_CIDRS:-}" ]; then
  echo 'EDGE_TRUSTED_PROXY_CIDRS must list the HTTPS gateway source networks' >&2
  exit 1
fi
umask 077
: > /tmp/trusted-proxies.conf
for cidr in $EDGE_TRUSTED_PROXY_CIDRS; do
  case "$cidr" in *[!0-9./:]*) echo 'invalid CIDR in EDGE_TRUSTED_PROXY_CIDRS' >&2; exit 1;; esac
  printf 'set_real_ip_from %s;\n' "$cidr" >> /tmp/trusted-proxies.conf
done
exec nginx -g 'daemon off;'
