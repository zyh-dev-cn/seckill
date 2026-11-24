package com.zyh.seckill.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class SeckillMessage {

    private Long goodsId;

    private Long userId;

    private String orderNo;
}
