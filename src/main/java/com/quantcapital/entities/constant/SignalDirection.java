/**
 * @(#)SignalDirection.java, 7月 16, 2025.
 * <p>
 * Copyright 2025 yuanfudao.com. All rights reserved.
 * FENBI.COM PROPRIETARY/CONFIDENTIAL. Use is subject to license terms.
 */
package com.quantcapital.entities.constant;

import lombok.AllArgsConstructor;
import lombok.Getter;

/**
 * 信号方向枚举
 * @author lijiechengbj
 */
@Getter
@AllArgsConstructor
public enum SignalDirection {
    /** 买入 */
    BUY("买入"),

    /** 卖出 */
    SELL("卖出"),

    /** 持有/无操作 */
    HOLD("持有");

    private final String description;

    @Override
    public String toString() {
        return description;
    }
}
