#!/usr/bin/env python3
"""验证项目内图谱只读权限与邀请接受后的授权；仅使用受控测试账号。"""
import json
from pathlib import Path
from smoke_client import Browser

origin='http://127.0.0.1:13002'
# 固定副本端口，避免测试误连原应用或外部服务。
browsers={}
for name in ['test_project_owner','test_project_editor','test_project_viewer','test_no_project','test_isolation_owner','admin']:
 b=Browser(origin);b.login(name,name);browsers[name]=b
projects=browsers['test_project_owner'].request('/organization/page')[1]['data']
project=str(next(p['id'] for p in projects if p['name']=='Ontology-测试-角色协作'))
checks=[]
def check(n,status,expected):
 assert status==expected,(n,status,expected)
 checks.append(n)
owner=browsers['test_project_owner'];editor=browsers['test_project_editor'];viewer=browsers['test_project_viewer'];outsider=browsers['test_no_project']
status,body,_=editor.request('/graph/save','POST',{'name':'AclGraphProbe','description':'隔离副本临时图谱','graphType':'ONTOLOGY','organizationId':project})
check('editor_create_graph',status,200)
gid=str(body['data']['id']);query={'graphId':gid,'graphType':'ONTOLOGY','cypher':'MATCH (n) RETURN count(n) AS total'}
try:
 status,result,_=viewer.request('/cypher/query','POST',query);check('viewer_current_graph_query',status,200)
 assert str(result['data'][0]['total'])=='0', '新图谱必须为空'
 check('viewer_export',viewer.request('/graph/export/json?graphType=ONTOLOGY&graphId='+gid)[0],200)
 check('outsider_graph_read_denied',outsider.request('/graph/getInfo?graphType=ONTOLOGY&graphId='+gid)[0],403)
 check('isolation_owner_query_denied',browsers['test_isolation_owner'].request('/cypher/query','POST',query)[0],403)
 check('editor_graph_delete_denied',editor.request('/graph/delete?graphType=ONTOLOGY&graphId='+gid+'&organizationId='+project,'DELETE')[0],403)
 for statement in ['CREATE (n)','MATCH (n) DELETE n','RETURN pg_sleep(1) AS delay','RETURN 1 AS a; RETURN 2 AS b']:
  check('readonly_reject_'+str(len(checks)),browsers['admin'].request('/cypher/query','POST',{**query,'cypher':statement})[0],400)
finally:
 check('owner_delete_temporary_graph',owner.request('/graph/delete?graphType=ONTOLOGY&graphId='+gid+'&organizationId='+project,'DELETE')[0],200)
uid=outsider.request('/auth/me')[1]['id']
status,inv,_=owner.request('/projects/'+project+'/invitations','POST',{'targetUserId':uid,'role':'VIEWER'});check('owner_invites_registered_user',status,200)
check('invite_not_membership',outsider.request('/projects/'+project+'/access')[0],403)
status,accepted,_=outsider.request('/project-invitations/'+inv['data']['id']+'/accept','POST');check('invite_accept',status,200)
try:
 status,access,_=outsider.request('/projects/'+project+'/access');check('accepted_viewer_access',status,200);assert access['data']['role']=='VIEWER'
finally:
 check('member_leave',outsider.request('/projects/'+project+'/leave','POST')[0],200)
check('leave_revokes_access',outsider.request('/projects/'+project+'/access')[0],403)
Path('output/http-acl').mkdir(parents=True,exist_ok=True)
Path('output/http-acl/report.json').write_text(json.dumps({'checks':checks,'passed':True},ensure_ascii=False,indent=2))
print('真实图谱与邀请验收通过：',len(checks),'项')
