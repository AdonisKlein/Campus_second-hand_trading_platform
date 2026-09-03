#!/usr/bin/env python3
"""MinIO 跨副本图片读取证据脚本（HPA 实验，成员 B）。

流程：登录测试学生账号 -> 上传一张真实 PNG（由 Marketplace 存入 MinIO）-> 发布带图商品。
随后在副本 >=2 时删除最初处理上传的旧 Pod，再 GET 图片 URL，若仍返回 200，
即证明新副本从共享 MinIO 读到了图片（实例无本地状态）。

用法：
    python experiments/hpa/image-evidence.py prepare --out state.json
    python experiments/hpa/image-evidence.py verify --state state.json
依赖：pip install requests（或把装有 requests 的目录加入 PYTHONPATH）。
"""

import argparse
import json
import sys
import time

import requests

ACCOUNT_EMAIL = "hpa-seller@example.test"
ACCOUNT_PASSWORD = "abc123"


def api_post(session, base, path, headers, json_body=None, files=None, timeout=60):
    return session.post(base + path, json=json_body, files=files,
                        headers=headers, timeout=timeout)


def login(session, base):
    csrf = session.get(base + "/api/auth/csrf", timeout=30)
    csrf.raise_for_status()
    token = csrf.json()["data"]
    headers = {"X-XSRF-TOKEN": token}
    login_response = session.post(
        base + "/api/auth/login",
        json={"email": ACCOUNT_EMAIL, "password": ACCOUNT_PASSWORD},
        headers=headers, timeout=30)
    if login_response.status_code != 200:
        raise SystemExit("login failed: %d %s" % (login_response.status_code, login_response.text[:300]))
    return session, headers


def prepare(base, image_path, out_path):
    session, headers = login(requests.Session(), base)
    with open(image_path, "rb") as handle:
        upload = api_post(session, base, "/api/media/product-images", headers,
                          files={"file": ("hpa-evidence.png", handle, "image/png")})
    if upload.status_code not in (200, 201):
        raise SystemExit("upload failed: %d %s" % (upload.status_code, upload.text[:500]))
    stored = upload.json()["data"]
    owner = stored["url"].split("/")[3]
    item_body = {
        "title": "HPA MinIO 跨副本图片证据",
        "category": "数码",
        "price": "9.90",
        "description": "压测期间上传，验证 Marketplace 无本地状态、图片存储在 MinIO。",
        "imageUrl": stored["url"],
        "region": "沙河校区",
        "tags": ["九成新"],
    }
    item = api_post(session, base, "/api/items", headers, json_body=item_body)
    if item.status_code not in (200, 201):
        raise SystemExit("item create failed: %d %s" % (item.status_code, item.text[:500]))
    item_id = item.json()["data"]["id"]
    state = {
        "imageUrl": stored["url"],
        "imageContentType": stored["contentType"],
        "imageSize": stored["size"],
        "width": stored["width"],
        "height": stored["height"],
        "itemId": item_id,
        "owner": owner,
        "account": ACCOUNT_EMAIL,
        "preparedAt": time.strftime("%Y-%m-%dT%H:%M:%S%z"),
    }
    with open(out_path, "w", encoding="utf-8") as handle:
        json.dump(state, handle, ensure_ascii=False, indent=2)
    print(json.dumps(state, ensure_ascii=False, indent=2))


def verify(base, state_path, out_path):
    state = json.load(open(state_path, encoding="utf-8"))
    file_name = state["imageUrl"].rsplit("/", 1)[1]
    candidates = [
        state["imageUrl"],
        "/api/media/product-images/%s/%s" % (state["owner"], file_name),
    ]
    results = {}
    for path in candidates:
        response = requests.get(base + path, timeout=30)
        results[path] = {
            "status": response.status_code,
            "contentType": response.headers.get("Content-Type", ""),
            "bytes": len(response.content),
        }
    with open(out_path, "w", encoding="utf-8") as handle:
        json.dump({"itemId": state["itemId"], "imageUrl": state["imageUrl"],
                   "checks": results, "checkedAt": time.strftime("%Y-%m-%dT%H:%M:%S%z")},
                  handle, ensure_ascii=False, indent=2)
    print(json.dumps(results, ensure_ascii=False, indent=2))
    if not any(result["status"] == 200 for result in results.values()):
        raise SystemExit("image read check failed")


def main():
    parser = argparse.ArgumentParser()
    parser.add_argument("command", choices=["prepare", "verify"])
    parser.add_argument("--base", default="http://127.0.0.1:8080")
    parser.add_argument("--image", default="experiments/hpa/evidence/run3/hpa-evidence.png")
    parser.add_argument("--out", default="experiments/hpa/evidence/run3/image-state.json")
    parser.add_argument("--state", default="experiments/hpa/evidence/run3/image-state.json")
    args = parser.parse_args()
    if args.command == "prepare":
        prepare(args.base, args.image, args.out)
    else:
        verify(args.base, args.state, args.out)


if __name__ == "__main__":
    sys.exit(main())
