CREATE SEQUENCE IF NOT EXISTS stock_id_seq
INCREMENT BY 1 START WITH 1 MINVALUE 1 CACHE 10 NO CYCLE;

CREATE TABLE IF NOT EXISTS stock (
    stock_id    BIGINT          PRIMARY KEY DEFAULT nextval('stock_id_seq'),
    code        VARCHAR(120)    NULL UNIQUE,
    quantity    INTEGER         NULL
);
