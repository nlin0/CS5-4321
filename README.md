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
