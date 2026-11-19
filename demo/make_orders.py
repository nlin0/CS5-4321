#!/usr/bin/env python3
"""Writes demo/orders.csv: 500,000 orders for the partial-index demo (same data every run).

Run from the repo root:  python3 demo/make_orders.py
"""
import os
import random

ROWS = 500_000
random.seed(4321)
path = os.path.join(os.path.dirname(os.path.abspath(__file__)), "orders.csv")

regions = ["NE", "SE", "MW", "SW", "W"]
with open(path, "w") as f:
    f.write("id,customer,region,status,amount\n")
    for i in range(1, ROWS + 1):
        r = random.random()
        # about 2% of orders are returned; that is the slice the partial index covers
        status = "returned" if r < 0.02 else "pending" if r < 0.30 else "shipped"
        f.write(f"{i},cust{random.randint(1, 20000)},{random.choice(regions)},{status},{random.randint(1, 1000)}\n")
print(f"wrote {ROWS} rows to {path}")
