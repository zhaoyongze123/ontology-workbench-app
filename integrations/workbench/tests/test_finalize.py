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
        return {'serviceOrigin':origin,'pageUrl':origin+'/oidc/login','embedUrl':origin+'/oidc/embed','authorizationEndpoint':origin+'/oidc/app/authorize','tokenEndpoint':origin+'/oidc/app/token','userInfoEndpoint':origin+'/oidc/app/userinfo','redirectUri':'https://app.workbench.aos.work/basilcraw/api/apps/oauth2/callback','appEntryPath':'/ontology','clientId':'fixture-app-client','clientSecret':'offline-secret-fixture','clientAuthMethod':'body'}

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
    def test_only_control_plane_contains_secret(self):
        with tempfile.TemporaryDirectory() as temp:
            root=Path(temp);path=root/'config.json';path.write_text(json.dumps(self.fixture_config()))
            result=module.assemble(path,root/'out')
            archive=Path(result['archive'])
            self.assertEqual(archive.stat().st_mode & 0o777,0o600)
            self.assertEqual(archive.read_bytes() and hashlib.sha256(archive.read_bytes()).hexdigest(),result['sha256'])
            self.assertEqual((root/'out/ontology-0.1.1-admin-upload.zip.sha256').stat().st_mode & 0o777,0o600)
            self.assertIn(result['sha256'],(root/'out/ontology-0.1.1-admin-upload.zip.sha256').read_text())
            self.assertEqual(result['version'],'0.1.1')
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
                self.assertEqual(manifest['plugin']['iframeUrl'],'https://example.invalid/oidc/embed')
                self.assertEqual(manifest['app']['websiteUrl'],'https://example.invalid/oidc/embed')
                self.assertEqual(manifest['app']['entryPath'],'/ontology')
                self.assertEqual(manifest['actions'][0]['url'],'https://example.invalid/oidc/login')
                self.assertEqual(manifest['auth']['oauth2']['redirectUri'],'https://app.workbench.aos.work/basilcraw/api/apps/oauth2/callback')
                self.assertEqual(manifest['auth']['oauth2']['scopes'],['profile'])
                self.assertEqual(manifest['auth']['oauth2']['options']['clientAuthMethod'],'body')
                self.assertNotIn('refreshUrl',manifest['auth']['oauth2'])
                self.assertNotIn('refreshToken',manifest['auth']['oauth2']['tokenFields'])
                service=json.loads(z.read('ontology-operations/scripts/service.json'))
                self.assertEqual(service['pagePath'],'/ontology-management/')
                self.assertEqual(service['apiPrefix'],'/api/v1')
                clients=yaml.safe_load(z.read('oauth-clients.yaml'))['oauthClients'][0]
                self.assertEqual(clients['clientId'],'fixture-app-client')
                self.assertEqual(clients['displayName'],'Ontology')
                self.assertEqual(clients['clientSecret'],'offline-secret-fixture')
                oauth_info=z.getinfo('oauth-clients.yaml')
                self.assertEqual((oauth_info.external_attr>>16)&0o777,0o600)
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
            with self.assertRaisesRegex(ValueError,'配置端点与 App OAuth broker 合同不一致：tokenEndpoint'):
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
        for key,value in {'__SERVICE_ORIGIN__':origin,'__PAGE_URL__':origin+'/oidc/login','__EMBED_URL__':origin+'/oidc/embed','__AUTHORIZATION_ENDPOINT__':origin+'/oidc/app/authorize','__TOKEN_ENDPOINT__':origin+'/oidc/app/token','__USERINFO_ENDPOINT__':origin+'/oidc/app/userinfo','__REDIRECT_URI__':module.APP_CALLBACK}.items():
            template=template.replace(key,value)
        manifest=yaml.safe_load(template);del manifest['plugin']['iframeUrl']
        with self.assertRaisesRegex(ValueError,'hasIframe=true 且 iframeUrl'):
            module.check_manifest_contract(manifest,origin)
    def test_app_website_url_is_required_and_matches_embed_url(self):
        origin='https://example.invalid'
        template=(module.VERSION/'source/manifest.yaml').read_text()
        for key,value in {'__SERVICE_ORIGIN__':origin,'__PAGE_URL__':origin+'/oidc/login','__EMBED_URL__':origin+'/oidc/embed','__AUTHORIZATION_ENDPOINT__':origin+'/oidc/app/authorize','__TOKEN_ENDPOINT__':origin+'/oidc/app/token','__USERINFO_ENDPOINT__':origin+'/oidc/app/userinfo','__REDIRECT_URI__':module.APP_CALLBACK}.items():
            template=template.replace(key,value)
        manifest=yaml.safe_load(template);manifest['app']['websiteUrl']='https://example.invalid/oidc/login'
        with self.assertRaisesRegex(ValueError,'app.websiteUrl 必须指向 /oidc/embed'):
            module.check_manifest_contract(manifest,origin)
    def test_embed_url_is_required_in_registration_config(self):
        config=self.fixture_config();del config['embedUrl']
        with tempfile.TemporaryDirectory() as temp:
            root=Path(temp);path=root/'config.json';path.write_text(json.dumps(config))
            with self.assertRaisesRegex(ValueError,'缺少配置字段：embedUrl'):
                module.assemble(path,root/'out')
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
