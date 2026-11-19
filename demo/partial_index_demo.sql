-- Partial index demo (about 2 minutes).
-- Before presenting, from the repo root:  python3 demo/make_orders.py
-- In the CLI:  .read demo/partial_index_demo.sql
-- "expect" lines are what the CLI prints for the generated data.

.load demo/orders.csv orders
-- expect: Loaded 500000 rows into orders

-- 1. Baseline, no index: all 500,000 rows are checked.
SELECT COUNT(*), SUM(amount) FROM orders WHERE status = 'returned' AND amount > 950;
-- expect: 501 | 488380   (no Index line)

-- 2. Partial index: only the returned orders (about 2%), sorted by amount.
.index returned_amount orders amount WHERE status = 'returned'
-- expect: Created index returned_amount ON orders(amount) WHERE status = 'returned' [9957 of 500000 rows]

-- 3. Same query: same answer, 501 rows read instead of 500,000.
SELECT COUNT(*), SUM(amount) FROM orders WHERE status = 'returned' AND amount > 950;
-- expect: Index: returned_amount (scanned 501 of 500000 rows)
-- expect: 501 | 488380

-- 4. A narrower range reads only that range.
SELECT COUNT(*), SUM(amount) FROM orders WHERE status = 'returned' AND amount >= 990 AND amount <= 1000;
-- expect: Index: returned_amount (scanned 100 of 500000 rows)
-- expect: 100 | 99537

-- 5. Outside the index's condition: no Index line, a normal full scan, still correct.
SELECT COUNT(*) FROM orders WHERE status = 'pending' AND amount > 950;
-- expect: 6976   (no Index line)

-- 6. The engine reasons about conditions: an index on amount > 500 ...
.index big_orders orders amount WHERE amount > 500
-- expect: Created index big_orders ON orders(amount) WHERE amount > 500 [249280 of 500000 rows]

-- ... is used for amount > 900, because amount > 900 implies amount > 500.
SELECT COUNT(*) FROM orders WHERE amount > 900;
-- expect: Index: big_orders (scanned 49940 of 500000 rows)
-- expect: 49940

-- ... but not for amount > 400, which it doesn't cover: full scan instead.
SELECT COUNT(*) FROM orders WHERE amount > 400;
-- expect: 299379   (no Index line)

.indexes
.dropindex returned_amount
.dropindex big_orders
