"""校验 App OAuth broker 配置并生成带校验记录的管理员上传包。"""
from pathlib import Path
import argparse,hashlib,json,os,re,shutil,tempfile,urllib.parse,zipfile
import yaml

VERSION=Path(__file__).resolve().parents[1]
APP_CALLBACK='https://app.workbench.aos.work/basilcraw/api/apps/oauth2/callback'
BUSINESS_PAGE_PATH='/ontology-management/'
BROWSER_LOGIN_PATH=BUSINESS_PAGE_PATH+'login'
EMBED_PATH=BUSINESS_PAGE_PATH
# Java servlet context path；OAuth broker 路由由服务端挂载在此 API 前缀下。
API_PREFIX='/api/v1'
BROKER_ENDPOINTS={
    'authorizationEndpoint':API_PREFIX+'/oidc/app/authorize',
    'tokenEndpoint':API_PREFIX+'/oidc/app/token',
    'userInfoEndpoint':API_PREFIX+'/oidc/app/userinfo',
}
PLACEHOLDER=re.compile(r'__[A-Z0-9_]+__')
ID_FIELD=re.compile(r'^(?:id|[A-Za-z]*Id)$')
ID_ARRAY_FIELD=re.compile(r'^(?:idList|[A-Za-z]*IdList)$')

def require(config,key):
    value=config.get(key)
    if not isinstance(value,str) or not value.strip():raise ValueError('缺少配置字段：'+key)
    if '__' in value:raise ValueError('配置字段仍含占位符：'+key)
    return value

def https_url(config,key,origin_only=False):
    value=require(config,key)
    parsed=urllib.parse.urlsplit(value)
    if parsed.scheme!='https' or not parsed.hostname or parsed.username or parsed.password or parsed.fragment:
        raise ValueError('需要有效的 HTTPS 地址：'+key)
    if origin_only and (parsed.path not in ('','/') or parsed.query):raise ValueError('需要纯域名入口：'+key)
    return value.rstrip('/') if origin_only else value

def check_derived_override(config,key,expected):
    """兼容旧配置，但显式地址必须与唯一域名及固定路由一致。"""
    if key not in config:return
    actual=https_url(config,key)
    if actual!=expected:
        raise ValueError('配置端点与 serviceOrigin 合同不一致：'+key)

