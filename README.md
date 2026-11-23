# CS5/4321 Relational Database Implementation

**CS5/4321 course project: Group 3**

A Java query engine that loads CSV files into typed, in-memory tables and runs a subset of SQL through a command-line shell. It implements relational operators, in-memory and disk-partitioned hash joins, and partial indexes that can reduce the number of rows scanned.

## Getting Started

With JDK 17 or newer and Maven installed, start the SQL shell from the repository root.

```sh
mvn -q compile exec:java
```

In the SQL shell, load a CSV with a header row and run a `SELECT` query. This example uses an `orders.csv` file in the repository root with `id`, `status`, and `amount` columns.

```text
.load orders.csv orders
SELECT id, amount FROM orders WHERE status = 'shipped' ORDER BY amount DESC;
.save orders orders_copy.csv
.export shipped_orders.csv
```

`.save` writes the entire table to CSV, and `.export` writes the latest query result to CSV.

## Implementation

| Area | How it works |
| --- | --- |
| [SQL parser](src/main/java/com/group3/parser/SQLParser.java) | Translates JSqlParser output into the engine's query model. Supports `SELECT`, aliases, `WHERE`, `GROUP BY`, `HAVING`, `ORDER BY`, `DISTINCT`, and `LIMIT`. Aggregates include `COUNT`, `SUM`, `AVG`, `MIN`, and `MAX`. |
| [Query engine](src/main/java/com/group3/engine/QueryEngine.java) | Composes filtering and projection with iterators and can stop early for a simple `LIMIT` query. Sorting buffers its input, aggregation keeps per-group state, and the engine materializes the final result. |
| [Join operators](src/main/java/com/group3/operator) | SQL supports one `INNER JOIN` on equality between two qualified columns. In auto mode, the engine selects Grace hash join when the right table exceeds the configured build-row threshold. Grace partitions both inputs on disk and builds a hash table from the smaller side of each partition pair.|
| [Partial indexes](src/main/java/com/group3/index) | Optional single-column indexes narrow scans on single-table queries. The query's `WHERE` must imply the partial index predicate, which can combine comparisons to literals and `IS [NOT] NULL` checks with `AND`. The engine rechecks the full filter on candidate rows. |
| [CSV storage](src/main/java/com/group3/storage) and [table model](src/main/java/com/group3/data) | Apache Commons CSV reads records in two passes to infer types and build the in-memory table. The table model validates single-column primary and foreign keys. Tables and query results can be written back to CSV. |

Loaded tables and join output stay in memory. Grace hash join limits the size of its per-partition hash table, while the total memory required also depends on row width and the size of the joined result.


## Run the tests

```sh
mvn test
```

The JUnit 5 suite covers SQL parsing, query results, join algorithms, index eligibility, CSV handling, and CLI behavior. The Java 17 CI workflow includes the Maven command with pinned compiler and test plugins. [Test suite](src/test/java/com/group3) · [CI workflow](.github/workflows/ci.yml)

## Team

Miles Lu, Muhammad Hisham, Logan Abramowitz, Nicole Lin, Nicole Luo, Bisrat Abebe, Tony Zhao, Gordon Mei, and Domenic Fioravanti
