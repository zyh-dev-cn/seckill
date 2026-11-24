package com.zyh.seckill.service;

import com.zyh.seckill.dto.SeckillResponse;
import com.zyh.seckill.entity.SeckillGoods;
import com.zyh.seckill.entity.SeckillOrder;

public interface SeckillService {

    SeckillResponse seckill(Long goodsId, Long userId);

    SeckillGoods getGoods(Long goodsId);

    SeckillOrder getOrder(String orderNo);
}