def check_manifest_contract(manifest,origin):
    """确认正式导出要求的嵌入声明、OAuth 映射及业务 ID 精度契约。"""
    browser_url=origin+BROWSER_LOGIN_PATH
    embed_url=origin+EMBED_PATH
    plugin=manifest.get('plugin',{})
    app=manifest.get('app',{})
    if plugin.get('key')!='com.basil.ontology' or app.get('key')!='ontology' or app.get('authProviderKey')!='ontology':
        raise ValueError('Manifest Ontology plugin/app/authProvider key 不符合正式 App 合同')
    if plugin.get('hasIframe') is not True or plugin.get('iframeUrl')!=embed_url:
        raise ValueError('plugin 必须声明 hasIframe=true 且 iframeUrl 指向 /ontology-management/')
    if app.get('websiteUrl')!=embed_url or app.get('entryPath')!='/ontology':
        raise ValueError('app.websiteUrl 必须指向 /ontology-management/，entryPath 必须为 /ontology')
    oauth=manifest.get('auth',{}).get('oauth2',{})
    expected={
        'authorizeUrl':origin+BROKER_ENDPOINTS['authorizationEndpoint'],
        'tokenUrl':origin+BROKER_ENDPOINTS['tokenEndpoint'],
        'userInfoUrl':origin+BROKER_ENDPOINTS['userInfoEndpoint'],
        'redirectUri':APP_CALLBACK,
    }
    expected.update({'refreshUrl':expected['tokenUrl']})
    for key,value in expected.items():
        if oauth.get(key)!=value:raise ValueError('Manifest OAuth 字段不符合 broker 合同：'+key)
    supported_scopes=['profile','ontology.read','ontology.write','ontology.manage']
    if oauth.get('scopes')!=supported_scopes:
        raise ValueError('Manifest scopes 必须与 Java App broker 支持的范围及顺序一致')
    if oauth.get('tokenFields',{}).get('refreshToken')!='refresh_token':
        raise ValueError('Manifest 必须映射 Java broker 的 refresh_token 字段')
    if oauth.get('options',{}).get('tokenRequestType')!='form' or oauth.get('options',{}).get('pkce') is not True:
        raise ValueError('Manifest 必须使用 form token request 与 PKCE')
    if oauth.get('tokenPlacement')!={'type':'bearer'}:
        raise ValueError('Manifest 必须通过 Bearer 传递 App access token')
    if oauth.get('resultBaseUrl')!='https://app.workbench.aos.work':
        raise ValueError('Manifest resultBaseUrl 必须保留正式 Workbench origin')
    if oauth.get('account')!={'action':'getCurrentUser','idField':'id','nameField':'username'}:
        raise ValueError('Manifest auth.account 必须映射 getCurrentUser 的 id 和 username')
    if oauth.get('options',{}).get('clientAuthMethod')!='body':
        raise ValueError('Manifest OAuth 客户端认证方式必须为 body')
    catalog=json.loads((VERSION/'source/ontology-operations/references/operations.json').read_text())['operations']
    actions={item.get('key'):item for item in manifest.get('actions',[])}
    expected_actions=set(catalog)|{'open','getCurrentUser'}
    if len(actions)!=27 or set(actions)!=expected_actions:
        raise ValueError('Manifest 必须保留原有 26 个 Action 并增加 getCurrentUser')
    opened=actions['open']
    current=actions['getCurrentUser']
    if opened.get('url')!=browser_url or opened.get('auth') is not False:
        raise ValueError('open Action 必须以未授权浏览器入口打开登录页面')
    if current.get('url')!=expected['userInfoUrl'] or current.get('auth') is not True:
        raise ValueError('getCurrentUser 必须通过 App Bearer 调用 broker userinfo')
    for key,operation in catalog.items():
        action=actions[key]
        if action.get('method')!=operation.get('method') or action.get('url')!='{{base}}'+operation.get('path',''):
            raise ValueError('Manifest 业务 Action 与 OpenAPI 操作目录不一致：'+key)
    # Workbench 清单里的业务主键以字符串声明，避免 19 位 ID 被宿主转成浮点数。
    for action in manifest.get('actions',[]):
        for name,schema in (action.get('input') or {}).items():
            if ID_FIELD.fullmatch(name) and schema.get('type')!='string':
                raise ValueError('Manifest ID 字段必须声明为字符串：'+action.get('key','')+'.'+name)
            if ID_ARRAY_FIELD.fullmatch(name):
                if schema.get('type')!='array' or (schema.get('items') or {}).get('type')!='string':
                    raise ValueError('Manifest ID 数组元素必须声明为字符串：'+action.get('key','')+'.'+name)

def check_staged_package(staging,client_id,secret):
    """打包前扫描 Secret、占位符、结构及 Action 合同。"""
    manifest_path=staging/'manifest.yaml'
    manifest_text=manifest_path.read_text()
    manifest=yaml.safe_load(manifest_text)
    if PLACEHOLDER.search(manifest_text):raise ValueError('Manifest 仍含未填写配置')
    check_manifest_contract(manifest,manifest['variables']['base'].removesuffix('/api/v1'))
    leaked=[]
    for path in staging.rglob('*'):
        if not path.is_file():continue
        raw=path.read_bytes()
        if secret.encode() in raw and path.relative_to(staging).as_posix()!='oauth-clients.yaml':
            leaked.append(path.relative_to(staging).as_posix())
        try:text=raw.decode('utf-8')
        except UnicodeDecodeError:continue
        if PLACEHOLDER.search(text):raise ValueError('成员文件仍含未填写配置')
    if leaked:raise ValueError('客户端凭证进入成员文件，拒绝打包')
    clients_path=staging/'oauth-clients.yaml'
    if not clients_path.is_file():raise ValueError('缺少受保护的根级 oauth-clients.yaml')
    clients=yaml.safe_load(clients_path.read_text()) or {}
    entries=clients.get('oauthClients',[])
    if len(entries)!=1 or entries[0]!={'key':'ontology','displayName':'Ontology','authType':'oauth2','clientId':client_id,'clientSecret':secret}:
        raise ValueError('根级 OAuth 客户端必须使用 Ontology displayName 和受保护的 broker 凭证')
    if (staging/'ontology-operations/scripts/service.json').is_file() is False:
        raise ValueError('缺少成员服务配置')
    return [
        'iframe_plugin_and_website_declarations',
        'relative_app_entry_path',
        'browser_login_entry_and_open_action',
        'app_oauth_broker_endpoints_and_fixed_callback',
        'account_mapping_and_get_current_user_action',
        'client_auth_method_body_and_oauth_display_name',
        'refresh_rotation_capability_declared',
        'business_ids_preserved_as_decimal_strings',
        'client_secret_confined_to_root_oauth_clients',
        'oauth_client_display_name_and_identity',
        'manifest_has_no_placeholders',
    ]

