/**
 * @(#)UniverseType.java, 7月 17, 2025.
 * <p>
 * Copyright 2025 yuanfudao.com. All rights reserved.
 * FENBI.COM PROPRIETARY/CONFIDENTIAL. Use is subject to license terms.
 */
package com.quantcapital.entities.constant;

import lombok.AllArgsConstructor;
import lombok.Getter;

/**
 * 股票池类型枚举
 * @author lijiechengbj
 */
@Getter
@AllArgsConstructor
public enum UniverseType {
    /** 静态股票池 */
    STATIC("静态"),
    /** 动态股票池 */
    DYNAMIC("动态"),
    /** 指数成分股 */
    INDEX("指数"),
    /** 行业股票池 */
    SECTOR("行业"),
    /** 自定义股票池 */
    CUSTOM("自定义");

    private final String description;
}