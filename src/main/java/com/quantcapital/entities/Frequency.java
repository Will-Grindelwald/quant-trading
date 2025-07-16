package com.quantcapital.entities;

import lombok.AllArgsConstructor;

/**
 * 数据频率枚举
 * 
 * 定义K线数据的时间频率，支持分钟、小时、日线等不同级别的数据。
 * 
 * @author QuantCapital Team
 */
@AllArgsConstructor
public enum Frequency {
    
    /** 1分钟 */
    MINUTE_1("1m", "1分钟", 1),
    
    /** 5分钟 */
    MINUTE_5("5m", "5分钟", 5),
    
    /** 15分钟 */
    MINUTE_15("15m", "15分钟", 15),
    
    /** 30分钟 */
    MINUTE_30("30m", "30分钟", 30),
    
    /** 1小时 */
    HOURLY("1h", "1小时", 60),
    
    /** 4小时 */
    HOUR_4("4h", "4小时", 240),
    
    /** 日线 */
    DAILY("1d", "日线", 1440),
    
    /** 周线 */
    WEEKLY("1w", "周线", 10080),
    
    /** 月线 */
    MONTHLY("1M", "月线", 43200);
    
    /** 频率代码 */
    private final String code;
    
    /** 中文描述 */
    private final String description;
    
    /** 分钟数 */
    private final int minutes;
    
    /**
     * 获取频率代码
     * 
     * @return 频率代码
     */
    public String getCode() {
        return code;
    }
    
    /**
     * 获取中文描述
     * 
     * @return 中文描述
     */
    public String getDescription() {
        return description;
    }
    
    /**
     * 获取对应的分钟数
     * 
     * @return 分钟数
     */
    public int getMinutes() {
        return minutes;
    }
    
    /**
     * 根据代码获取频率
     * 
     * @param code 频率代码
     * @return 对应的频率枚举，如果找不到返回null
     */
    public static Frequency fromCode(String code) {
        for (Frequency frequency : values()) {
            if (frequency.code.equals(code)) {
                return frequency;
            }
        }
        return null;
    }
    
    /**
     * 判断是否为日内频率（小于1天）
     * 
     * @return 是否为日内频率
     */
    public boolean isIntraday() {
        return minutes < DAILY.minutes;
    }
    
    /**
     * 判断是否为高频数据（小于1小时）
     * 
     * @return 是否为高频数据
     */
    public boolean isHighFrequency() {
        return minutes < HOURLY.minutes;
    }
    
    /**
     * 获取下一个更高级别的频率
     * 
     * @return 更高级别的频率，如果已经是最高级别则返回自身
     */
    public Frequency getNextHigherFrequency() {
        switch (this) {
            case MINUTE_1:
                return MINUTE_5;
            case MINUTE_5:
                return MINUTE_15;
            case MINUTE_15:
                return MINUTE_30;
            case MINUTE_30:
                return HOURLY;
            case HOURLY:
                return HOUR_4;
            case HOUR_4:
                return DAILY;
            case DAILY:
                return WEEKLY;
            case WEEKLY:
                return MONTHLY;
            default:
                return this;
        }
    }
    
    @Override
    public String toString() {
        return String.format("%s(%s)", description, code);
    }
}