/**
 * @(#)TimeInForce.java, 7月 16, 2025.
 * <p>
 * Copyright 2025 yuanfudao.com. All rights reserved.
 * FENBI.COM PROPRIETARY/CONFIDENTIAL. Use is subject to license terms.
 */
package com.quantcapital.entities;

import lombok.AllArgsConstructor;
import lombok.Getter;

/**
 * 订单有效期类型枚举
 * @author lijiechengbj
 */
@Getter
@AllArgsConstructor
public enum TimeInForce {
    /** 当日有效 */
    DAY("当日有效"),

    /** 有效直至取消 */
    GTC("有效直至取消"),

    /** 立即成交或取消 */
    IOC("立即成交或取消"),

    /** 全部成交或取消 */
    FOK("全部成交或取消"),

    /** 指定时间有效 */
    GTT("指定时间有效");

    private final String description;

    @Override
    public String toString() {
        return description;
    }
}