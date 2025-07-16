/**
 * @(#)TradeStatus.java, 7月 17, 2025.
 * <p>
 * Copyright 2025 yuanfudao.com. All rights reserved.
 * FENBI.COM PROPRIETARY/CONFIDENTIAL. Use is subject to license terms.
 */
package com.quantcapital.entities.constant;

import lombok.AllArgsConstructor;
import lombok.Getter;

/**
 * 交易状态枚举
 * @author lijiechengbj
 */
@Getter
@AllArgsConstructor
public enum TradeStatus {
    /** 开仓状态 */
    OPEN("开仓"),
    /** 已平仓 */
    CLOSED("已平仓"),
    /** 部分平仓 */
    PARTIALLY_CLOSED("部分平仓");

    private final String description;
}