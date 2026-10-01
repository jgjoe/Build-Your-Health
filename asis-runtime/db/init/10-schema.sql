-- AS-IS schema/data loader.
--
-- The original .sql files are mounted read-only at /asis-sql. They contain
-- foreign keys, so they must run in dependency order:
--   member -> product -> contents -> board -> orders -> order_items
--   -> reviews -> user_records -> insert (seed data)
-- The MySQL entrypoint runs this file against the MYSQL_DATABASE (HealthDB).
--
-- Do not reorder; do not edit the originals.

-- The mysql client inside the image starts with a locale-derived (latin1)
-- client charset when LANG is unset; the .sql files are UTF-8 with Korean
-- text (insert.sql), so pin the session charset before loading anything.
SET NAMES utf8mb4;

SOURCE /asis-sql/member.sql
SOURCE /asis-sql/product.sql
SOURCE /asis-sql/contents.sql
SOURCE /asis-sql/board.sql
SOURCE /asis-sql/orders.sql
SOURCE /asis-sql/order_items.sql
SOURCE /asis-sql/reviews.sql
SOURCE /asis-sql/user_records.sql
SOURCE /asis-sql/insert.sql
