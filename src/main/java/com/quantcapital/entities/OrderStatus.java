/**
 * @(#)OrderStatus.java, 7月 16, 2025.
 * <p>
 * Copyright 2025 yuanfudao.com. All rights reserved.
 * FENBI.COM PROPRIETARY/CONFIDENTIAL. Use is subject to license terms.
 */
package com.quantcapital.entities;

import lombok.AllArgsConstructor;
import lombok.Getter;

/**
 * 订单状态枚举
 * 
 * @author QuantCapital Team
 */
@Getter
@AllArgsConstructor
public enum OrderStatus {
    
    /** 待提交 */
    PENDING("待提交"),
    
    /** 已提交 */
    SUBMITTED("已提交"),
    
    /** 部分成交 */
    PARTIALLY_FILLED("部分成交"),
    
    /** 全部成交 */
    FILLED("全部成交"),
    
    /** 已取消 */
    CANCELLED("已取消"),
    
    /** 已拒绝 */
    REJECTED("已拒绝"),
    
    /** 已过期 */
    EXPIRED("已过期");
    
    private final String description;
    
    @Override
    public String toString() {
        return description;
    }
}