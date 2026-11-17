## CS5/4321 Project

Miles Lu, Muhammad Hisham, Logan Abramowitz, Nicole Lin, Nicole Luo, Bisrat Abebe, Tony Zhao, Gordon Mei, Domenic Fioravanti

To compile and run the project through Main.java:

```
mvn compile exec:java
```

To run tests:

```
mvn test
```
### Supported dataset size

The engine targets tables of up to **100,000 rows** each (`QueryEngine.SUPPORTED_MAX_ROWS_PER_TABLE`; initial target, to be confirmed with Person 4).
WHERE, SELECT, DISTINCT and LIMIT run as a lazy iterator pipeline, so no intermediate table is built.
Only ORDER BY and GROUP BY hold rows in memory, and LIMIT without ORDER BY stops reading early.

### Partial indexes

An index on one column, optionally holding only the rows that match a condition (a partial index):

```
.index returned_amount orders amount WHERE status = 'returned'
.indexes
SELECT id, amount FROM orders WHERE status = 'returned' AND amount > 950;
.dropindex returned_amount
```

A query uses an index only when its WHERE implies the index's condition (here `status = 'returned'`, or something
stronger such as `amount > 950` for an index on `amount > 500`). It then prints a line such as
`Index: returned_amount (scanned 236 of 100000 rows)`, and only the rows in the index's range are scanned.

Indexes are opt-in: with none created, queries run exactly as before. Results never change: rows keep their
table order, the full WHERE is still applied to every scanned row, and a WHERE that could raise an error
(unknown column, mismatched types) falls back to a full scan so the error is the same. Joined queries do not use
indexes. Index conditions are limited to ANDs of `column <op> literal` comparisons and `IS [NOT] NULL`.
