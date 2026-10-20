"""离线核验操作边界，不连接真实服务，也不使用真实凭证。"""
from pathlib import Path
import sys,json,os,unittest
from unittest.mock import patch,MagicMock
SCRIPTS=Path(__file__).resolve().parents[1]/'source/ontology-operations/scripts'
sys.path.insert(0,str(SCRIPTS))
import ontology,runtime

class Contracts(unittest.TestCase):
    def test_status_does_not_verify_remote(self):
        with patch.dict(os.environ,{},clear=True):
            result=ontology.execute({'operation':'status'})
        self.assertFalse(result['configured']);self.assertFalse(result['verified'])
    def test_status_never_returns_token(self):
        with patch.dict(os.environ,{'APP_OAUTH_ONTOLOGY_ACCESS_TOKEN':'offline-fixture'},clear=True):
            result=ontology.execute({'operation':'status'})
        self.assertTrue(result['configured']);self.assertNotIn('offline-fixture',json.dumps(result))
    def test_missing_token_refuses_http(self):
        with patch.dict(os.environ,{},clear=True),patch('runtime.urllib.request.build_opener') as opener:
            with self.assertRaises(runtime.AppError):runtime.request('GET','/organization/page',{},None)
            opener.assert_not_called()
    def test_unlisted_operation(self):
        with self.assertRaises(runtime.AppError):ontology.execute({'operation':'query'})
    def test_mutation_requires_confirmation(self):
        with self.assertRaises(runtime.AppError):ontology.execute({'operation':'saveOrganization','body':{'name':'测试'}})
    def test_missing_required_field(self):
        with self.assertRaises(runtime.AppError):ontology.execute({'operation':'saveOrganization','_confirm':True,'body':{}})
    def test_read_post_no_confirmation(self):
        with patch('ontology.request',return_value={'success':True}) as request:
            ontology.execute({'operation':'pageNode','body':{'graphId':'2112092892498628608','graphType':'ENTITY'}})
            self.assertEqual(request.call_args.args[0],'POST')
    def test_big_id_stays_string(self):
        value='2112092892498628608'
        with patch('ontology.request',return_value={'success':True}) as request:
            ontology.execute({'operation':'getGraphInfo','query':{'graphId':value,'graphType':'ENTITY'}})
            self.assertEqual(request.call_args.args[2]['graphId'],value)
    def test_big_id_array_stays_decimal_string(self):
        value='2112092892498628608'
        with patch('ontology.request',return_value={'success':True}) as request:
            ontology.execute({'operation':'deleteNode','_confirm':True,'body':{'idList':[value],'graphId':value,'graphType':'ENTITY'}})
            self.assertEqual(request.call_args.args[3]['idList'],[value])
            self.assertEqual(request.call_args.args[3]['graphId'],value)
    def test_float_id_refused(self):
        with self.assertRaises(runtime.AppError):ontology.execute({'operation':'getGraphInfo','query':{'graphId':2.1e18,'graphType':'ENTITY'}})
    def test_invalid_graph_type(self):
        with self.assertRaises(runtime.AppError):ontology.execute({'operation':'getGraphInfo','query':{'graphId':'1','graphType':'OTHER'}})
    def test_unknown_query_field(self):
        with self.assertRaises(runtime.AppError):ontology.execute({'operation':'organizationPage','query':{'sql':'DROP'}})
    def test_unknown_body_field(self):
        with self.assertRaises(runtime.AppError):ontology.execute({'operation':'saveOrganization','_confirm':True,'body':{'name':'测试','extra':1}})
    def test_raw_cypher_not_accepted(self):
        with self.assertRaises(runtime.AppError):ontology.execute({'operation':'previewGraph','body':{'graphId':'1','graphType':'ENTITY','cypher':'DELETE n'}})
    def test_fixed_preview_query(self):
        with patch('ontology.request',return_value={'success':True}) as request:
            ontology.execute({'operation':'previewGraph','body':{'graphId':'1','graphType':'ENTITY'}})
            self.assertEqual(request.call_args.args[3]['cypher'],'MATCH (n) RETURN n LIMIT 100')
    def test_cross_origin_config_refused(self):
        with patch.dict(runtime.CONFIG,{'serviceOrigin':'https://host.invalid/path'}):
            with self.assertRaises(runtime.AppError):runtime.origin()
    def test_redirect_not_followed(self):
        self.assertIsNone(runtime.NoRedirect().redirect_request(None,None,302,'',{},'https://elsewhere.invalid'))
    def test_business_failure_refused(self):
        response=MagicMock();response.__enter__.return_value=response
        response.read.return_value=b'{"success":false,"message":"offline"}'
        with patch.dict(os.environ,{'APP_OAUTH_ONTOLOGY_ACCESS_TOKEN':'offline-fixture'},clear=True),patch('runtime.urllib.request.build_opener') as opener:
            opener.return_value.open.return_value=response
            with self.assertRaises(runtime.AppError):runtime.request('GET','/organization/page',{},None)
    def test_pagination_wrapper_preserved(self):
        value={'success':True,'data':[],'addition':{'total':12}}
        with patch('ontology.request',return_value=value):
            self.assertEqual(ontology.execute({'operation':'organizationPage'})['addition']['total'],12)
    def test_identity_uses_broker_and_whitelists_unicode_identity(self):
        response=MagicMock();response.__enter__.return_value=response
        response.read.return_value=json.dumps({'id':2112092892498628608,'username':'alice','sub':'subject-1','name':'林青','access_token':'must-not-return'}).encode()
        with patch.dict(os.environ,{'APP_OAUTH_ONTOLOGY_ACCESS_TOKEN':'offline-fixture'}),patch('runtime.urllib.request.build_opener') as opener:
            opener.return_value.open.return_value=response
            result=ontology.execute({'operation':'identity'})
        self.assertEqual(result,{'id':'2112092892498628608','username':'alice','sub':'subject-1','name':'林青'})
        request=opener.return_value.open.call_args.args[0]
        self.assertTrue(request.full_url.endswith('/oidc/app/userinfo'))
        self.assertEqual(request.get_header('Authorization'),'Bearer offline-fixture')
        self.assertNotIn('access_token',result)
    def test_identity_allows_optional_name_without_fabricating_it(self):
        response=MagicMock();response.__enter__.return_value=response
        response.read.return_value=b'{"id":"user-7","username":"bob","sub":"subject-7"}'
        with patch.dict(os.environ,{'APP_OAUTH_ONTOLOGY_ACCESS_TOKEN':'offline-fixture'}),patch('runtime.urllib.request.build_opener') as opener:
            opener.return_value.open.return_value=response
            result=ontology.execute({'operation':'identity'})
        self.assertEqual(result,{'id':'user-7','username':'bob','sub':'subject-7'})
    def test_identity_requires_injected_app_bearer(self):
        with patch.dict(os.environ,{},clear=True),patch('runtime.urllib.request.build_opener') as opener:
            with self.assertRaises(runtime.AppError):ontology.execute({'operation':'identity'})
            opener.assert_not_called()
if __name__=='__main__':unittest.main()
