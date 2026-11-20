#!/usr/bin/env python3
"""Generate the fixtures for live_demo.sql next to this script."""

import csv
from pathlib import Path


DEMO_DIR = Path(__file__).resolve().parent
CUSTOMER_HEADER = ("customer_id", "name", "region")
SALE_HEADER = ("order_id", "customer_id", "status", "amount")
CUSTOMERS = (
    (1, "Ada", "North"),
    (2, "Ben", "South"),
    (3, "Chloe", "North"),
    (4, "Diego", "West"),
)
SALES = (
    (101, 1, "shipped", 80),
    (102, 1, "shipped", 120),
    (103, 1, "pending", 200),
    (104, 2, "shipped", 150),
    (105, 2, "shipped", 25),
    (106, 3, "shipped", 50),
    (107, 3, "returned", 60),
    (108, 4, "shipped", 90),
)
SCALE_ROWS = 100_000


def write_csv(filename, header, rows):
    with (DEMO_DIR / filename).open("w", encoding="utf-8", newline="") as file:
        writer = csv.writer(file)
        writer.writerow(header)
        writer.writerows(rows)


def scaled_sales():
    for index in range(SCALE_ROWS):
        _, customer_id, status, amount = SALES[index % len(SALES)]
        yield (index + 1, customer_id, status, amount)


def main():
    write_csv("live_customers.csv", CUSTOMER_HEADER, CUSTOMERS)
    write_csv("live_sales.csv", SALE_HEADER, SALES)
    write_csv("live_duplicate_customers.csv", CUSTOMER_HEADER,
              (CUSTOMERS[0], (1, "Avery", "South")))
    write_csv("live_orphan_sales.csv", SALE_HEADER, ((201, 999, "shipped", 10),))
    write_csv("live_sales_100k.csv", SALE_HEADER, scaled_sales())
    print(f"Created demo fixtures, including {SCALE_ROWS:,} orders in demo/live_sales_100k.csv.")


if __name__ == "__main__":
    main()
