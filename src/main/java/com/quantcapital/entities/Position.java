package com.quantcapital.entities;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.math.RoundingMode;

/**
 * 持仓实体
 * 
 * 描述单个标的的持仓信息，包含数量、成本价和盈亏计算。
 * 支持多头和空头仓位的管理，提供完整的仓位操作和统计功能。
 * 
 * @author QuantCapital Team
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class Position {
    
    /** 标的代码 */
    private String symbol;
    
    /** 持仓数量（正数=多头，负数=空头，0=空仓） */
    private int quantity;
    
    /** 平均成本价 */
    private double avgPrice;
    
    /** 策略ID（标记开仓策略） */
    private String strategyId;
    
    /** 最后更新时间 */
    private java.time.LocalDateTime lastUpdateTime;
    
    /**
     * 构造函数
     * 
     * @param symbol 标的代码
     * @param quantity 持仓数量
     * @param avgPrice 平均成本价
     * @param strategyId 策略ID
     */
    public Position(String symbol, int quantity, double avgPrice, String strategyId) {
        this.symbol = symbol;
        this.quantity = quantity;
        this.avgPrice = avgPrice;
        this.strategyId = strategyId;
        this.lastUpdateTime = java.time.LocalDateTime.now();
        
        validate();
    }
    
    /**
     * 数据验证
     */
    private void validate() {
        if (symbol == null || symbol.trim().isEmpty()) {
            throw new IllegalArgumentException("标的代码不能为空");
        }
        if (avgPrice < 0) {
            throw new IllegalArgumentException("平均成本价不能为负数");
        }
    }
    
    /**
     * 是否为多头仓位
     * 
     * @return 是否为多头仓位
     */
    public boolean isLong() {
        return quantity > 0;
    }
    
    /**
     * 是否为空头仓位
     * 
     * @return 是否为空头仓位
     */
    public boolean isShort() {
        return quantity < 0;
    }
    
    /**
     * 是否为空仓
     * 
     * @return 是否为空仓
     */
    public boolean isEmpty() {
        return quantity == 0;
    }
    
    /**
     * 获取持仓市值（基于成本价）
     * 
     * @return 持仓市值
     */
    public double getMarketValue() {
        return Math.abs(quantity) * avgPrice;
    }
    
    /**
     * 计算未实现盈亏
     * 
     * @param currentPrice 当前价格
     * @return 未实现盈亏
     */
    public double getUnrealizedPnl(double currentPrice) {
        if (isEmpty()) {
            return 0.0;
        }
        return quantity * (currentPrice - avgPrice);
    }
    
    /**
     * 计算未实现盈亏百分比
     * 
     * @param currentPrice 当前价格
     * @return 未实现盈亏百分比
     */
    public double getUnrealizedPnlPct(double currentPrice) {
        if (isEmpty() || avgPrice == 0) {
            return 0.0;
        }
        return (currentPrice - avgPrice) / avgPrice;
    }
    
    /**
     * 计算当前市值（基于当前价格）
     * 
     * @param currentPrice 当前价格
     * @return 当前市值
     */
    public double getCurrentMarketValue(double currentPrice) {
        return Math.abs(quantity) * currentPrice;
    }
    
    /**
     * 更新持仓（基于成交记录）
     * 
     * @param quantityChange 数量变化（正数为买入，负数为卖出）
     * @param fillPrice 成交价格
     */
    public void updatePosition(int quantityChange, double fillPrice) {
        if (quantityChange == 0) {
            return;
        }
        
        int newQuantity = this.quantity + quantityChange;
        
        // 如果方向相同（加仓），计算新的平均成本价
        if ((this.quantity >= 0 && quantityChange > 0) || (this.quantity <= 0 && quantityChange < 0)) {
            if (newQuantity != 0) {
                double totalCost = (this.quantity * this.avgPrice) + (quantityChange * fillPrice);
                this.avgPrice = Math.abs(totalCost / newQuantity);
            }
        } 
        // 如果是减仓，成本价保持不变
        else if (Math.abs(quantityChange) <= Math.abs(this.quantity)) {
            // 减仓，成本价不变
        }
        // 如果是反向开仓（超过原持仓数量），使用新价格
        else if (newQuantity * this.quantity < 0) {
            this.avgPrice = fillPrice;
        }
        
        this.quantity = newQuantity;
        this.lastUpdateTime = java.time.LocalDateTime.now();
    }
    
    /**
     * 计算可平仓数量
     * 
     * @return 可平仓数量（绝对值）
     */
    public int getAvailableQuantity() {
        return Math.abs(quantity);
    }
    
    /**
     * 检查是否有足够仓位进行平仓
     * 
     * @param closeQuantity 平仓数量
     * @return 是否有足够仓位
     */
    public boolean hasEnoughPosition(int closeQuantity) {
        return Math.abs(quantity) >= closeQuantity;
    }
    
    /**
     * 复制当前持仓
     * 
     * @return 持仓副本
     */
    public Position copy() {
        return new Position(symbol, quantity, avgPrice, strategyId);
    }
    
    /**
     * 获取持仓方向描述
     * 
     * @return 持仓方向
     */
    public String getPositionDirection() {
        if (isLong()) {
            return "多头";
        } else if (isShort()) {
            return "空头";
        } else {
            return "空仓";
        }
    }
    
    /**
     * 格式化持仓信息为字符串
     * 
     * @param currentPrice 当前价格（可选，用于显示浮动盈亏）
     * @return 格式化字符串
     */
    public String formatPosition(Double currentPrice) {
        StringBuilder sb = new StringBuilder();
        sb.append(String.format("持仓[%s]", symbol));
        sb.append(String.format(" 数量:%d", quantity));
        sb.append(String.format(" 成本:%.2f", avgPrice));
        sb.append(String.format(" 市值:%.2f", getMarketValue()));
        
        if (currentPrice != null && !isEmpty()) {
            double unrealizedPnl = getUnrealizedPnl(currentPrice);
            double unrealizedPnlPct = getUnrealizedPnlPct(currentPrice) * 100;
            sb.append(String.format(" 浮动盈亏:%.2f(%.2f%%)", unrealizedPnl, unrealizedPnlPct));
        }
        
        return sb.toString();
    }
    
    @Override
    public String toString() {
        return String.format("Position[%s %s %d@%.2f]", 
                symbol, getPositionDirection(), Math.abs(quantity), avgPrice);
    }
}