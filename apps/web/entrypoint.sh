#!/bin/sh
set -e

DEFAULT_SERVER_HOST="http://127.0.0.1:13001"
SERVER_HOST=${SERVER_HOST:-$DEFAULT_SERVER_HOST}
NGINX_CONFIG_TEMPLATE_PATH="/etc/nginx/templates/nginx.conf.template"
NGINX_CONFIG_OUTPUT_PATH="/app/config/nginx.conf"
# nginx配置模板版本检测变量
CONFIG_VERSION_FILE="/app/config/.config_version"
CURRENT_TEMPLATE_VERSION="$CONFIG_VERSION"

echo "-----------------------"
echo "接口=》 SERVER_HOST=${SERVER_HOST}  (需要注意挂载目录下的host是否更改,更改则并非当前url)"

if [ ! -f "$NGINX_CONFIG_TEMPLATE_PATH" ]; then
  echo "错误: Nginx 配置模板 '$NGINX_CONFIG_TEMPLATE_PATH' 未找到!"
  exit 1
fi

# 配置文件生成逻辑改造
if [ ! -f "$NGINX_CONFIG_OUTPUT_PATH" ] || 
   [ ! -f "$CONFIG_VERSION_FILE" ] || 
   [ "$(cat $CONFIG_VERSION_FILE)" != "$CURRENT_TEMPLATE_VERSION" ]; then
  
  echo "检测到配置需要更新 (版本: $CURRENT_TEMPLATE_VERSION)"
  
  # 备份旧配置（如果存在）
  if [ -f "$NGINX_CONFIG_OUTPUT_PATH" ]; then
    BACKUP_PATH="${NGINX_CONFIG_OUTPUT_PATH}.bak.$(date +%Y%m%d%H%M%S)"
    cp "$NGINX_CONFIG_OUTPUT_PATH" "$BACKUP_PATH"
    echo "旧配置已备份至: $BACKUP_PATH"
    echo "或在挂载目录的nginx下查看"
  fi

  # 生成新配置
  envsubst '${SERVER_HOST}' < "$NGINX_CONFIG_TEMPLATE_PATH" > "$NGINX_CONFIG_OUTPUT_PATH"
  echo "$CURRENT_TEMPLATE_VERSION" > "$CONFIG_VERSION_FILE"
  chmod 644 "$NGINX_CONFIG_OUTPUT_PATH"
  
  echo "新版配置已生成 (版本: $CURRENT_TEMPLATE_VERSION)"
else
  echo "当前配置已是最新版本 (版本: $(cat $CONFIG_VERSION_FILE))"
fi

# if [ ! -f "$NGINX_CONFIG_OUTPUT_PATH" ]; then
#   echo "初始化nginx  $NGINX_CONFIG_OUTPUT_PATH 配置"
#   echo "正在从 '$NGINX_CONFIG_TEMPLATE_PATH' 生成 Nginx 配置到 '$NGINX_CONFIG_OUTPUT_PATH'..."
#   envsubst '${SERVER_HOST}' < "$NGINX_CONFIG_TEMPLATE_PATH" > "$NGINX_CONFIG_OUTPUT_PATH"
#   echo "Nginx 配置已生成"
# else
#   echo "使用挂载nginx 配置目录 $NGINX_CONFIG_OUTPUT_PATH."
# fi

echo "-----------------------"
echo "开始 Nginx~"
exec nginx -g 'daemon off;'