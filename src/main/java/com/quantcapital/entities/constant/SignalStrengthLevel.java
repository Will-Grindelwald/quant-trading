/**
 * @(#)SignalStrengthLevel.java, 7月 16, 2025.
 * <p>
 * Copyright 2025 yuanfudao.com. All rights reserved.
 * FENBI.COM PROPRIETARY/CONFIDENTIAL. Use is subject to license terms.
 */
package com.quantcapital.entities.constant;

import lombok.AllArgsConstructor;
import lombok.Getter;

/**
 * 信号强度级别枚举
 * @author lijiechengbj
 */
@Getter
@AllArgsConstructor
enum SignalStrengthLevel {
    /** 非常强 */
    VERY_STRONG("非常强"),

    /** 强 */
    STRONG("强"),

    /** 中等 */
    MODERATE("中等"),

    /** 弱 */
    WEAK("弱"),

    /** 非常弱 */
    VERY_WEAK("非常弱");

    private final String description;

    @Override
    public String toString() {
        return description;
    }
}