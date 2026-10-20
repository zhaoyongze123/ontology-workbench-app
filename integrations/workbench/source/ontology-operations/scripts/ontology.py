#!/usr/bin/env python3
"""按固定操作目录调用服务；输入通过 stdin JSON，避免参数拼接。"""
from pathlib import Path
import json,sys,math,re
from runtime import AppError,request,request_userinfo,status
CATALOG=json.loads((Path(__file__).parent.parent/'references/operations.json').read_text())

def validate(value,schema,where):
    # 精确保留大整数 ID；后端接受十进制字符串，不经浮点数转换。
    if '$ref' in schema:
        schema=CATALOG['schemas'][schema['$ref'].split('/')[-1]]
    kind=schema.get('type')
    if kind=='object':
        if not isinstance(value,dict):raise AppError(where+' 必须为对象')
        props=schema.get('properties',{})
        if set(value)-set(props):raise AppError(where+' 包含未声明字段')
        for name in schema.get('required',[]):
            if name not in value or value[name] is None:raise AppError(where+'.'+name+' 缺失')
        for key,item in value.items():
            if item is not None:validate(item,props[key],where+'.'+key)
    elif kind=='array':
        if not isinstance(value,list):raise AppError(where+' 必须为数组')
        for item in value:validate(item,schema.get('items',{}),where+'[]')
    elif kind=='integer':
        if isinstance(value,bool) or not (isinstance(value,int) or isinstance(value,str) and re.fullmatch(r'[0-9]+',value)):
            raise AppError(where+' 必须为整数或十进制 ID 字符串')
    elif kind=='number':
        if isinstance(value,bool) or not isinstance(value,(float,int)) or not math.isfinite(value):raise AppError(where+' 必须为有限数值')
    elif kind=='string' and not isinstance(value,str):raise AppError(where+' 必须为字符串')
    elif kind=='boolean' and not isinstance(value,bool):raise AppError(where+' 必须为布尔值')
    if 'enum' in schema and value not in schema['enum']:raise AppError(where+' 不在允许值内')

def execute(payload):
    if not isinstance(payload,dict):raise AppError('输入必须为 JSON 对象')
    if set(payload)-{'operation','query','body','_confirm'}:raise AppError('输入包含未声明控制字段')
    name=payload.get('operation')
    if name=='status':
        if set(payload)-{'operation'}:raise AppError('status 不接受业务参数')
        return status()
    if name=='identity':
        if set(payload)!={'operation'}:raise AppError('identity 不接受业务参数')
        return request_userinfo()
    if name=='open':
        if set(payload)-{'operation'}:raise AppError('open 不接受业务参数')
        from runtime import origin,CONFIG
        return {'url':origin()+CONFIG['pagePath']}
    op=CATALOG['operations'].get(name)
    if op is None:raise AppError('未支持的 Ontology 操作')
    if op['mutating'] and payload.get('_confirm') is not True:raise AppError('写操作需要用户明确授权并设置 _confirm=true')
    query=payload.get('query',{})
    if not isinstance(query,dict):raise AppError('query 必须为对象')
    params={p['name']:p for p in op.get('queryParameters',[])}
    if set(query)-set(params):raise AppError('query 包含未声明字段')
    for key,param in params.items():
        if param.get('required') and key not in query:raise AppError('query.'+key+' 缺失')
        if key in query:validate(query[key],param.get('schema',{}),'query.'+key)
    body=payload.get('body')
    if op.get('fixedPreview'):
        validate(body,{'type':'object','required':['graphId','graphType'],'properties':{'graphId':{'type':'integer'},'graphType':{'type':'string','enum':['ONTOLOGY','ENTITY']}}},'body')
        body={**body,'cypher':'MATCH (n) RETURN n LIMIT 100'}
    elif op.get('bodySchema'):validate(body,op['bodySchema'],'body')
    elif body is not None:raise AppError('此操作不接受 body')
    return request(op['method'],op['path'],query,body)

def main():
    try:
        raw=sys.stdin.read(1024*1024+1)
        if len(raw)>1024*1024:raise AppError('输入超过限制')
        result=execute(json.loads(raw))
        print(json.dumps(result,ensure_ascii=False,allow_nan=False))
        return 0
    except (AppError,ValueError,TypeError) as e:
        # 不输出输入内容、Token、原始异常堆栈或上游响应正文。
        message=str(e) if isinstance(e,AppError) else '输入或服务响应格式无效'
        print(json.dumps({'success':False,'message':message},ensure_ascii=False))
        return 1
if __name__=='__main__':raise SystemExit(main())
