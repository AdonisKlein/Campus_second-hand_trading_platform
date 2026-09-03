#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""确定性生成 Marketplace HPA 压测数据（固定随机种子）。

输出可直接导入 campus_marketplace 库的 SQL：50 个在售卖家投影 + 20000 件可见商品。
同一输入永远生成同一份 SQL，便于复现与核对行数。

用法:
    python experiments/hpa/seed-marketplace.py --out seed-hpa.sql
"""

import argparse
import random

SEED = 20260826
SELLER_COUNT = 50
ITEM_COUNT = 20_000
REGIONS = ["学院路校区", "沙河校区", "大运村", "其他校内区域"]
CATEGORIES = ["教材", "数码", "生活", "运动", "衣物", "其他"]
TITLE_TERMS = ["教材", "考研", "高数", "台灯", "键盘", "耳机", "风扇", "自行车", "吉他",
               "显示器", "保温杯", "雨伞", "书桌", "椅子", "吹风机"]
TAG_TERMS = ["九成新", "宿舍神器", "毕业生", "可小刀", "同城自提"]


def values_sql(fields):
    return "(" + fields + ")"


def main():
    parser = argparse.ArgumentParser()
    parser.add_argument("--out", default="seed-hpa.sql")
    args = parser.parse_args()

    rng = random.Random(SEED)
    lines = [
        "-- 确定性生成：seed=%d sellers=%d items=%d" % (SEED, SELLER_COUNT, ITEM_COUNT),
        "SET NAMES utf8mb4;",
    ]

    # 卖家公开投影（Marketplace 自有表，供搜索 JOIN 使用）
    sellers = []
    for seller_id in range(1, SELLER_COUNT + 1):
        username = "seller%04d" % seller_id
        nickname = "卖家" + str(seller_id)
        region = rng.choice(REGIONS)
        credit = rng.randint(40, 100)
        created = "2026-08-20 08:00:00.000000"
        updated = "2026-09-01 12:00:00.000000"
        sellers.append(
            "(%d,'%s','%s','%s',%d,'2026-09-01 10:00:00.000000','ACTIVE','STUDENT','%s',1,0,'%s')"
            % (seller_id, username, nickname, region, credit, created, updated)
        )
    seller_batch = values_sql("id,username,nickname,campus_region,credit_score,"
                              "last_active_at,status,role,created_at,source_version,row_version,updated_at")
    for offset in range(0, len(sellers), 500):
        lines.append("INSERT INTO searchable_user_projection %s VALUES\n%s;" %
                     (seller_batch, ",\n".join(sellers[offset:offset + 500])))

    # 商品与少量标签，描述含常见汉字，保证 %term% 搜索需要扫描
    items = []
    tag_rows = []
    for item_id in range(1, ITEM_COUNT + 1):
        seller_id = (item_id - 1) % SELLER_COUNT + 1
        title = "%s-%04d 号商品" % (rng.choice(TITLE_TERMS), item_id)
        category = rng.choice(CATEGORIES)
        price = "%.2f" % rng.uniform(5, 999)
        description = "个人闲置 的 %s，九成新 的 物品，宿舍自提 的 优先。" % title
        region = rng.choice(REGIONS)
        created = "2026-08-%02d %02d:%02d:%02d.000000" % (
            rng.randint(1, 28), rng.randint(8, 22), rng.randint(0, 59), rng.randint(0, 59))
        items.append(
            "(%d,'%s','%s',%s,'%s','','%s',%d,'ON_SALE','VISIBLE',%d,'%s')"
            % (item_id, title[:120], category, price, description[:1000], region,
               seller_id, rng.randint(0, 5), created)
        )
        if rng.random() < 0.1:
            tag = rng.choice(TAG_TERMS)
            tag_rows.append("(%d,'%s')" % (item_id, tag))

    item_batch = values_sql("id,title,category,price,description,image_url,region,seller_id,"
                            "status,moderation_status,version,created_at")
    for offset in range(0, len(items), 500):
        lines.append("INSERT INTO items %s VALUES\n%s;" %
                     (item_batch, ",\n".join(items[offset:offset + 500])))
    for offset in range(0, len(tag_rows), 500):
        lines.append("INSERT IGNORE INTO item_tags (item_id,tag) VALUES\n%s;" %
                     ",\n".join(tag_rows[offset:offset + 500]))

    with open(args.out, "w", encoding="utf-8") as handle:
        handle.write("\n".join(lines) + "\n")
    print("wrote %s (%d lines)" % (args.out, len(lines)))


if __name__ == "__main__":
    main()
