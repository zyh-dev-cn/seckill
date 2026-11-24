CREATE TABLE IF NOT EXISTS seckill_goods (
    id          BIGINT        NOT NULL AUTO_INCREMENT COMMENT '主键',
    name        VARCHAR(128)  NOT NULL COMMENT '商品名称',
    price       DECIMAL(10,2) NOT NULL COMMENT '秒杀价',
    stock       INT           NOT NULL COMMENT '库存',
    start_time  DATETIME      NOT NULL COMMENT '活动开始时间',
    end_time    DATETIME      NOT NULL COMMENT '活动结束时间',
    create_time DATETIME      NOT NULL COMMENT '创建时间',
    PRIMARY KEY (id)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COMMENT '秒杀商品表';

CREATE TABLE IF NOT EXISTS seckill_order (
    id          BIGINT      NOT NULL AUTO_INCREMENT COMMENT '主键',
    order_no    VARCHAR(64) NOT NULL COMMENT '订单号',
    user_id     BIGINT      NOT NULL COMMENT '用户ID',
    goods_id    BIGINT      NOT NULL COMMENT '商品ID',
    status      TINYINT     NOT NULL DEFAULT 1 COMMENT '1-已创建 0-已取消',
    create_time DATETIME    NOT NULL COMMENT '创建时间',
    PRIMARY KEY (id),
    UNIQUE KEY uk_order_no (order_no),
    UNIQUE KEY uk_user_goods (user_id, goods_id)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COMMENT '秒杀订单表';

INSERT IGNORE INTO seckill_goods (id, name, price, stock, start_time, end_time, create_time)
VALUES (1, 'iPhone 17 Pro 秒杀', 4999.00, 100, '2020-01-01 00:00:00', '2030-12-31 23:59:59', NOW());
