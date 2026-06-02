# Python API Integration Tests

This directory contains black-box integration tests for services that are already running locally or in Docker Compose.

## Covered Flows

- User API: register, login, current user, invalid login.
- Business flow: user login, stock preheat, order creation and status update.
- Product API: CRUD, repeated reads for cache path, list query, stock aggregation, status switch, metrics snapshot.

## Run

From the repository root:

```powershell
python -m unittest discover -s api-integration-tests-python/tests -p "test_*.py" -v
```

Run product checks only:

```powershell
python -m unittest discover -s api-integration-tests-python/tests -p "test_product_api_integration.py" -v
```

## Environment Variables

- `GATEWAY_BASE_URL`: Spring Cloud Gateway base URL, default `http://localhost:8080`.
- `API_BASE_URL`: Backward-compatible alias for `GATEWAY_BASE_URL`.
- `USER_API_BASE_URL`: Direct user service base URL when bypassing gateway.
- `STOCK_API_BASE_URL`: Direct stock service base URL when bypassing gateway.
- `ORDER_API_BASE_URL`: Direct order service base URL when bypassing gateway.
- `PRODUCT_API_BASE_URL`: Direct product service base URL when bypassing gateway.

Example:

```powershell
$env:GATEWAY_BASE_URL="http://localhost:8080"
python -m unittest discover -s api-integration-tests-python/tests -p "test_product_api_integration.py" -v
```
