package com.zyh.seckill.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.zyh.seckill.config.RabbitConfig;
import com.zyh.seckill.dto.SeckillMessage;
import com.zyh.seckill.dto.SeckillResponse;
import com.zyh.seckill.entity.SeckillGoods;
import com.zyh.seckill.entity.SeckillOrder;
import com.zyh.seckill.mapper.SeckillGoodsMapper;
import com.zyh.seckill.mapper.SeckillOrderMapper;
import com.zyh.seckill.service.SeckillService;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.Arrays;
import java.util.UUID;

@Service
public class SeckillServiceImpl implements SeckillService {

    private static final String STOCK_KEY = "seckill:stock:";
    private static final String USER_KEY = "seckill:users:";
    private static final String GOODS_KEY = "seckill:goods:";

    private final SeckillGoodsMapper goodsMapper;
    private final SeckillOrderMapper orderMapper;
    private final StringRedisTemplate redis;
    private final DefaultRedisScript<Long> seckillScript;
    private final ObjectMapper objectMapper;
    private final RabbitTemplate rabbitTemplate;

    public SeckillServiceImpl(SeckillGoodsMapper goodsMapper,
                              SeckillOrderMapper orderMapper,
                              StringRedisTemplate redis,
                              DefaultRedisScript<Long> seckillScript,
                              ObjectMapper objectMapper,
                              RabbitTemplate rabbitTemplate) {
        this.goodsMapper = goodsMapper;
        this.orderMapper = orderMapper;
        this.redis = redis;
        this.seckillScript = seckillScript;
        this.objectMapper = objectMapper;
        this.rabbitTemplate = rabbitTemplate;
    }

    @Override
    public SeckillResponse seckill(Long goodsId, Long userId) {
        SeckillGoods goods = loadGoods(goodsId);
        if (goods == null) {
            return new SeckillResponse(1, "商品不存在", null);
        }

        LocalDateTime now = LocalDateTime.now();
        if (now.isBefore(goods.getStartTime())) {
            return new SeckillResponse(2, "活动未开始", null);
        }
        if (now.isAfter(goods.getEndTime())) {
            return new SeckillResponse(3, "活动已结束", null);
        }

        // 库存判断、扣减、防重复下单在 Redis 里原子完成，没抢到的请求不会碰数据库
        Long result = redis.execute(seckillScript,
                Arrays.asList(STOCK_KEY + goodsId, USER_KEY + goodsId),
                String.valueOf(userId));

        if (result == null) {
            return new SeckillResponse(6, "系统繁忙，请重试", null);
        }
        if (result == -1) {
            return new SeckillResponse(7, "库存未预热", null);
        }
        if (result == -2) {
            return new SeckillResponse(5, "请勿重复下单", null);
        }
        if (result == 0) {
            return new SeckillResponse(4, "库存不足", null);
        }

        String orderNo = UUID.randomUUID().toString().replace("-", "");
        try {
            rabbitTemplate.convertAndSend(RabbitConfig.EXCHANGE, RabbitConfig.ROUTING_KEY,
                    new SeckillMessage(goodsId, userId, orderNo));
        } catch (Exception e) {
            rollbackRedis(goodsId, userId);
            return new SeckillResponse(6, "系统繁忙，请重试", null);
        }

        return new SeckillResponse(0, "抢购成功，订单生成中", orderNo);
    }

    private void rollbackRedis(Long goodsId, Long userId) {
        redis.opsForValue().increment(STOCK_KEY + goodsId);
        redis.opsForSet().remove(USER_KEY + goodsId, String.valueOf(userId));
    }

    private SeckillGoods loadGoods(Long goodsId) {
        String json = redis.opsForValue().get(GOODS_KEY + goodsId);
        if (json != null) {
            try {
                return objectMapper.readValue(json, SeckillGoods.class);
            } catch (Exception ignored) {
            }
        }
        return goodsMapper.selectById(goodsId);
    }

    @Override
    public SeckillGoods getGoods(Long goodsId) {
        return goodsMapper.selectById(goodsId);
    }

    @Override
    public SeckillOrder getOrder(String orderNo) {
        return orderMapper.selectOne(new LambdaQueryWrapper<SeckillOrder>()
                .eq(SeckillOrder::getOrderNo, orderNo));
    }
}
