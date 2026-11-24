package com.zyh.seckill.vo;

import com.zyh.seckill.entity.SeckillOrder;
import lombok.AllArgsConstructor;
import lombok.Data;

@Data
@AllArgsConstructor
public class OrderStatusVO {

    private boolean ready;

    private SeckillOrder order;
}
