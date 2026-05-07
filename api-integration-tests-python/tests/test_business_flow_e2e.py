import json
import http.client
import os
import time
import unittest
import urllib.error
import urllib.request
from random import randint


_GATEWAY = os.getenv("GATEWAY_BASE_URL") or os.getenv("API_BASE_URL") or "http://localhost:8080"
USER_API = f"{os.getenv('USER_API_BASE_URL', _GATEWAY)}/api/user"
STOCK_API = f"{os.getenv('STOCK_API_BASE_URL', _GATEWAY)}/api/stock/slots"
ORDER_API = f"{os.getenv('ORDER_API_BASE_URL', _GATEWAY)}/api/order"


def random_user():
    suffix = f"{int(time.time() * 1000)}_{randint(10000, 99999)}"
    return {
        "username": f"e2e_user_{suffix}",
        "password": "P@ssw0rd123",
        "nickname": f"E2E-{suffix}",
    }


def request_json(url, method="GET", payload=None, headers=None):
    body = None if payload is None else json.dumps(payload).encode("utf-8")
    req_headers = {"Content-Type": "application/json"}
    if headers:
        req_headers.update(headers)

    retryable_errors = (
        urllib.error.URLError,
        ConnectionResetError,
        ConnectionAbortedError,
        http.client.RemoteDisconnected,
    )

    for attempt in range(5):
        request = urllib.request.Request(url=url, data=body, method=method, headers=req_headers)
        try:
            with urllib.request.urlopen(request, timeout=10) as response:
                content = response.read().decode("utf-8") if response.length != 0 else ""
                return response.status, json.loads(content) if content else None
        except urllib.error.HTTPError as http_error:
            content = http_error.read().decode("utf-8")
            parsed = json.loads(content) if content else None
            return http_error.code, parsed
        except retryable_errors:
            if attempt == 4:
                raise
            time.sleep(1)


