"""使用虚构配置验证打包边界，绝不读取真实 OIDC 注册信息。"""
from pathlib import Path
import contextlib,hashlib,importlib.util,io,json,tempfile,unittest,zipfile
from unittest.mock import patch
import yaml
spec=importlib.util.spec_from_file_location('finalizer',Path(__file__).resolve().parents[1]/'tools/finalize.py')
module=importlib.util.module_from_spec(spec);spec.loader.exec_module(module)

class Packaging(unittest.TestCase):
    def fixture_config(self):
        origin='https://example.invalid'
        return {'serviceOrigin':origin,'pageUrl':origin+'/ontology-management/login','embedUrl':origin+'/ontology-management/','authorizationEndpoint':origin+'/api/v1/oidc/app/authorize','tokenEndpoint':origin+'/api/v1/oidc/app/token','userInfoEndpoint':origin+'/api/v1/oidc/app/userinfo','redirectUri':'https://app.workbench.aos.work/basilcraw/api/apps/oauth2/callback','appEntryPath':'/ontology','clientId':'fixture-app-client','clientSecret':'offline-secret-fixture','clientAuthMethod':'body'}

    def minimal_config(self,origin='https://new.example.invalid'):
        # 新格式只维护服务域名；Workbench 固定 callback 和客户端身份仍显式配置。
        return {'serviceOrigin':origin,'redirectUri':module.APP_CALLBACK,'appEntryPath':'/ontology','clientId':'fixture-app-client','clientSecret':'offline-secret-fixture','clientAuthMethod':'body'}

    def run_cli(self,config,root):
        path=root/'config.json';path.write_text(json.dumps(config))
        output=io.StringIO()
        with patch('sys.argv',['finalize.py','--config',str(path),'--output',str(root/'out')]),contextlib.redirect_stdout(output):
            code=module.main()
        return code,json.loads(output.getvalue())

    def test_empty_registration_does_not_create_archive(self):
        with tempfile.TemporaryDirectory() as temp:
            root=Path(temp);config=root/'config.json';config.write_text('{}')
            with self.assertRaises(ValueError):module.assemble(config,root/'output')
            self.assertFalse((root/'output').exists())
    def test_public_example_without_real_java_client_cannot_create_archive(self):
        # 公开示例故意不含 client id/secret，必须等管理员从 Java Admin 创建后再填私有副本。
        config=Path(__file__).resolve().parents[1]/'config.example.json'
        with tempfile.TemporaryDirectory() as temp:
            output=Path(temp)/'output'
            with self.assertRaisesRegex(ValueError,'缺少配置字段：clientId'):
                module.assemble(config,output)
            self.assertFalse(output.exists())
    def test_only_control_plane_contains_secret(self):
        with tempfile.TemporaryDirectory() as temp:
            root=Path(temp);path=root/'config.json';path.write_text(json.dumps(self.fixture_config()))
            result=module.assemble(path,root/'out')
            archive=Path(result['archive'])
            self.assertEqual(archive.stat().st_mode & 0o777,0o600)
            self.assertEqual(archive.read_bytes() and hashlib.sha256(archive.read_bytes()).hexdigest(),result['sha256'])
            self.assertEqual((root/'out/ontology-0.2.0-rc.1-admin-upload.zip.sha256').stat().st_mode & 0o777,0o600)
            self.assertIn(result['sha256'],(root/'out/ontology-0.2.0-rc.1-admin-upload.zip.sha256').read_text())
            self.assertEqual(result['version'],'0.2.0-rc.1')
            report=json.loads(Path(result['validationReport']).read_text())
            self.assertEqual(report['archiveSha256'],result['sha256'])
            self.assertFalse(report['appResourceAuthorizationVerified'])
            self.assertNotIn('offline-secret-fixture',json.dumps(report))
            with zipfile.ZipFile(archive) as z:
                self.assertIsNone(z.testzip())
                self.assertIn('manifest.yaml',z.namelist())
                self.assertIn('ontology-operations/SKILL.md',z.namelist())
                self.assertIn('upload-validation.json',z.namelist())
                self.assertNotIn('private/oidc-registration.json',z.namelist())
                self.assertFalse(any('/tests/' in name or name.startswith('tests/') for name in z.namelist()))
                self.assertNotIn('__',z.read('manifest.yaml').decode())
                for name in z.namelist():
                    self.assertNotIn('private/',name)
                    if name!='oauth-clients.yaml':self.assertNotIn(b'offline-secret-fixture',z.read(name))
                manifest=yaml.safe_load(z.read('manifest.yaml'))
                self.assertEqual(manifest['plugin']['hasIframe'],True)
                self.assertEqual(manifest['plugin']['iframeUrl'],'https://example.invalid/ontology-management/')
                self.assertEqual(manifest['app']['websiteUrl'],'https://example.invalid/ontology-management/')
                self.assertEqual(manifest['app']['entryPath'],'/ontology')
                self.assertEqual(manifest['actions'][0]['url'],'https://example.invalid/ontology-management/login')
                self.assertEqual(manifest['auth']['oauth2']['redirectUri'],'https://app.workbench.aos.work/basilcraw/api/apps/oauth2/callback')
                self.assertEqual(manifest['auth']['oauth2']['scopes'],['profile','ontology.read','ontology.write','ontology.manage'])
                self.assertEqual(manifest['auth']['oauth2']['options']['clientAuthMethod'],'body')
                self.assertEqual(manifest['auth']['oauth2']['refreshUrl'],'https://example.invalid/api/v1/oidc/app/token')
                self.assertEqual(manifest['auth']['oauth2']['tokenFields']['refreshToken'],'refresh_token')
                service=json.loads(z.read('ontology-operations/scripts/service.json'))
                self.assertEqual(service['pagePath'],'/ontology-management/')
                self.assertEqual(service['apiPrefix'],'/api/v1')
                clients=yaml.safe_load(z.read('oauth-clients.yaml'))['oauthClients'][0]
                self.assertEqual(clients['clientId'],'fixture-app-client')
                self.assertEqual(clients['displayName'],'Ontology')
                self.assertEqual(clients['clientSecret'],'offline-secret-fixture')
                oauth_info=z.getinfo('oauth-clients.yaml')
                self.assertEqual((oauth_info.external_attr>>16)&0o777,0o600)
    def test_minimal_single_origin_config_packages_and_all_addresses_follow_origin(self):
        first_origin='https://first.example.invalid'
        origin='https://rotated.example.invalid'
        with tempfile.TemporaryDirectory() as temp:
            root=Path(temp);path=root/'config.json'
            first_config=self.minimal_config(first_origin)
            path.write_text(json.dumps(first_config))
            first_result=module.assemble(path,root/'first-out')
            first_config['serviceOrigin']=origin
            path.write_text(json.dumps(first_config))
            result=module.assemble(path,root/'rotated-out')
            with zipfile.ZipFile(first_result['archive']) as z:
                first_manifest=yaml.safe_load(z.read('manifest.yaml'))
                self.assertEqual(first_manifest['variables']['base'],first_origin+'/api/v1')
            with zipfile.ZipFile(result['archive']) as z:
                manifest=yaml.safe_load(z.read('manifest.yaml'))
                self.assertEqual(manifest['variables']['base'],origin+'/api/v1')
                self.assertEqual(manifest['plugin']['iframeUrl'],origin+'/ontology-management/')
                self.assertEqual(manifest['app']['websiteUrl'],origin+'/ontology-management/')
                self.assertEqual(manifest['actions'][0]['url'],origin+'/ontology-management/login')
                oauth=manifest['auth']['oauth2']
                self.assertEqual(oauth['authorizeUrl'],origin+'/api/v1/oidc/app/authorize')
                self.assertEqual(oauth['tokenUrl'],origin+'/api/v1/oidc/app/token')
                self.assertEqual(oauth['userInfoUrl'],origin+'/api/v1/oidc/app/userinfo')
                self.assertEqual(oauth['redirectUri'],module.APP_CALLBACK)
                current_user=next(action for action in manifest['actions'] if action['key']=='getCurrentUser')
                self.assertEqual(current_user['url'],origin+'/api/v1/oidc/app/userinfo')
                service=json.loads(z.read('ontology-operations/scripts/service.json'))
                self.assertEqual(service['serviceOrigin'],origin)
                self.assertNotIn(b'first.example.invalid',b' '.join(z.read(name) for name in z.namelist()))
    def test_http_provider_rejected(self):
        with self.assertRaises(ValueError):module.https_url({'tokenEndpoint':'http://identity.invalid/token'},'tokenEndpoint')
    def test_browser_gateway_callback_rejected_for_app_package(self):
        config=self.fixture_config();config['redirectUri']='https://example.invalid/oidc/callback'
        with tempfile.TemporaryDirectory() as temp:
            root=Path(temp);path=root/'config.json';path.write_text(json.dumps(config))
            with self.assertRaisesRegex(ValueError,'redirectUri 必须使用 Workbench App OAuth 固定回调'):
                module.assemble(path,root/'out')
            self.assertFalse((root/'out').exists())
    def test_cli_shows_missing_field_without_secret(self):
        with tempfile.TemporaryDirectory() as temp:
            code,result=self.run_cli({'clientSecret':'offline-secret-fixture'},Path(temp))
            self.assertEqual(code,1)
            self.assertIn('缺少配置字段：serviceOrigin',result['message'])
            self.assertNotIn('offline-secret-fixture',json.dumps(result))
    def test_cli_explains_browser_callback_without_secret(self):
        config=self.fixture_config();config['redirectUri']='https://example.invalid/oidc/callback'
        with tempfile.TemporaryDirectory() as temp:
            code,result=self.run_cli(config,Path(temp))
            self.assertEqual(code,1)
            self.assertIn('redirectUri 必须使用 Workbench App OAuth 固定回调',result['message'])
            self.assertNotIn('offline-secret-fixture',json.dumps(result))
            self.assertFalse((Path(temp)/'out').exists())
    def test_upstream_oidc_endpoint_is_not_packaged_as_app_broker(self):
        config=self.fixture_config();config['tokenEndpoint']='https://identity.invalid/token'
        with tempfile.TemporaryDirectory() as temp:
            root=Path(temp);path=root/'config.json';path.write_text(json.dumps(config))
            with self.assertRaisesRegex(ValueError,'配置端点与 serviceOrigin 合同不一致：tokenEndpoint'):
                module.assemble(path,root/'out')
            self.assertFalse((root/'out').exists())
    def test_external_entry_path_is_rejected_and_host_path_must_be_relative(self):
        config=self.fixture_config();config['appEntryPath']='https://example.invalid/ontology'
        with tempfile.TemporaryDirectory() as temp:
            root=Path(temp);path=root/'config.json';path.write_text(json.dumps(config))
            with self.assertRaisesRegex(ValueError,'appEntryPath 必须为相对宿主路径 /ontology'):
                module.assemble(path,root/'out')
            self.assertFalse((root/'out').exists())
    def test_post_auth_method_rejected_in_favor_of_form_body_contract(self):
        config=self.fixture_config();config['clientAuthMethod']='post'
        with tempfile.TemporaryDirectory() as temp:
            root=Path(temp);path=root/'config.json';path.write_text(json.dumps(config))
            with self.assertRaisesRegex(ValueError,'客户端认证方式必须为 body'):
                module.assemble(path,root/'out')
    def test_iframe_fields_are_required(self):
        origin='https://example.invalid'
        template=(module.VERSION/'source/manifest.yaml').read_text()
        for key,value in {'__SERVICE_ORIGIN__':origin,'__PAGE_URL__':origin+'/ontology-management/login','__EMBED_URL__':origin+'/ontology-management/','__AUTHORIZATION_ENDPOINT__':origin+'/api/v1/oidc/app/authorize','__TOKEN_ENDPOINT__':origin+'/api/v1/oidc/app/token','__USERINFO_ENDPOINT__':origin+'/api/v1/oidc/app/userinfo','__REDIRECT_URI__':module.APP_CALLBACK}.items():
            template=template.replace(key,value)
        manifest=yaml.safe_load(template);del manifest['plugin']['iframeUrl']
        with self.assertRaisesRegex(ValueError,'hasIframe=true 且 iframeUrl'):
            module.check_manifest_contract(manifest,origin)

    def test_java_scope_and_refresh_fields_are_required(self):
        origin='https://example.invalid'
        template=(module.VERSION/'source/manifest.yaml').read_text()
        for key,value in {'__SERVICE_ORIGIN__':origin,'__PAGE_URL__':origin+'/ontology-management/login','__EMBED_URL__':origin+'/ontology-management/','__AUTHORIZATION_ENDPOINT__':origin+'/api/v1/oidc/app/authorize','__TOKEN_ENDPOINT__':origin+'/api/v1/oidc/app/token','__USERINFO_ENDPOINT__':origin+'/api/v1/oidc/app/userinfo','__REDIRECT_URI__':module.APP_CALLBACK}.items():
            template=template.replace(key,value)
        manifest=yaml.safe_load(template)
        manifest['auth']['oauth2']['scopes']=['profile']
        with self.assertRaisesRegex(ValueError,'scopes 必须与 Java App broker'):
            module.check_manifest_contract(manifest,origin)
        manifest=yaml.safe_load(template)
        del manifest['auth']['oauth2']['tokenFields']['refreshToken']
        with self.assertRaisesRegex(ValueError,'refresh_token 字段'):
            module.check_manifest_contract(manifest,origin)
    def test_app_website_url_is_required_and_matches_embed_url(self):
        origin='https://example.invalid'
        template=(module.VERSION/'source/manifest.yaml').read_text()
        for key,value in {'__SERVICE_ORIGIN__':origin,'__PAGE_URL__':origin+'/ontology-management/login','__EMBED_URL__':origin+'/ontology-management/','__AUTHORIZATION_ENDPOINT__':origin+'/api/v1/oidc/app/authorize','__TOKEN_ENDPOINT__':origin+'/api/v1/oidc/app/token','__USERINFO_ENDPOINT__':origin+'/api/v1/oidc/app/userinfo','__REDIRECT_URI__':module.APP_CALLBACK}.items():
            template=template.replace(key,value)
        manifest=yaml.safe_load(template);manifest['app']['websiteUrl']='https://example.invalid/ontology-management/login'
        with self.assertRaisesRegex(ValueError,'app.websiteUrl 必须指向 /ontology-management/'):
            module.check_manifest_contract(manifest,origin)
    def test_conflicting_legacy_embed_override_is_rejected(self):
        config=self.fixture_config();config['embedUrl']='https://other.invalid/ontology-management/'
        with tempfile.TemporaryDirectory() as temp:
            root=Path(temp);path=root/'config.json';path.write_text(json.dumps(config))
            with self.assertRaisesRegex(ValueError,'配置端点与 serviceOrigin 合同不一致：embedUrl'):
                module.assemble(path,root/'out')
            self.assertFalse((root/'out').exists())
    def test_browser_callback_cannot_replace_workbench_app_callback(self):
        config=self.fixture_config();config['redirectUri']='https://example.invalid/oidc/callback'
        with tempfile.TemporaryDirectory() as temp:
            root=Path(temp);path=root/'config.json';path.write_text(json.dumps(config))
            with self.assertRaisesRegex(ValueError,'redirectUri 必须使用 Workbench App OAuth 固定回调'):
                module.assemble(path,root/'out')
    def test_manifest_preserves_every_id_as_decimal_string(self):
        manifest=yaml.safe_load((module.VERSION/'source/manifest.yaml').read_text())
        id_fields=0;id_arrays=0
        for action in manifest['actions']:
            for name,schema in (action.get('input') or {}).items():
                if module.ID_FIELD.fullmatch(name):
                    id_fields+=1;self.assertEqual(schema['type'],'string',(action['key'],name))
                if module.ID_ARRAY_FIELD.fullmatch(name):
                    id_arrays+=1;self.assertEqual(schema['type'],'array');self.assertEqual(schema['items']['type'],'string')
                if name in ('pageNum','pageSize'):self.assertEqual(schema['type'],'integer')
        self.assertGreaterEqual(id_fields,35)
        self.assertGreaterEqual(id_arrays,5)
        self.assertEqual(len(manifest['actions']),27)
        self.assertEqual(manifest['plugin']['key'],'com.basil.ontology')
        self.assertEqual(manifest['app']['key'],'ontology')
if __name__=='__main__':unittest.main()