def assemble(config_path,output):
    config=json.loads(config_path.read_text())
    origin=https_url(config,'serviceOrigin',True)
    browser_url=origin+BROWSER_LOGIN_PATH
    embed_url=origin+EMBED_PATH
    # 地址由域名和固定路由派生；兼容旧字段时仍严格校验，防止上游 URL 混入。
    for key,path in BROKER_ENDPOINTS.items():
        check_derived_override(config,key,origin+path)
    check_derived_override(config,'pageUrl',browser_url)
    check_derived_override(config,'embedUrl',embed_url)
    entry=require(config,'appEntryPath')
    redirect=https_url(config,'redirectUri')
    if entry!='/ontology':raise ValueError('appEntryPath 必须为相对宿主路径 /ontology')
    if redirect!=APP_CALLBACK:raise ValueError('redirectUri 必须使用 Workbench App OAuth 固定回调')
    client_id=require(config,'clientId')
    secret=require(config,'clientSecret')
    method=require(config,'clientAuthMethod')
    if method!='body':raise ValueError('App OAuth broker 客户端认证方式必须为 body')

    replacements={
        '__SERVICE_ORIGIN__':origin,
        '__PAGE_URL__':browser_url,
        '__EMBED_URL__':embed_url,
        '__AUTHORIZATION_ENDPOINT__':origin+BROKER_ENDPOINTS['authorizationEndpoint'],
        '__TOKEN_ENDPOINT__':origin+BROKER_ENDPOINTS['tokenEndpoint'],
        '__USERINFO_ENDPOINT__':origin+BROKER_ENDPOINTS['userInfoEndpoint'],
        '__REDIRECT_URI__':APP_CALLBACK,
        '__CLIENT_AUTH_METHOD__':method,
    }
    manifest=(VERSION/'source/manifest.yaml').read_text()
    for key,value in replacements.items():manifest=manifest.replace(key,value)
    if PLACEHOLDER.search(manifest):raise ValueError('Manifest 仍含未填写配置')
    yaml.safe_load(manifest)

    output.mkdir(parents=True,exist_ok=True)
    archive=output/'ontology-0.2.0-rc.1-admin-upload.zip'
    report_path=output/'ontology-0.2.0-rc.1-upload-validation.json'
    checksum_path=output/'ontology-0.2.0-rc.1-admin-upload.zip.sha256'
    with tempfile.TemporaryDirectory(prefix='ontology-app-') as temporary:
        staging=Path(temporary)/'ontology'
        shutil.copytree(VERSION/'source',staging,ignore=shutil.ignore_patterns('__pycache__','*.pyc'))
        (staging/'manifest.yaml').write_text(manifest)
        clients={'oauthClients':[{'key':'ontology','displayName':'Ontology','authType':'oauth2','clientId':client_id,'clientSecret':secret}]}
        private=staging/'oauth-clients.yaml'
        private.write_text(yaml.safe_dump(clients,allow_unicode=True,sort_keys=False));private.chmod(0o600)
        service_path=staging/'ontology-operations/scripts/service.json'
        service=json.loads(service_path.read_text())
        service['serviceOrigin']=origin
        # open Action 进入登录页；成员的业务深链仍应落在已验证的 Ontology 页面。
        service['pagePath']=BUSINESS_PAGE_PATH
        service['apiPrefix']='/api/v1'
        service['authEnvironmentPrefix']='APP_OAUTH_ONTOLOGY'
        service_path.write_text(json.dumps(service,ensure_ascii=False,indent=2)+'\n')
        # 上传包只含 Workbench App broker 客户端；上游 OIDC 客户端留在网关服务端。
        (staging/'README.md').write_text(
            '# Ontology App 0.2.0-rc.1 管理员上传联调包\n\n'
            '本私有管理员包的根级 `oauth-clients.yaml` 保存 Workbench App OAuth 的 `clientId` 与 `clientSecret`，只允许受保护的 Workbench 管理控制面读取。成员端只接收宿主注入的 Bearer。Java 服务上游 Workbench OIDC 凭证 (`ontology.workbench.*`) 属于服务端机密配置，与本 App client 凭证用途不同，绝不能写入本包。此 ZIP 含 App client secret，禁止提交代码库、公开分发或放入普通成员资源；仅通过已批准的受限管理员通道暂存、上传与清理。\n\n'
            'Manifest 按官方 App schema 声明外部 iframe、相对 `app.entryPath`、form token request、body client authentication、PKCE、access/refresh token 字段、userinfo 映射，以及 Java broker 支持的 `profile`、`ontology.read`、`ontology.write`、`ontology.manage` scope。`open` 进入 `/ontology-management/login`；iframe 使用 `/ontology-management/`。\n\n'
            '上传后须由用户执行文档中的 Workbench 授权和 API 验收。包内 `upload-validation.json` 只记录离线结构及凭证边界检查，不表示平台已接受、授权或线上 API 已验证。API 层会将 OAuth scope 与项目成员权限交叉检查；数据库隔离、迁移、回滚和完整 Workbench 生命周期仍以各验收工单及手动证据为准。\n'
        )
        validation_checks=check_staged_package(staging,client_id,secret)
        internal_report={
            'schemaVersion':1,
            'package':'ontology-0.2.0-rc.1-admin-upload.zip',
            'status':'ready_for_manual_workbench_validation',
            'checks':{name:'passed' for name in validation_checks},
            'browserLoginConfirmedByUser':False,
            'appResourceAuthorizationVerified':False,
            'limitations':[
                'Workbench App client credentials must be provisioned from the Java admin console before packaging.',
                'Workbench upload, browser login, App OAuth authorization, refresh, identity mapping, and API Bearer injection remain unverified.',
                'This private admin archive contains oauth-clients.yaml; store and upload it only through the approved restricted admin channel.',
                'Project API ACL and database/AGE isolation require their separate release acceptance evidence.',
            ],
        }
        (staging/'upload-validation.json').write_text(json.dumps(internal_report,ensure_ascii=False,indent=2)+'\n')
        with zipfile.ZipFile(archive,'w',zipfile.ZIP_DEFLATED) as z:
            for path in sorted(staging.rglob('*')):
                if path.is_file():z.write(path,path.relative_to(staging))
    archive.chmod(0o600)
    digest=hashlib.sha256(archive.read_bytes()).hexdigest()
    external_report={**internal_report,'archiveSha256':digest}
    report_path.write_text(json.dumps(external_report,ensure_ascii=False,indent=2)+'\n');report_path.chmod(0o600)
    checksum_path.write_text(digest+'  '+archive.name+'\n');checksum_path.chmod(0o600)
    # 工具只输出归档状态与哈希，不回显任何客户端凭证。
    return {'archive':str(archive),'validationReport':str(report_path),'sha256':digest,
            'version':'0.2.0-rc.1','status':'ready_for_manual_workbench_validation',
            'browserLoginConfirmedByUser':False,'appResourceAuthorizationVerified':False}

def main():
    parser=argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--config',type=Path,required=True)
    parser.add_argument('--output',type=Path,default=VERSION/'dist')
    args=parser.parse_args()
    try:
        print(json.dumps(assemble(args.config,args.output),ensure_ascii=False));return 0
    except (ValueError,OSError,KeyError,yaml.YAMLError) as exc:
        # 只显示白名单内的配置字段错误，避免将 JSON 解析细节或凭证写入终端。
        prefixes=('缺少配置','配置字段','需要','Manifest','plugin','app.websiteUrl','appEntryPath','配置端点','pageUrl','embedUrl','redirectUri','App OAuth','客户端凭证','缺少成员')
        message=str(exc) if type(exc) is ValueError and str(exc).startswith(prefixes) else '配置格式无效或打包校验失败，请检查本地文件'
        print(json.dumps({'status':'pending_configuration','message':message},ensure_ascii=False));return 1
if __name__=='__main__':raise SystemExit(main())