class BusinessFlowE2ETest(unittest.TestCase):
    def _log_stage(self, stage_name, detail):
        print(f"\n[流程阶段] {stage_name} | {detail}")

    def _log_success(self, stage_name, payload):
        print(f"[阶段成功] {stage_name} | 返回={json.dumps(payload, ensure_ascii=False)}")

    def _request_user(self, path, method="GET", payload=None, headers=None):
        return request_json(f"{USER_API}{path}", method=method, payload=payload, headers=headers)

    def _request_stock(self, path, method="GET", payload=None, headers=None):
        return request_json(f"{STOCK_API}{path}", method=method, payload=payload, headers=headers)

    def _request_order(self, path, method="GET", payload=None, headers=None):
        return request_json(f"{ORDER_API}{path}", method=method, payload=payload, headers=headers)

    def _wait_order_ready(self, order_id, max_wait_seconds=30):
        order_status = None
        order_data = None
        for _ in range(max_wait_seconds):
            order_status, order_data = self._request_order(f"/{order_id}", method="GET")
            if order_status == 200 and order_data.get("success"):
                return order_data
            time.sleep(1)
        self.fail(f"订单在 {max_wait_seconds}s 内未可查询: status={order_status}, body={order_data}")

    def test_e2e_business_flow(self):
        self._log_stage(
            "总览",
            "注册登录 -> 查询本人 -> 充值 -> 预热库存 -> 抢单 -> 查单 -> 更新状态 -> 登出",
        )
        user = random_user()

        self._log_stage("用户注册", "POST /api/user/register")
        register_status, register_data = self._request_user("/register", method="POST", payload=user)
        self.assertEqual(register_status, 201, msg=f"注册失败: status={register_status}, body={register_data}")
        self.assertTrue(register_data.get("success"))
        user_id = register_data["data"]["id"]
        self._log_success("用户注册", register_data)

        self._log_stage("用户登录", "POST /api/user/login")
        login_status, login_data = self._request_user(
            "/login",
            method="POST",
            payload={"username": user["username"], "password": user["password"]},
        )
        self.assertEqual(login_status, 200, msg=f"登录失败: status={login_status}, body={login_data}")
        self.assertTrue(login_data.get("success"))
        token = login_data["data"].get("accessToken")
        self.assertTrue(token, "登录返回缺少 accessToken")
        self._log_success("用户登录", login_data)

        auth_header = {"Authorization": f"Bearer {token}"}

        self._log_stage("查询当前用户", "GET /api/user/me")
        me_status, me_data = self._request_user("/me", method="GET", headers=auth_header)
        self.assertEqual(me_status, 200, msg=f"/me 失败: status={me_status}, body={me_data}")
        self.assertTrue(me_data.get("success"))
        self.assertEqual(me_data["data"]["username"], user["username"])
        self._log_success("查询当前用户", me_data)

        self._log_stage("账户充值", "POST /api/user/{id}/recharge")
        recharge_status, recharge_data = self._request_user(
            f"/{user_id}/recharge",
            method="POST",
            payload={"amount": "100.00"},
        )
        self.assertEqual(recharge_status, 200, msg=f"充值失败: status={recharge_status}, body={recharge_data}")
        self.assertTrue(recharge_data.get("success"))
        self._log_success("账户充值", recharge_data)

        self._log_stage("库存预热", "POST /api/stock/slots/preheat")
        redis_key_1 = f"stock:e2e:bucket-1:{int(time.time() * 1000)}"
        redis_key_2 = f"stock:e2e:bucket-2:{int(time.time() * 1000)}"
        preheat_1_status, preheat_1_data = self._request_stock(
            "/preheat",
            method="POST",
            payload={"slotId": "bucket-1", "redisKey": redis_key_1, "quantity": 20, "overwrite": True},
        )
        preheat_2_status, preheat_2_data = self._request_stock(
            "/preheat",
            method="POST",
            payload={"slotId": "bucket-2", "redisKey": redis_key_2, "quantity": 20, "overwrite": True},
        )
        self.assertEqual(preheat_1_status, 200, msg=f"bucket-1 预热失败: {preheat_1_data}")
        self.assertEqual(preheat_2_status, 200, msg=f"bucket-2 预热失败: {preheat_2_data}")
        self.assertTrue(preheat_1_data.get("written"))
        self.assertTrue(preheat_2_data.get("written"))
        self._log_success("库存预热", {"bucket-1": preheat_1_data, "bucket-2": preheat_2_data})

        self._log_stage("创建订单", "POST /api/order/snag")
        snag_status, snag_data = self._request_order("/snag", method="POST", payload={"userId": str(user["username"])})
        self.assertEqual(snag_status, 200, msg=f"下单接口失败: status={snag_status}, body={snag_data}")
        self.assertTrue(snag_data.get("success"), msg=f"下单业务失败: {snag_data}")
        order_id = snag_data["data"].get("orderId")
        self.assertTrue(order_id, "下单成功但未返回 orderId")
        self._log_success("创建订单", snag_data)

        self._log_stage("查询订单", "GET /api/order/{orderId}")
        order_data = self._wait_order_ready(order_id, max_wait_seconds=30)
        self.assertEqual(order_data["data"]["orderId"], order_id)
        self._log_success("查询订单", order_data)

        self._log_stage("更新订单状态", "PUT /api/order/{orderId}/status")
        update_status, update_data = self._request_order(
            f"/{order_id}/status",
            method="PUT",
            payload={"status": "PAID"},
        )
        self.assertEqual(update_status, 200, msg=f"更新订单状态失败: status={update_status}, body={update_data}")
        self.assertTrue(update_data.get("success"))
        self.assertEqual(update_data["data"]["status"], "PAID")
        self._log_success("更新订单状态", update_data)

        self._log_stage("用户登出", "POST /api/user/logout")
        logout_status, logout_data = self._request_user("/logout", method="POST", headers=auth_header)
        self.assertEqual(logout_status, 200, msg=f"登出失败: status={logout_status}, body={logout_data}")
        self.assertTrue(logout_data.get("success"))
        self._log_success("用户登出", logout_data)


if __name__ == "__main__":
    unittest.main()
