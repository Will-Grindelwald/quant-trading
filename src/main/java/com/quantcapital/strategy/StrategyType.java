/**
 * @(#)StrategyType.java, 7月 16, 2025.
 * <p>
 * Copyright 2025 yuanfudao.com. All rights reserved.
 * FENBI.COM PROPRIETARY/CONFIDENTIAL. Use is subject to license terms.
 */
package com.quantcapital.strategy;

import lombok.AllArgsConstructor;
import lombok.Getter;

/**
 * 策略类型枚举
 * @author lijiechengbj
 */
@Getter
@AllArgsConstructor
public enum StrategyType {
    /** 开单策略 - 寻找开仓机会 */
    ENTRY("开单策略"),

    /** 止盈止损策略 - 管理已有持仓 */
    EXIT("止盈止损策略"),

    /** 通用强制止损策略 - 兜底风控 */
    UNIVERSAL_STOP("通用强制止损");

    private final String description;

    @Override
    public String toString() {
        return description;
    }
}