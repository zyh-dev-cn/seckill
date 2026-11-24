package com.zyh.seckill.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.zyh.seckill.entity.SeckillGoods;
import com.zyh.seckill.mapper.SeckillGoodsMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

import java.util.List;

@Component
public class StockWarmupRunner implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(StockWarmupRunner.class);

    private final SeckillGoodsMapper goodsMapper;
    private final StringRedisTemplate redis;
    private final ObjectMapper objectMapper;

    public StockWarmupRunner(SeckillGoodsMapper goodsMapper, StringRedisTemplate redis, ObjectMapper objectMapper) {
        this.goodsMapper = goodsMapper;
        this.redis = redis;
        this.objectMapper = objectMapper;
    }

    @Override
    public void run(ApplicationArguments args) throws Exception {
        List<SeckillGoods> list = goodsMapper.selectList(null);
        for (SeckillGoods g : list) {
            redis.opsForValue().set("seckill:stock:" + g.getId(), String.valueOf(g.getStock()));
            redis.opsForValue().set("seckill:goods:" + g.getId(), objectMapper.writeValueAsString(g));
            redis.delete("seckill:users:" + g.getId());
        }
        log.info("秒杀数据预热完成，共 {} 个商品", list.size());
    }
}
