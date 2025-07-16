package com.quantcapital.entities;

import com.fasterxml.jackson.annotation.JsonFormat;
import com.quantcapital.entities.constant.Frequency;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDateTime;

/**
 * K线数据实体
 * 
 * 包含OHLC价格数据、成交量以及各种技术指标。
 * 支持多个频率的K线数据，是量化分析的基础数据结构。
 * 
 * @author QuantCapital Team
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class Bar {
    
    /** 标的代码 */
    private String symbol;
    
    /** 时间戳 */
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private LocalDateTime datetime;
    
    /** 数据频率 */
    private Frequency frequency;
    
    // ==================== 基础OHLC数据 ====================
    
    /** 开盘价 */
    private double open;
    
    /** 最高价 */
    private double high;
    
    /** 最低价 */
    private double low;
    
    /** 收盘价 */
    private double close;
    
    /** 成交量（股） */
    private long volume;
    
    /** 成交额（元） */
    private double amount;
    
    /** 换手率（%） */
    private Double turnover;
    
    // ==================== 技术指标 ====================
    
    /** 5日移动平均线 */
    private Double ma5;
    
    /** 20日移动平均线 */
    private Double ma20;
    
    /** 60日移动平均线 */
    private Double ma60;
    
    /** MACD DIF线 */
    private Double macdDif;
    
    /** MACD DEA线 */
    private Double macdDea;
    
    /** MACD柱状图 */
    private Double macdHistogram;
    
    /** RSI相对强弱指标(0-100) */
    private Double rsi14;
    
    /** 布林带上轨 */
    private Double bollUpper;
    
    /** 布林带下轨 */
    private Double bollLower;
    
    // ==================== 基本面数据 ====================
    
    /** 总市值（亿元） */
    private Double marketCap;
    
    /** 流通市值（亿元） */
    private Double circulatingMarketCap;
    
    /** 是否ST股票 */
    private boolean isSt;
    
    /** 是否新股/次新股 */
    private boolean isNewStock;
    
    // ==================== 计算方法 ====================
    
    /**
     * 计算价格变化幅度
     * 
     * @return 价格变化幅度 (close - open) / open
     */
    public double getPriceChange() {
        if (open <= 0) {
            return 0.0;
        }
        return (close - open) / open;
    }
    
    /**
     * 计算价格变化幅度（百分比）
     * 
     * @return 价格变化百分比，保留2位小数
     */
    public double getPriceChangePercent() {
        return BigDecimal.valueOf(getPriceChange() * 100)
                .setScale(2, RoundingMode.HALF_UP)
                .doubleValue();
    }
    
    /**
     * 计算振幅
     * 
     * @return 振幅 (high - low) / open
     */
    public double getAmplitude() {
        if (open <= 0) {
            return 0.0;
        }
        return (high - low) / open;
    }
    
    /**
     * 计算上影线长度比例
     * 
     * @return 上影线长度占总振幅的比例
     */
    public double getUpperShadowRatio() {
        if (high <= low) {
            return 0.0;
        }
        double bodyHigh = Math.max(open, close);
        return (high - bodyHigh) / (high - low);
    }
    
    /**
     * 计算下影线长度比例
     * 
     * @return 下影线长度占总振幅的比例
     */
    public double getLowerShadowRatio() {
        if (high <= low) {
            return 0.0;
        }
        double bodyLow = Math.min(open, close);
        return (bodyLow - low) / (high - low);
    }
    
    /**
     * 判断是否为阳线
     * 
     * @return 是否为阳线
     */
    public boolean isGreen() {
        return close > open;
    }
    
    /**
     * 判断是否为阴线
     * 
     * @return 是否为阴线
     */
    public boolean isRed() {
        return close < open;
    }
    
    /**
     * 判断是否为十字星（开盘价和收盘价几乎相等）
     * 
     * @return 是否为十字星
     */
    public boolean isDoji() {
        if (open <= 0) {
            return false;
        }
        return Math.abs((close - open) / open) < 0.001; // 0.1%的阈值
    }
    
    /**
     * 判断是否有成交量异常放大
     * 
     * @param avgVolume 平均成交量
     * @param threshold 放大倍数阈值
     * @return 是否成交量异常
     */
    public boolean isVolumeAbnormal(long avgVolume, double threshold) {
        if (avgVolume <= 0) {
            return false;
        }
        return volume > avgVolume * threshold;
    }
    
    /**
     * 获取典型价格（HLC平均价）
     * 
     * @return 典型价格
     */
    public double getTypicalPrice() {
        return (high + low + close) / 3.0;
    }
    
    /**
     * 获取加权平均价格（VWAP近似）
     * 
     * @return 加权平均价格
     */
    public double getWeightedPrice() {
        return (open + high + low + close) / 4.0;
    }
    
    /**
     * 数据校验
     * 
     * @return 数据是否有效
     */
    public boolean isValid() {
        return symbol != null && !symbol.trim().isEmpty()
                && datetime != null
                && open > 0 && high > 0 && low > 0 && close > 0
                && high >= Math.max(open, close)
                && low <= Math.min(open, close)
                && volume >= 0
                && amount >= 0;
    }
    
    @Override
    public String toString() {
        return String.format("Bar[%s %s O:%.2f H:%.2f L:%.2f C:%.2f V:%d A:%.0f]",
                symbol, datetime, open, high, low, close, volume, amount);
    }
}