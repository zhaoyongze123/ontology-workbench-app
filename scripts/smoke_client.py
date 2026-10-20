"""隔离副本 HTTP 验收客户端；不输出凭据、Cookie 或业务正文。"""
import http.cookiejar
import json
import urllib.error
import urllib.parse
import urllib.request

class Browser:
    def __init__(self, origin):
        self.origin = origin
        self.jar = http.cookiejar.CookieJar()
        self.opener = urllib.request.build_opener(urllib.request.HTTPCookieProcessor(self.jar))
        self.csrf = None

    def request(self, path, method="GET", body=None, form=False, csrf=True, origin=None):
        headers = {"Origin": origin or self.origin}
        if self.csrf and csrf:
            headers[self.csrf["headerName"]] = self.csrf["token"]
        data = None
        if body is not None:
            data = (urllib.parse.urlencode(body) if form else json.dumps(body)).encode()
            headers["Content-Type"] = ("application/x-www-form-urlencoded" if form else "application/json")
        request = urllib.request.Request(self.origin + "/api/v1" + path, data=data,
                                         headers=headers, method=method)
        try:
            response = self.opener.open(request, timeout=20)
        except urllib.error.HTTPError as error:
            response = error
        content = response.read()
        try:
            parsed = json.loads(content)
        except (json.JSONDecodeError, UnicodeDecodeError):
            parsed = None
        return response.status, parsed, response.headers

    def get_csrf(self):
        code, payload, _ = self.request("/auth/csrf")
        assert code == 200, "获取 CSRF 失败"
        self.csrf = payload

    def login(self, username, password):
        self.get_csrf()
        previous = tuple(cookie.value for cookie in self.jar)
        code, payload, headers = self.request("/auth/login", "POST",
                    {"username": username, "password": password}, form=True)
        assert code == 200, "有效测试用户登录失败"
        assert tuple(cookie.value for cookie in self.jar) != previous, "登录后未轮换会话 ID"
        assert "HttpOnly" in headers.get("Set-Cookie", ""), "会话缺少 HttpOnly"
        assert "Domain=" not in headers.get("Set-Cookie", ""), "会话不是 host-only"
        assert "password" not in json.dumps(payload).lower(), "响应泄漏密码字段"
        self.get_csrf()
