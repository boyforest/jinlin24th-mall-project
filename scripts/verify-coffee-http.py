#!/usr/bin/env python3
"""只由一次性 MySQL 验证脚本启动，真实 HTTP/Redis 会话验证；不输出 token/密码。"""
import datetime
import hashlib
import json
import pathlib
import subprocess
import sys
import urllib.error
import urllib.request

base, jar = sys.argv[1:]
assert base.startswith('http://127.0.0.1:'), '只允许本机临时服务'
checks = []

def call(method, path, status=200, body=None, token=None):
    headers = {'Content-Type': 'application/json'}
    if token:
        headers['Authorization'] = 'Bearer ' + token
    request = urllib.request.Request(base + path, data=None if body is None else json.dumps(body).encode(), headers=headers, method=method)
    try:
        response = urllib.request.urlopen(request, timeout=15)
    except urllib.error.HTTPError as e:
        response = e
    payload = json.load(response)
    checks.append({'method': method, 'path': path, 'status': response.code, 'code': payload.get('code')})
    assert response.code == status, f'{method} {path}: expected {status}, got {response.code}'
    if status == 200:
        assert payload['code'] == 0, f'{path}: business error'
    return payload.get('data')

super_login = call('POST', '/admin/login', body={'username': 'admin', 'password': '123123'})
call('GET', '/admin/auth/context', 403, token=super_login['token'])
super_token = call('PUT', '/admin/password', body={'oldPassword': '123123', 'newPassword': 'test-only-super-password'}, token=super_login['token'])['token']
context = call('GET', '/admin/auth/context', token=super_token)
assert context['roles'] == ['SUPER_ADMIN'] and isinstance(context['adminId'], str)
store = call('GET', '/user/coffee/store')
assert store['capabilities']['placeOrder'] is False
sid = store['id']
call('GET', '/user/coffee/menu?storeId=' + sid)
accounts = {}
for username, role in [('live-coffee', 'COFFEE_ADMIN'), ('live-goods', 'GOODS_ADMIN')]:
    body = {'username': username, 'temporaryPassword': 'test-only-temporary', 'roleCode': role}
    if role == 'COFFEE_ADMIN':
        body['storeId'] = sid
    account = call('POST', '/admin/accounts', body=body, token=super_token)
    assert account['mustChangePwd'] is True and 'passwordHash' not in account
    login = call('POST', '/admin/login', body={'username': username, 'password': 'test-only-temporary'})
    token = call('PUT', '/admin/password', body={'oldPassword': 'test-only-temporary', 'newPassword': 'test-only-new-password'}, token=login['token'])['token']
    accounts[role] = (account['id'], token)
coffee_id, coffee_token = accounts['COFFEE_ADMIN']
_, goods_token = accounts['GOODS_ADMIN']
assert call('GET', '/admin/auth/context', token=coffee_token)['defaultRoute'] == '/coffee/workbench'
assert call('GET', '/admin/auth/context', token=goods_token)['defaultRoute'] == '/dashboard'
call('GET', '/admin/user/list', 403, token=coffee_token)
call('GET', '/admin/user/list', token=goods_token)
call('GET', '/admin/coffee/context', 403, token=goods_token)
call('GET', '/admin/marketing/activity/list', 403, token=goods_token)
call('GET', '/admin/accounts/list', 403, token=coffee_token)
call('GET', '/admin/coffee/menu?storeId=' + sid, token=coffee_token)
workbench = call('GET', '/admin/coffee/workbench?storeId=' + sid, token=coffee_token)
assert len(workbench['columns']) == 4
page = call('GET', '/admin/coffee/orders/list?storeId=' + sid, token=coffee_token)
assert page['records'] == []
call('GET', '/admin/coffee/orders/list?storeId=' + sid + '&size=-1', 400, token=coffee_token)
call('GET', '/admin/coffee/ledger?storeId=' + sid + '&dateFrom=2026-10-26&dateTo=2026-10-26', token=coffee_token)
call('POST', '/admin/accounts/' + coffee_id + '/reset-password', body={'temporaryPassword': 'test-only-reset'}, token=super_token)
call('GET', '/admin/auth/context', 401, token=coffee_token)
call('GET', '/user/marketing/activity/list?position=home_recommend')
call('GET', '/user/business-order/list', 401)
call('POST', '/user/coffee/orders', 401, body={})
call('POST', '/api/coffee/payment/notify', 503, body={})
call('POST', '/api/coffee/refund/notify', 503, body={})
record = {'verifiedAt': datetime.datetime.now(datetime.timezone.utc).isoformat(), 'revision': subprocess.check_output(['git', 'describe', '--always', '--dirty'], text=True).strip(), 'jarSha256': hashlib.sha256(pathlib.Path(jar).read_bytes()).hexdigest(), 'applicationVersion': '1.0.0', 'profile': 'prod', 'channelEnabled': False, 'checks': checks}
output = pathlib.Path('.run/coffee-workbench-http.json')
output.parent.mkdir(exist_ok=True)
output.write_text(json.dumps(record, ensure_ascii=False, indent=2) + '\n')
print(f'真实 HTTP：{len(checks)} 项通过，版本 1.0.0，记录 {output}（无 token/密码）。')
