import json
import os
import time
import unittest
import urllib.error
import urllib.request
from random import randint


_GATEWAY = os.getenv("GATEWAY_BASE_URL") or os.getenv("API_BASE_URL") or "http://localhost:8080"
PRODUCT_API = f"{os.getenv('PRODUCT_API_BASE_URL', _GATEWAY)}/api/product"


def request_json(url, method="GET", payload=None):
    body = None if payload is None else json.dumps(payload).encode("utf-8")
    request = urllib.request.Request(
        url=url,
        data=body,
        method=method,
        headers={"Content-Type": "application/json"},
    )
    try:
        with urllib.request.urlopen(request, timeout=10) as response:
            content = response.read().decode("utf-8") if response.length != 0 else ""
            return response.status, json.loads(content) if content else None
    except urllib.error.HTTPError as http_error:
        content = http_error.read().decode("utf-8")
        parsed = json.loads(content) if content else None
        return http_error.code, parsed


def unique_product_id():
    suffix = f"{int(time.time() * 1000)}_{randint(10000, 99999)}"
    return f"prod_it_{suffix}"


class ProductApiIntegrationTest(unittest.TestCase):

    def test_product_crud_cache_status_metrics_flow(self):
        product_id = unique_product_id()
        create_payload = {
            "productId": product_id,
            "name": "Integration Test Product",
            "subtitle": "created by product integration test",
            "mainImage": "https://example.com/product.png",
            "category": "integration-test",
            "tags": "test,product",
            "status": 1,
            "price": "19.90",
        }

        create_status, create_data = request_json(PRODUCT_API, method="POST", payload=create_payload)
        self.assertEqual(201, create_status, msg=create_data)
        self.assertTrue(create_data.get("success"), msg=create_data)
        self.assertEqual(product_id, create_data["data"]["productId"])

        detail_status, detail_data = request_json(f"{PRODUCT_API}/{product_id}")
        self.assertEqual(200, detail_status, msg=detail_data)
        self.assertTrue(detail_data.get("success"), msg=detail_data)
        self.assertEqual(product_id, detail_data["data"]["productId"])

        second_detail_status, second_detail_data = request_json(f"{PRODUCT_API}/{product_id}")
        self.assertEqual(200, second_detail_status, msg=second_detail_data)
        self.assertTrue(second_detail_data.get("success"), msg=second_detail_data)

        list_status, list_data = request_json(
            f"{PRODUCT_API}/list?status=1&category=integration-test&pageNo=1&pageSize=10"
        )
        self.assertEqual(200, list_status, msg=list_data)
        self.assertTrue(list_data.get("success"), msg=list_data)
        self.assertGreaterEqual(list_data["data"]["total"], 1)

        stock_status, stock_data = request_json(f"{PRODUCT_API}/{product_id}/with-stock")
        self.assertEqual(200, stock_status, msg=stock_data)
        self.assertTrue(stock_data.get("success"), msg=stock_data)
        self.assertEqual(product_id, stock_data["data"]["product"]["productId"])

        offline_status, offline_data = request_json(f"{PRODUCT_API}/{product_id}/status?status=0", method="PATCH")
        self.assertEqual(200, offline_status, msg=offline_data)
        self.assertTrue(offline_data.get("success"), msg=offline_data)
        self.assertEqual(0, offline_data["data"]["status"])

        metrics_status, metrics_data = request_json(f"{PRODUCT_API}/metrics")
        self.assertEqual(200, metrics_status, msg=metrics_data)
        self.assertTrue(metrics_data.get("success"), msg=metrics_data)
        self.assertIn("productReadTotal", metrics_data["data"])

        delete_status, delete_data = request_json(f"{PRODUCT_API}/{product_id}", method="DELETE")
        self.assertEqual(200, delete_status, msg=delete_data)
        self.assertTrue(delete_data.get("success"), msg=delete_data)

        missing_status, missing_data = request_json(f"{PRODUCT_API}/{product_id}")
        self.assertEqual(404, missing_status, msg=missing_data)
        self.assertFalse(missing_data.get("success"), msg=missing_data)


if __name__ == "__main__":
    unittest.main()
