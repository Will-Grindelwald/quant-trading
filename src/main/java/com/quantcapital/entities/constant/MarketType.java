/**
 * @(#)MarketType.java, 7月 17, 2025.
 * <p>
 * Copyright 2025 yuanfudao.com. All rights reserved.
 * FENBI.COM PROPRIETARY/CONFIDENTIAL. Use is subject to license terms.
 */
package com.quantcapital.entities.constant;

import lombok.AllArgsConstructor;
import lombok.Getter;

import java.time.ZoneId;

/**
 * 市场类型枚举
 * @author lijiechengbj
 */
@Getter
@AllArgsConstructor
public enum MarketType {
    /** A股市场 */
    A_SHARE("A股", ZoneId.of("Asia/Shanghai")),
    /** 港股市场 */
    HK_STOCK("港股", ZoneId.of("Asia/Hong_Kong")),
    /** 美股市场 */
    US_STOCK("美股", ZoneId.of("America/New_York")),
    /** 加密货币（24小时） */
    CRYPTO("加密货币", ZoneId.of("UTC"));

    private final String description;
    private final ZoneId timezone;
}