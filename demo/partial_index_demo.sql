-- Partial index demo. Run from the repo root after: python3 demo/make_orders.py
-- In the CLI:  .read demo/partial_index_demo.sql
.load demo/orders.csv orders

-- 1. No index yet: every one of the 500,000 rows is checked.
SELECT COUNT(*), SUM(amount) FROM orders WHERE status = 'returned' AND amount > 950;

-- 2. Index only the returned orders (about 2% of the table), ordered by amount.
.index returned_amount orders amount WHERE status = 'returned'
.indexes

-- 3. Same query: the engine uses the index and prints how many rows it scanned.
SELECT COUNT(*), SUM(amount) FROM orders WHERE status = 'returned' AND amount > 950;

-- 4. A real result from the index, still in normal SQL form.
SELECT id, customer, region, amount FROM orders WHERE status = 'returned' AND amount >= 998 ORDER BY amount DESC LIMIT 5;

-- 5. A query outside the index's condition: no Index line, normal full scan.
SELECT COUNT(*) FROM orders WHERE status = 'pending' AND amount > 950;

.dropindex returned_amount
