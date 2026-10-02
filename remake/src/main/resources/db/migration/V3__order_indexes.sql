-- Indexes chosen from the R2 measurements (docs/db-tuning.md, scenario S2b).
-- ORDER_ITEMS(ORDER_ID): the foreign key column, which Oracle does not index automatically.
--   Order detail and the order-history join stop scanning all order lines.
-- ORDERS(USER_ID, ORDER_DATE, ORDER_ID): the equality column first, then the history sort order,
--   so a member's newest page is read in index order. It also serves the per-member order count,
--   so no separate ORDERS(USER_ID) index is added.

CREATE INDEX IX_ORDER_ITEMS_ORDER ON ORDER_ITEMS (ORDER_ID);

CREATE INDEX IX_ORDERS_USER_DATE ON ORDERS (USER_ID, ORDER_DATE, ORDER_ID);
