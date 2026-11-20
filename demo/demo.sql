-- mvn -q compile exec:java

-- load data
.load demo/live_customers.csv live_customers
.load demo/live_sales.csv live_sales
.schema live_sales

-- which customer regions brought in the most money
.join auto 16 10000
SELECT c.region AS region, COUNT(*) AS shipped_orders, SUM(s.amount) AS revenue
FROM live_customers c INNER JOIN live_sales s ON c.customer_id = s.customer_id
WHERE s.status = 'shipped'
GROUP BY c.region
ORDER BY revenue DESC;

-- export results, check north's total revenue
.export demo/result.csv

SELECT s.order_id AS order_id, c.name AS customer, s.amount AS amount
FROM live_customers c INNER JOIN live_sales s ON c.customer_id = s.customer_id
WHERE c.region = 'North' AND s.status = 'shipped'
ORDER BY s.order_id;

-- check constraints, 
-- Say “We're going to declare customer and order IDs as primary keys, then
-- require each nonblank customer ID in the sales data to match a customer.
-- The engine checks rows that are already loaded when it declares the keys.
-- The first three declarations should succeed. Then we'll try two separate
-- files with deliberate errors.”
.pk live_customers customer_id
.pk live_sales order_id
.fk live_sales customer_id live_customers customer_id

-- errors when creating constraints 
.load demo/live_duplicate_customers.csv live_duplicate_customers
.pk live_duplicate_customers customer_id
.load demo/live_orphan_sales.csv live_orphan_sales
.fk live_orphan_sales customer_id live_customers customer_id

-- scale up
.load demo/live_sales_100k.csv live_sales_100k
SELECT c.region AS region, COUNT(*) AS shipped_orders, SUM(s.amount) AS revenue
FROM live_customers c INNER JOIN live_sales_100k s ON c.customer_id = s.customer_id
WHERE s.status = 'shipped'
GROUP BY c.region
ORDER BY revenue DESC;