#!/usr/bin/env python3
"""在一次性 Docker 部署中验证管理员登录、项目/AGE 写入与只读查询。"""

import json
import os
import urllib.parse

from smoke_client import Browser


transport_origin = os.environ.get("ONTOLOGY_SMOKE_TRANSPORT", "http://127.0.0.1:28083")
trusted_origin = os.environ.get("ONTOLOGY_SMOKE_ORIGIN", "https://127.0.0.1:28083")
mode = os.environ.get("ONTOLOGY_SMOKE_MODE", "create")
username = "smoke-owner"
password = os.environ["ONTOLOGY_SMOKE_PASSWORD"]
project_name = "ci_smoke_project"
graph_name = "ci_smoke_graph"


def api_succeeded(response):
    # ApiResult.SUCCESS 在服务端 ResultEnum 中的稳定契约码为 0。
    return response is not None and response.get("code") == 0

browser = Browser(transport_origin)
status, csrf, _ = browser.request("/auth/csrf", origin=trusted_origin)
assert status == 200 and csrf and csrf.get("token"), "获取 CSRF token 失败"
browser.csrf = csrf
status, login, _ = browser.request(
    "/auth/login",
    "POST",
    {"username": username, "password": password},
    form=True,
    origin=trusted_origin,
)
assert status == 200 and login and login.get("user", {}).get("username") == username, "新建管理员登录失败"
status, browser.csrf, _ = browser.request("/auth/csrf", origin=trusted_origin)
assert status == 200 and browser.csrf and browser.csrf.get("token"), "登录后获取 CSRF token 失败"

if mode == "create":
    status, project, _ = browser.request(
        "/organization/save",
        "POST",
        {"name": project_name, "description": "Disposable CI deployment smoke"},
        origin=trusted_origin,
    )
    assert status == 200 and api_succeeded(project) and project.get("data", {}).get("id"), "创建项目失败"
    project_id = project["data"]["id"]

    status, graph, _ = browser.request(
        "/graph/save",
        "POST",
        {
            "name": graph_name,
            "description": "Disposable AGE persistence probe",
            "graphType": "ONTOLOGY",
            "organizationId": project_id,
        },
        origin=trusted_origin,
    )
    assert status == 200 and api_succeeded(graph) and graph.get("data", {}).get("id"), "创建本体图谱失败"
    graph_id = graph["data"]["id"]

    status, node_result, _ = browser.request(
        "/node/saveNode",
        "POST",
        {
            "name": "CiSmokeNode",
            "description": "AGE write/read probe",
            "propertySchemaJson": [],
            "graphId": graph_id,
            "graphType": "ONTOLOGY",
        },
        origin=trusted_origin,
    )
    assert status == 200 and api_succeeded(node_result), "AGE 图谱节点写入失败"
elif mode == "verify":
    status, projects, _ = browser.request(
        "/organization/page?keyword=" + urllib.parse.quote(project_name), origin=trusted_origin
    )
    assert status == 200 and api_succeeded(projects), "读取恢复后的项目失败"
    project_rows = [row for row in projects.get("data", []) if row.get("name") == project_name]
    assert len(project_rows) == 1, "恢复后未找到唯一测试项目"
    project_id = project_rows[0]["id"]
    status, graphs, _ = browser.request(
        "/graph/page?graphType=ONTOLOGY&organizationId=" + urllib.parse.quote(str(project_id)),
        origin=trusted_origin,
    )
    assert status == 200 and api_succeeded(graphs), "读取恢复后的图谱失败"
    graph_rows = [row for row in graphs.get("data", []) if row.get("name") == graph_name]
    assert len(graph_rows) == 1, "恢复后未找到唯一测试图谱"
    graph_id = graph_rows[0]["id"]
else:
    raise ValueError("ONTOLOGY_SMOKE_MODE 仅支持 create 或 verify")

status, result, _ = browser.request(
    "/cypher/query",
    "POST",
    {
        "graphId": graph_id,
        "graphType": "ONTOLOGY",
        "cypher": "MATCH (n) RETURN count(n) AS total",
    },
    origin=trusted_origin,
)
assert status == 200 and api_succeeded(result) and result.get("data"), "AGE 只读查询失败"
assert int(result["data"][0]["total"]) == 1, "AGE 查询未读回刚写入或已恢复的节点"

print(json.dumps({"passed": True, "mode": mode, "project": project_name, "graph": graph_name, "nodes": 1}))
