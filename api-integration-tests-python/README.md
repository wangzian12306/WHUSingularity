# Python API 集成测试小项目

这个目录是一个独立的 Python 集成测试项目，用于测试已启动容器中的 `singularity-user` API。

## 测试覆盖

- `POST /api/user/register` 注册
- `POST /api/user/login` 登录
- `GET /api/user/me` 鉴权用户信息查询
- 错误密码登录返回 401

## 目录结构

```text
api-integration-tests-python/
  ├─ README.md
  └─ tests/
     └─ test_user_api_integration.py
```

## 运行方式

在仓库根目录执行：

```powershell
python -m unittest discover -s api-integration-tests-python/tests -p "test_*.py" -v
```

## 可选环境变量

- `GATEWAY_BASE_URL`：Spring Cloud Gateway 根地址，默认 `http://localhost:8080`（与 compose 中 `8080:8080` 一致）。
- `API_BASE_URL`：与 `GATEWAY_BASE_URL` 二选一，作为网关根地址（兼容旧说明）。
- 直连某微服务（不经网关）时：`USER_API_BASE_URL`、`STOCK_API_BASE_URL`、`ORDER_API_BASE_URL`。

例如：

```powershell
$env:GATEWAY_BASE_URL="http://localhost:8080"
python -m unittest discover -s api-integration-tests-python/tests -p "test_*.py" -v
```
