/**
 * @(#)EventType.java, 7月 16, 2025.
 * <p>
 * Copyright 2025 yuanfudao.com. All rights reserved.
 * FENBI.COM PROPRIETARY/CONFIDENTIAL. Use is subject to license terms.
 */
package com.quantcapital.entities.constant;

/**
 * 事件类型枚举
 *
 * @author lijiechengbj
 */
public enum EventType {
    /**
     * 市场数据事件
     */
    MARKET,

    /**
     * 策略信号事件
     */
    SIGNAL,

    /**
     * 订单事件
     */
    ORDER,

    /**
     * 成交事件
     */
    FILL,

    /**
     * 定时器事件
     */
    TIMER,

    /**
     * 系统事件
     */
    SYSTEM
}
