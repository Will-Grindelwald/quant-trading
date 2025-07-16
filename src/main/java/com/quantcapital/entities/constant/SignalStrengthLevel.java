/**
 * @(#)SignalStrengthLevel.java, 7月 16, 2025.
 * <p>
 * Copyright 2025 yuanfudao.com. All rights reserved.
 * FENBI.COM PROPRIETARY/CONFIDENTIAL. Use is subject to license terms.
 */
package com.quantcapital.entities.constant;

/**
 * 信号强度级别枚举
 * 
 * 定义交易信号的强度级别，用于策略决策和仓位管理。
 * 强度级别从弱到强分为五个等级。
 * 
 * @author QuantCapital Team
 */
public enum SignalStrengthLevel {
    
    /** 弱信号 (20%) */
    WEAK(0.2, "弱信号"),
    
    /** 较弱信号 (40%) */
    BELOW_AVERAGE(0.4, "较弱信号"),
    
    /** 中等信号 (60%) */
    AVERAGE(0.6, "中等信号"),
    
    /** 较强信号 (80%) */
    ABOVE_AVERAGE(0.8, "较强信号"),
    
    /** 强信号 (100%) */
    STRONG(1.0, "强信号");
    
    /** 信号强度值 (0.0-1.0) */
    private final double strength;
    
    /** 描述 */
    private final String description;
    
    SignalStrengthLevel(double strength, String description) {
        this.strength = strength;
        this.description = description;
    }
    
    /**
     * 获取信号强度值
     * 
     * @return 强度值 (0.0-1.0)
     */
    public double getStrength() {
        return strength;
    }
    
    /**
     * 获取描述
     * 
     * @return 描述
     */
    public String getDescription() {
        return description;
    }
    
    /**
     * 根据强度值获取对应的级别
     * 
     * @param strength 强度值
     * @return 对应的级别
     */
    public static SignalStrengthLevel fromStrength(double strength) {
        if (strength <= 0.2) return WEAK;
        if (strength <= 0.4) return BELOW_AVERAGE;
        if (strength <= 0.6) return AVERAGE;
        if (strength <= 0.8) return ABOVE_AVERAGE;
        return STRONG;
    }
}