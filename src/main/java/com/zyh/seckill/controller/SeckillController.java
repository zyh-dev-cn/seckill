package com.zyh.seckill.controller;

import com.zyh.seckill.dto.SeckillResponse;
import com.zyh.seckill.entity.SeckillGoods;
import com.zyh.seckill.entity.SeckillOrder;
import com.zyh.seckill.service.SeckillService;
import com.zyh.seckill.vo.OrderStatusVO;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api")
public class SeckillController {

    private final SeckillService seckillService;

    public SeckillController(SeckillService seckillService) {
        this.seckillService = seckillService;
    }

    @PostMapping("/seckill/{goodsId}")
    public SeckillResponse seckill(@PathVariable Long goodsId,
                                   @RequestParam(defaultValue = "0") Long userId) {
        return seckillService.seckill(goodsId, userId);
    }

    @GetMapping("/goods/{goodsId}")
    public SeckillGoods goods(@PathVariable Long goodsId) {
        return seckillService.getGoods(goodsId);
    }

    @GetMapping("/order/{orderNo}")
    public OrderStatusVO order(@PathVariable String orderNo) {
        SeckillOrder order = seckillService.getOrder(orderNo);
        return new OrderStatusVO(order != null, order);
    }
}
