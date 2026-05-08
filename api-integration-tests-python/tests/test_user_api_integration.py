import json
import http.client
import os
import time
import unittest
import urllib.error
import urllib.request
from random import randint


# 默认经 Spring Cloud Gateway（compose 映射 8080）；user/stock/order 在宿主不再固定端口暴露时必用此法。
# 分项覆盖：GATEWAY_BASE_URL > API_BASE_URL > http://localhost:8080
# 仅调单服务（未走网关）时可设 USER_API_BASE_URL / STOCK_API_BASE_URL / ORDER_API_BASE_URL。
_GATEWAY = os.getenv("GATEWAY_BASE_URL") or os.getenv("API_BASE_URL") or "http://localhost:8080"
USER_API = f"{os.getenv('USER_API_BASE_URL', _GATEWAY)}/api/user"
STOCK_API = f"{os.getenv('STOCK_API_BASE_URL', _GATEWAY)}/api/stock/slots"
ORDER_API = f"{os.getenv('ORDER_API_BASE_URL', _GATEWAY)}/api/order"


def random_user():
    suffix = f"{int(time.time() * 1000)}_{randint(10000, 99999)}"
    return {
        "username": f"it_user_{suffix}",
        "password": "P@ssw0rd123",
        "nickname": f"IT-{suffix}",
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


class UserApiIntegrationTest(unittest.TestCase):
    def _log_case(self, module_name, feature_name, interfaces):
        print(f"\n[开始测试] 模块={module_name} | 功能={feature_name} | 接口={interfaces}")

    def _log_success(self, title, payload):
        print(f"[测试成功] {title} | 返回={json.dumps(payload, ensure_ascii=False)}")

    def _request_user(self, path, method="GET", payload=None, headers=None):
        return request_json(f"{USER_API}{path}", method=method, payload=payload, headers=headers)

    def _request_stock(self, path, method="GET", payload=None, headers=None):
        return request_json(f"{STOCK_API}{path}", method=method, payload=payload, headers=headers)

    def _request_order(self, path, method="GET", payload=None, headers=None):
        return request_json(f"{ORDER_API}{path}", method=method, payload=payload, headers=headers)

    def _register_and_login(self):
        user = random_user()
        register_status, register_data = self._request_user("/register", method="POST", payload=user)
        self.assertEqual(register_status, 201, msg=f"注册失败: status={register_status}, body={register_data}")
        login_status, login_data = self._request_user(
            "/login",
            method="POST",
            payload={"username": user["username"], "password": user["password"]},
        )
        self.assertEqual(login_status, 200, msg=f"登录失败: status={login_status}, body={login_data}")
        token = login_data["data"].get("accessToken")
        self.assertTrue(token, "登录返回缺少 accessToken")
        return user, register_data["data"]["id"], token

    def test_register_login_me_logout_flow(self):
        self._log_case(
            "user",
            "用户鉴权主流程",
            "POST /api/user/register -> POST /api/user/login -> GET /api/user/me -> POST /api/user/logout",
        )
        user, _, token = self._register_and_login()

        me_status, me_data = self._request_user("/me", method="GET", headers={"Authorization": f"Bearer {token}"})
        self.assertEqual(me_status, 200, msg=f"/me 访问失败: status={me_status}, body={me_data}")
        self.assertTrue(me_data.get("success"))
        self.assertEqual(me_data["data"]["username"], user["username"])

        logout_status, logout_data = self._request_user(
            "/logout",
            method="POST",
            headers={"Authorization": f"Bearer {token}"},
        )
        self.assertEqual(logout_status, 200, msg=f"logout 失败: status={logout_status}, body={logout_data}")
        self.assertTrue(logout_data.get("success"))

        me_again_status, me_again_data = self._request_user(
            "/me",
            method="GET",
            headers={"Authorization": f"Bearer {token}"},
        )
        self.assertEqual(me_again_status, 401, msg=f"退出后 token 仍可用: {me_again_data}")
        self.assertEqual(me_again_data["error"]["code"], "AUTH_TOKEN_INVALID")
        self._log_success("用户鉴权主流程", {"logout": logout_data, "me_after_logout": me_again_data})

    def test_register_should_reject_duplicate_username(self):
        self._log_case("user", "重复注册校验", "POST /api/user/register")
        user = random_user()
        first_status, _ = self._request_user("/register", method="POST", payload=user)
        self.assertEqual(first_status, 201)
        second_status, second_data = self._request_user("/register", method="POST", payload=user)
        self.assertEqual(second_status, 409, msg=f"重复用户名未返回409: {second_data}")
        self.assertEqual(second_data["error"]["code"], "USER_USERNAME_EXISTS")
        self._log_success("重复注册校验", second_data)

    def test_register_invalid_param_should_be_400(self):
        self._log_case("user", "注册参数校验", "POST /api/user/register")
        bad_status, bad_data = self._request_user(
            "/register",
            method="POST",
            payload={"username": "bad!", "password": "123", "nickname": "x"},
        )
        self.assertEqual(bad_status, 400, msg=f"非法注册参数未返回400: {bad_data}")
        self.assertEqual(bad_data["error"]["code"], "REQ_INVALID_PARAM")
        self._log_success("注册参数校验", bad_data)

    def test_login_with_wrong_password_should_be_401(self):
        self._log_case("user", "登录密码错误处理", "POST /api/user/login")
        user = random_user()
        self._request_user("/register", method="POST", payload=user)
        bad_login_status, bad_login_data = self._request_user(
            "/login",
            method="POST",
            payload={"username": user["username"], "password": "bad-password"},
        )
        self.assertEqual(bad_login_status, 401, msg=f"错误密码登录未返回 401: status={bad_login_status}, body={bad_login_data}")
        self.assertFalse(bad_login_data.get("success"))
        self.assertEqual(bad_login_data["error"]["code"], "AUTH_BAD_CREDENTIALS")
        self._log_success("登录密码错误处理", bad_login_data)

    def test_me_without_token_should_be_401(self):
        self._log_case("user", "鉴权缺失处理", "GET /api/user/me")
        status, data = self._request_user("/me", method="GET")
        self.assertEqual(status, 401, msg=f"未带token访问/me未返回401: {data}")
        self.assertEqual(data["error"]["code"], "AUTH_TOKEN_MISSING")
        self._log_success("鉴权缺失处理", data)

    def test_admin_ping_normal_user_should_be_403(self):
        self._log_case("user", "普通用户访问管理员接口拦截", "GET /api/user/admin/ping")
        _, _, token = self._register_and_login()
        status, data = self._request_user("/admin/ping", method="GET", headers={"Authorization": f"Bearer {token}"})
        self.assertEqual(status, 403, msg=f"普通用户访问admin接口应403: {data}")
        self.assertEqual(data["error"]["code"], "AUTH_FORBIDDEN")
        self._log_success("普通用户访问管理员接口拦截", data)

    def test_user_crud_and_balance_flow(self):
        self._log_case(
            "user",
            "用户资料与余额变更",
            "GET/PUT/DELETE /api/user/{id} + POST /api/user/{id}/recharge + POST /api/user/{id}/deduct",
        )
        user, user_id, _ = self._register_and_login()

        query_status, query_data = self._request_user(f"/{user_id}", method="GET")
        self.assertEqual(query_status, 200)
        self.assertTrue(query_data.get("success"))
        self.assertEqual(query_data["data"]["username"], user["username"])

        update_status, update_data = self._request_user(
            f"/{user_id}",
            method="PUT",
            payload={"nickname": "UpdatedNick"},
        )
        self.assertEqual(update_status, 200, msg=f"更新用户失败: {update_data}")
        self.assertTrue(update_data.get("success"))
        self.assertEqual(update_data["data"]["nickname"], "UpdatedNick")

        recharge_status, recharge_data = self._request_user(
            f"/{user_id}/recharge",
            method="POST",
            payload={"amount": "25.50"},
        )
        self.assertEqual(recharge_status, 200, msg=f"充值失败: {recharge_data}")
        self.assertTrue(recharge_data.get("success"))

        deduct_status, deduct_data = self._request_user(
            f"/{user_id}/deduct",
            method="POST",
            payload={"amount": "10.00"},
        )
        self.assertEqual(deduct_status, 200, msg=f"扣款失败: {deduct_data}")
        self.assertTrue(deduct_data.get("success"))

        delete_status, delete_data = self._request_user(f"/{user_id}", method="DELETE")
        self.assertEqual(delete_status, 200, msg=f"删除用户失败: {delete_data}")
        self.assertTrue(delete_data.get("success"))

        query_deleted_status, query_deleted_data = self._request_user(f"/{user_id}", method="GET")
        self.assertEqual(
            query_deleted_status,
            200,
            msg=f"删除后查询接口异常: status={query_deleted_status}, body={query_deleted_data}",
        )
        self.assertFalse(query_deleted_data.get("success"))
        self.assertIsNone(query_deleted_data.get("data"))
        self._log_success(
            "用户资料与余额变更",
            {"update": update_data, "recharge": recharge_data, "deduct": deduct_data, "delete": delete_data},
        )

    def test_stock_slot_preheat_should_support_overwrite_and_validation(self):
        self._log_case("stock", "slot 预热与覆盖策略", "POST /api/stock/slots/preheat")
        redis_key = f"stock:test:{int(time.time() * 1000)}"

        first_status, first_data = self._request_stock(
            "/preheat",
            method="POST",
            payload={"slotId": "A", "redisKey": redis_key, "quantity": 7, "overwrite": False},
        )
        self.assertEqual(first_status, 200, msg=f"首次预热失败: {first_data}")
        self.assertTrue(first_data.get("written"))
        self.assertEqual(first_data.get("currentValue"), "7")

        second_status, second_data = self._request_stock(
            "/preheat",
            method="POST",
            payload={"slotId": "A", "redisKey": redis_key, "quantity": 9, "overwrite": False},
        )
        self.assertEqual(second_status, 200, msg=f"二次预热失败: {second_data}")
        self.assertFalse(second_data.get("written"))
        self.assertEqual(second_data.get("currentValue"), "7")

        overwrite_status, overwrite_data = self._request_stock(
            "/preheat",
            method="POST",
            payload={"slotId": "A", "redisKey": redis_key, "quantity": 11, "overwrite": True},
        )
        self.assertEqual(overwrite_status, 200, msg=f"覆盖预热失败: {overwrite_data}")
        self.assertTrue(overwrite_data.get("written"))
        self.assertEqual(overwrite_data.get("currentValue"), "11")

        invalid_status, invalid_data = self._request_stock(
            "/preheat",
            method="POST",
            payload={"slotId": "A", "redisKey": redis_key, "quantity": 0, "overwrite": True},
        )
        self.assertEqual(invalid_status, 400, msg=f"非法预热参数未返回400: {invalid_data}")
        self.assertIn("message", invalid_data)
        self._log_success(
            "slot 预热与覆盖策略",
            {"first": first_data, "second": second_data, "overwrite": overwrite_data, "invalid": invalid_data},
        )

    def test_order_snag_query_and_update_status_flow(self):
        self._log_case(
            "order",
            "抢单到订单状态更新",
            "POST /api/order/snag -> GET /api/order/{orderId} -> PUT /api/order/{orderId}/status",
        )
        bucket_1 = "stock:bucket-1"
        bucket_2 = "stock:bucket-2"
        self._request_stock("/preheat", method="POST", payload={"slotId": "bucket-1", "redisKey": bucket_1, "quantity": 50, "overwrite": True})
        self._request_stock("/preheat", method="POST", payload={"slotId": "bucket-2", "redisKey": bucket_2, "quantity": 50, "overwrite": True})

        user, _, _ = self._register_and_login()
        snag_status, snag_data = self._request_order("/snag", method="POST", payload={"userId": str(user["username"])})
        self.assertEqual(snag_status, 200, msg=f"下单接口异常: {snag_data}")
        self.assertTrue(snag_data.get("success"), msg=f"下单失败: {snag_data}")
        order_id = snag_data["data"].get("orderId")
        self.assertTrue(order_id, "下单成功但未返回 orderId")

        time.sleep(2)
        order_status = None
        order_data = None
        for _ in range(30):
            order_status, order_data = self._request_order(f"/{order_id}", method="GET")
            if order_status == 200 and order_data.get("success"):
                break
            time.sleep(1)
        self.assertEqual(order_status, 200, msg=f"查询订单失败: {order_data}")
        self.assertTrue(order_data.get("success"), msg=f"订单查询未成功: {order_data}")
        self.assertEqual(order_data["data"]["orderId"], order_id)

        update_status, update_data = self._request_order(
            f"/{order_id}/status",
            method="PUT",
            payload={"status": "PAID"},
        )
        self.assertEqual(update_status, 200, msg=f"更新订单状态失败: {update_data}")
        self.assertTrue(update_data.get("success"))
        self.assertEqual(update_data["data"]["status"], "PAID")
        self._log_success(
            "抢单到订单状态更新",
            {"snag": snag_data, "query": order_data, "update": update_data},
        )

if __name__ == "__main__":
    unittest.main()
