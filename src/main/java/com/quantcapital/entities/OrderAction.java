package com.quantcapital.entities;

import lombok.AllArgsConstructor;
import lombok.Getter;

/**
 * 订单操作类型枚举
 * 
 * 定义对订单的操作类型，用于订单事件中。
 * 
 * @author QuantCapital Team
 */
@Getter
@AllArgsConstructor
public enum OrderAction {
    
    /** 新建订单 */
    NEW("新建订单"),
    
    /** 修改订单 */
    MODIFY("修改订单"),
    
    /** 取消订单 */
    CANCEL("取消订单"),
    
    /** 拒绝订单 */
    REJECT("拒绝订单");
    
    private final String description;
    
    @Override
    public String toString() {
        return description;
    }
}