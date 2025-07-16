package com.quantcapital.entities.constant;

import lombok.AllArgsConstructor;
import lombok.Getter;

/**
 * 订单类型枚举
 * 
 * @author QuantCapital Team
 */
@Getter
@AllArgsConstructor
public enum OrderType {
    
    /** 市价单 */
    MARKET("市价单"),
    
    /** 限价单 */
    LIMIT("限价单"),
    
    /** 止损单 */
    STOP("止损单"),
    
    /** 止损限价单 */
    STOP_LIMIT("止损限价单");
    
    private final String description;
    
    @Override
    public String toString() {
        return description;
    }
}