package com.zyh.seckill.listener;

import com.zyh.seckill.config.RabbitConfig;
import com.zyh.seckill.dto.SeckillMessage;
import com.zyh.seckill.entity.SeckillOrder;
import com.zyh.seckill.mapper.SeckillGoodsMapper;
import com.zyh.seckill.mapper.SeckillOrderMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;

@Component
public class OrderConsumer {

    private static final Logger log = LoggerFactory.getLogger(OrderConsumer.class);

    private final SeckillGoodsMapper goodsMapper;
    private final SeckillOrderMapper orderMapper;

    public OrderConsumer(SeckillGoodsMapper goodsMapper, SeckillOrderMapper orderMapper) {
        this.goodsMapper = goodsMapper;
        this.orderMapper = orderMapper;
    }

    @RabbitListener(queues = RabbitConfig.QUEUE, concurrency = "5")
    public void handle(SeckillMessage msg) {
        int affected = goodsMapper.deductStock(msg.getGoodsId());
        if (affected == 0) {
            log.warn("数据库库存不足，丢弃消息 orderNo={}", msg.getOrderNo());
            return;
        }

        SeckillOrder order = new SeckillOrder();
        order.setOrderNo(msg.getOrderNo());
        order.setUserId(msg.getUserId());
        order.setGoodsId(msg.getGoodsId());
        order.setStatus(1);
        order.setCreateTime(LocalDateTime.now());

        try {
            orderMapper.insert(order);
            log.info("订单创建成功 orderNo={} userId={}", msg.getOrderNo(), msg.getUserId());
        } catch (DuplicateKeyException e) {
            log.warn("重复订单，已忽略 orderNo={}", msg.getOrderNo());
        }
    }
}
