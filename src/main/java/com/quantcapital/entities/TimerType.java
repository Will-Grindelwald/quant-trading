/**
 * @(#)TimerType.java, 7月 16, 2025.
 * <p>
 * Copyright 2025 yuanfudao.com. All rights reserved.
 * FENBI.COM PROPRIETARY/CONFIDENTIAL. Use is subject to license terms.
 */
package com.quantcapital.entities;

import lombok.AllArgsConstructor;
import lombok.Getter;

/**
 * 定时器类型枚举
 * @author lijiechengbj
 */
@Getter
@AllArgsConstructor
public enum TimerType {
    /** 市场数据更新 */
    MARKET_DATA_UPDATE("市场数据更新"),

    /** 风控检查 */
    RISK_CHECK("风控检查"),

    /** 心跳检测 */
    HEARTBEAT("心跳检测"),

    /** 清理任务 */
    CLEANUP("清理任务"),

    /** 策略定时器 */
    STRATEGY_TIMER("策略定时器"),

    /** 组合再平衡 */
    PORTFOLIO_REBALANCE("组合再平衡");

    private final String description;

    @Override
    public String toString() {
        return description;
    }
}