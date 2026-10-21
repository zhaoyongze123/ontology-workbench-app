-- 本地密码凭证激活状态与账号管理员启停状态分离；历史账号保持已激活。
ALTER TABLE ontology.og_user
    ADD COLUMN password_activated BOOLEAN NOT NULL DEFAULT TRUE;

COMMENT ON COLUMN ontology.og_user.password_activated IS
    '是否已通过一次性账号激活链接设置本地密码；独立于管理员 enabled 状态，也不限制 Workbench OIDC 身份。';
