package com.devin.ontology.oidc;

final class WorkbenchIdentityExceptions {
    private WorkbenchIdentityExceptions() { }

    static final class DisabledUser extends RuntimeException {
        DisabledUser() { super("关联的 Ontology 用户已停用"); }
    }

    static final class BindingConflict extends RuntimeException {
        BindingConflict() { super("该 Workbench 身份已绑定到其他 Ontology 用户"); }
    }

    static final class StaleInternalUser extends RuntimeException {
        StaleInternalUser() { super("Ontology 登录状态已失效"); }
    }
}
