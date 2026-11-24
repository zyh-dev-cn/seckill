package com.zyh.seckill.dto;

import lombok.AllArgsConstructor;
import lombok.Data;

@Data
@AllArgsConstructor
public class SeckillResponse {

    private int code;

    private String message;

    private String orderNo;
}
