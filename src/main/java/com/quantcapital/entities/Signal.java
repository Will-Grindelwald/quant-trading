package com.quantcapital.entities;

import com.fasterxml.jackson.annotation.JsonFormat;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.UUID;

/**
 * 交易信号实体
 * 
 * 策略产生的交易信号，包含买卖方向、信号强度、价格等信息。
 * 是策略模块输出到组合风控模块的核心数据结构。
 * 
 * @author QuantCapital Team
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class Signal {
    
    /** 信号ID，全局唯一 */
    private String signalId;
    
    /** 策略ID */
    private String strategyId;
    
    /** 标的代码 */
    private String symbol;
    
    /** 信号方向 */
    private SignalDirection direction;
    
    /** 信号强度 (0.0-1.0，1.0为最强) */
    private double strength;
    
    /** 信号时间戳 */
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private LocalDateTime timestamp;
    
    /** 信号参考价格（策略决策时的价格） */
    private double referencePrice;
    
    /** 建议仓位大小（可选，由策略提供建议） */
    private Double suggestedPositionSize;
    
    /** 信号原因/描述（用于复盘分析） */
    private String reason;
    
    /** 信号优先级 (1-10，数值越小优先级越高) */
    private int priority;
    
    /** 信号有效期（秒） */
    private long validitySeconds;
    
    /** 止损价格（可选） */
    private Double stopLossPrice;
    
    /** 止盈价格（可选） */
    private Double takeProfitPrice;
    
    /**
     * 默认构造函数，生成UUID作为信号ID
     */
    public Signal(String strategyId, String symbol, SignalDirection direction, 
                  double strength, LocalDateTime timestamp, double referencePrice, String reason) {
        this.signalId = UUID.randomUUID().toString();
        this.strategyId = strategyId;
        this.symbol = symbol;
        this.direction = direction;
        this.strength = Math.max(0.0, Math.min(1.0, strength)); // 限制在0-1范围内
        this.timestamp = timestamp;
        this.referencePrice = referencePrice;
        this.reason = reason;
        this.priority = 5; // 默认中等优先级
        this.validitySeconds = 300; // 默认5分钟有效期
    }
    
    /**
     * 判断信号是否已过期
     * 
     * @param currentTime 当前时间
     * @return 是否过期
     */
    public boolean isExpired(LocalDateTime currentTime) {
        return timestamp.plusSeconds(validitySeconds).isBefore(currentTime);
    }
    
    /**
     * 判断是否为买入信号
     * 
     * @return 是否为买入信号
     */
    public boolean isBuySignal() {
        return direction == SignalDirection.BUY;
    }
    
    /**
     * 判断是否为卖出信号
     * 
     * @return 是否为卖出信号
     */
    public boolean isSellSignal() {
        return direction == SignalDirection.SELL;
    }
    
    /**
     * 判断是否为持有信号
     * 
     * @return 是否为持有信号
     */
    public boolean isHoldSignal() {
        return direction == SignalDirection.HOLD;
    }
    
    /**
     * 获取信号强度级别
     * 
     * @return 信号强度级别
     */
    public SignalStrengthLevel getStrengthLevel() {
        if (strength >= 0.8) {
            return SignalStrengthLevel.VERY_STRONG;
        } else if (strength >= 0.6) {
            return SignalStrengthLevel.STRONG;
        } else if (strength >= 0.4) {
            return SignalStrengthLevel.MODERATE;
        } else if (strength >= 0.2) {
            return SignalStrengthLevel.WEAK;
        } else {
            return SignalStrengthLevel.VERY_WEAK;
        }
    }
    
    /**
     * 计算与当前价格的价差幅度
     * 
     * @param currentPrice 当前价格
     * @return 价差幅度
     */
    public double getPriceDifference(double currentPrice) {
        if (referencePrice <= 0) {
            return 0.0;
        }
        return (currentPrice - referencePrice) / referencePrice;
    }
    
    /**
     * 设置止损价格（基于参考价格的百分比）
     * 
     * @param stopLossPercent 止损百分比（如0.05表示5%）
     */
    public void setStopLossByPercent(double stopLossPercent) {
        if (referencePrice > 0) {
            if (isBuySignal()) {
                this.stopLossPrice = referencePrice * (1 - stopLossPercent);
            } else if (isSellSignal()) {
                this.stopLossPrice = referencePrice * (1 + stopLossPercent);
            }
        }
    }
    
    /**
     * 设置止盈价格（基于参考价格的百分比）
     * 
     * @param takeProfitPercent 止盈百分比（如0.10表示10%）
     */
    public void setTakeProfitByPercent(double takeProfitPercent) {
        if (referencePrice > 0) {
            if (isBuySignal()) {
                this.takeProfitPrice = referencePrice * (1 + takeProfitPercent);
            } else if (isSellSignal()) {
                this.takeProfitPrice = referencePrice * (1 - takeProfitPercent);
            }
        }
    }
    
    /**
     * 验证信号数据的有效性
     * 
     * @return 信号是否有效
     */
    public boolean isValid() {
        return signalId != null && !signalId.trim().isEmpty()
                && strategyId != null && !strategyId.trim().isEmpty()
                && symbol != null && !symbol.trim().isEmpty()
                && direction != null
                && strength >= 0.0 && strength <= 1.0
                && timestamp != null
                && referencePrice > 0
                && priority >= 1 && priority <= 10
                && validitySeconds > 0;
    }
    
    @Override
    public String toString() {
        return String.format("Signal[%s %s %s强度%.2f 价格%.2f 原因:%s]",
                symbol, direction, getStrengthLevel(), strength, referencePrice, reason);
    }
}

/**
 * 信号方向枚举
 */
enum SignalDirection {
    /** 买入 */
    BUY("买入"),
    
    /** 卖出 */
    SELL("卖出"),
    
    /** 持有/无操作 */
    HOLD("持有");
    
    private final String description;
    
    SignalDirection(String description) {
        this.description = description;
    }
    
    public String getDescription() {
        return description;
    }
    
    @Override
    public String toString() {
        return description;
    }
}

/**
 * 信号强度级别枚举
 */
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
    
    SignalStrengthLevel(String description) {
        this.description = description;
    }
    
    public String getDescription() {
        return description;
    }
    
    @Override
    public String toString() {
        return description;
    }
}