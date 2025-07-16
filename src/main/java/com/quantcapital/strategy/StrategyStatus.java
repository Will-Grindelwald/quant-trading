/**
 * @(#)StrategyStatus.java, 7月 16, 2025.
 * <p>
 * Copyright 2025 yuanfudao.com. All rights reserved.
 * FENBI.COM PROPRIETARY/CONFIDENTIAL. Use is subject to license terms.
 */
package com.quantcapital.strategy;

import lombok.AllArgsConstructor;
import lombok.Getter;

/**
 * 策略状态枚举
 * @author lijiechengbj
 */
@Getter
@AllArgsConstructor
public enum StrategyStatus {
    /** 未初始化 */
    NOT_INITIALIZED("未初始化"),

    /** 已初始化 */
    INITIALIZED("已初始化"),

    /** 运行中 */
    RUNNING("运行中"),

    /** 已暂停 */
    PAUSED("已暂停"),

    /** 已停止 */
    STOPPED("已停止"),

    /** 错误状态 */
    ERROR("错误状态");

    private final String description;

    @Override
    public String toString() {
        return description;
    }
}