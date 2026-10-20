"""Ontology App 的地址、凭证和 HTTP 边界。"""
from pathlib import Path
import json,os,urllib.parse,urllib.request,urllib.error
from datetime import datetime,timezone
CONFIG=json.loads((Path(__file__).parent/'service.json').read_text())
class AppError(Exception):
    pass

def origin():
    value=CONFIG['serviceOrigin']
    p=urllib.parse.urlsplit(value)
    if p.scheme!='https' or not p.hostname or p.username or p.password or p.query or p.fragment or p.path not in ('','/'):
        raise AppError('应用服务地址配置无效')
    return value.rstrip('/')

def credential():
    # 用户 Token 由 Workbench 注入；客户端 Secret 不属于成员运行资源。
    prefix=CONFIG['authEnvironmentPrefix']
    token=os.environ.get(prefix+'_ACCESS_TOKEN','')
    if not token or any(c.isspace() or ord(c)<32 for c in token):
        raise AppError('缺少有效的 App 授权，请通过 Workbench 连接 Ontology 后恢复执行')
    token_type=os.environ.get(prefix+'_TOKEN_TYPE','Bearer')
    if token_type.lower()!='bearer':raise AppError('应用授权 Token 类型不受支持')
    expires=os.environ.get(prefix+'_EXPIRES_AT')
    if expires:
        try:
            time=datetime.fromisoformat(expires.replace('Z','+00:00'))
            if time.tzinfo is None or time<=datetime.now(timezone.utc):raise ValueError()
        except ValueError:raise AppError('应用授权已过期或有效期配置无效') from None
    return token

def status():
    # 状态检查只反映本地注入，不冒称已完成真实 OIDC 验证。
    result={'source':'app','appKey':'ontology','providerKey':'ontology','serviceOrigin':origin(),'configured':False,'verified':False}
    try:credential();result['configured']=True
    except AppError as e:result['message']=str(e)
    return result

class NoRedirect(urllib.request.HTTPRedirectHandler):
    def redirect_request(self,req,fp,code,msg,headers,newurl):
        # 禁止携带用户凭证自动跳转到其他地址。
        return None

def request(method,path,query,body):
    token=credential()
    url=origin()+CONFIG['apiPrefix']+path
    if query:url+='?'+urllib.parse.urlencode(query,doseq=True)
    data=json.dumps(body,ensure_ascii=False,allow_nan=False).encode() if body is not None else None
    req=urllib.request.Request(url,data=data,headers={'Authorization':'Bearer '+token,'Content-Type':'application/json','Accept':'application/json'},method=method)
    try:
        with urllib.request.build_opener(NoRedirect()).open(req,timeout=30) as response:
            raw=response.read(4*1024*1024+1)
            if len(raw)>4*1024*1024:raise AppError('响应超过单次大小限制，请缩小查询范围')
            result=json.loads(raw)
    except urllib.error.HTTPError as e:raise AppError('Ontology HTTP '+str(e.code)+'；请检查授权、网关和接口状态') from None
    except (urllib.error.URLError,TimeoutError):raise AppError('Ontology 服务连接失败，请检查本地服务及临时隧道') from None
    except (ValueError,UnicodeDecodeError):raise AppError('Ontology 返回了无效的 JSON 响应') from None
    if not isinstance(result,dict) or result.get('success') is not True:
        raise AppError('Ontology 业务请求未成功，未将失败响应当作结果')
    return result

def request_userinfo():
    """通过 App OAuth broker 验证当前 Bearer，并只返回约定的身份字段。"""
    token=credential()
    url=origin()+'/oidc/app/userinfo'
    req=urllib.request.Request(url,headers={'Authorization':'Bearer '+token,'Accept':'application/json'},method='GET')
    try:
        with urllib.request.build_opener(NoRedirect()).open(req,timeout=15) as response:
            raw=response.read(64*1024+1)
            if len(raw)>64*1024:raise AppError('用户身份响应超过大小限制')
            result=json.loads(raw)
    except urllib.error.HTTPError as e:raise AppError('App 用户身份验证失败，HTTP '+str(e.code)) from None
    except (urllib.error.URLError,TimeoutError):raise AppError('App 用户身份服务连接失败') from None
    except (ValueError,UnicodeDecodeError):raise AppError('App 用户身份服务返回了无效 JSON') from None
    if not isinstance(result,dict):raise AppError('App 用户身份响应格式无效')
    identity={key:result.get(key) for key in ('id','username','sub')}
    if 'name' in result:
        # 上游不一定提供 name；有值时原样保留 Unicode，不用其他字段伪造。
        if result['name'] is not None and not isinstance(result['name'],str):
            raise AppError('App 用户身份字段 name 格式无效')
        identity['name']=result['name']
    if isinstance(identity['id'],int) and not isinstance(identity['id'],bool):
        # 身份 ID 一律输出十进制字符串，避免宿主经 JavaScript 数字丢失精度。
        identity['id']=str(identity['id'])
    if not isinstance(identity['id'],str) or not identity['id'] or any(not isinstance(identity[key],str) or not identity[key] for key in ('username','sub')):
        raise AppError('App 用户身份响应缺少 id、username 或 sub')
    return identity
