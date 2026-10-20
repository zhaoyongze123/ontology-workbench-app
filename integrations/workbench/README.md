# Workbench App 接入

此目录保存既有 `0.1.1` App 的去凭据模板、工具脚本和打包契约测试，便于继续开发和比对正式导出格式。

该模板对应历史 Python 接入链路。当前 Java 多用户版本尚未完成下游 OAuth2 授权、scope 和刷新轮换；不能直接用旧模板生成新版本后宣称已适配。新上传包应在服务端授权契约与 Workbench 联调完成后生成。

`source/oauth-clients.yaml` 中的 Secret 必须保持为空或占位符，发布时只从服务端受保护配置填入管理员私密部分；生成的 ZIP 不进入 Git。

