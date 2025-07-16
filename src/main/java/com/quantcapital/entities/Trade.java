package com.quantcapital.entities;

import com.fasterxml.jackson.annotation.JsonFormat;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.UUID;

/**
 * 交易实体
 * 
 * 描述一次完整的买卖交易周期，从开仓到平仓的完整记录。
 * 包含买入和卖出的详细信息，以及盈亏计算。
 * 
 * @author QuantCapital Team
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class Trade {
    
    /** 交易ID，全局唯一 */
    private String tradeId;
    
    /** 标的代码 */
    private String symbol;
    
    /** 策略ID */
    private String strategyId;
    
    // ==================== 开仓信息 ====================
    
    /** 开仓成交记录 */
    private Fill openFill;
    
    /** 开仓时间 */
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private LocalDateTime openTime;
    
    /** 开仓价格 */
    private double openPrice;
    
    /** 开仓数量 */
    private int openQuantity;
    
    /** 开仓方向（买入/卖出） */
    private String openSide;
    
    // ==================== 平仓信息 ====================
    
    /** 平仓成交记录 */
    private Fill closeFill;
    
    /** 平仓时间 */
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private LocalDateTime closeTime;
    
    /** 平仓价格 */
    private Double closePrice;
    
    /** 平仓数量 */
    private Integer closeQuantity;
    
    /** 平仓方向（买入/卖出） */
    private String closeSide;
    
    // ==================== 交易结果 ====================
    
    /** 已实现盈亏 */
    private double realizedPnl;
    
    /** 总手续费 */
    private double totalCommission;
    
    /** 持续时间（秒） */
    private Long durationSeconds;
    
    /** 交易状态 */
    private TradeStatus status;
    
    /** 盈亏百分比 */
    private Double pnlPercentage;
    
    /** 交易备注 */
    private String remarks;
    
    /**
     * 交易状态枚举
     */
    public enum TradeStatus {
        /** 开仓状态 */
        OPEN("开仓"),
        /** 已平仓 */
        CLOSED("已平仓"),
        /** 部分平仓 */
        PARTIALLY_CLOSED("部分平仓");
        
        private final String description;
        
        TradeStatus(String description) {
            this.description = description;
        }
        
        public String getDescription() {
            return description;
        }
    }
    
    /**
     * 构造函数（开仓）
     * 
     * @param symbol 标的代码
     * @param strategyId 策略ID
     * @param openFill 开仓成交记录
     */
    public Trade(String symbol, String strategyId, Fill openFill) {
        this.tradeId = UUID.randomUUID().toString();
        this.symbol = symbol;
        this.strategyId = strategyId;
        this.openFill = openFill;
        this.openTime = openFill.getTimestamp();
        this.openPrice = openFill.getPrice();
        this.openQuantity = openFill.getQuantity();
        this.openSide = openFill.getSide().toString();
        this.status = TradeStatus.OPEN;
        this.totalCommission = openFill.getTotalFee();
        this.realizedPnl = 0.0;
    }
    
    /**
     * 平仓操作
     * 
     * @param closeFill 平仓成交记录
     */
    public void closeTrade(Fill closeFill) {
        if (status == TradeStatus.CLOSED) {
            throw new IllegalStateException("交易已关闭，无法重复关闭");
        }
        
        if (!closeFill.getSymbol().equals(this.symbol)) {
            throw new IllegalArgumentException("平仓标的与开仓标的不匹配");
        }
        
        // 检查平仓方向是否正确
        boolean isValidClose = false;
        if (openSide.equals("BUY") && closeFill.getSide().toString().equals("SELL")) {
            isValidClose = true;
        } else if (openSide.equals("SELL") && closeFill.getSide().toString().equals("BUY")) {
            isValidClose = true;
        }
        
        if (!isValidClose) {
            throw new IllegalArgumentException("平仓方向错误");
        }
        
        this.closeFill = closeFill;
        this.closeTime = closeFill.getTimestamp();
        this.closePrice = closeFill.getPrice();
        this.closeQuantity = closeFill.getQuantity();
        this.closeSide = closeFill.getSide().toString();
        this.totalCommission += closeFill.getTotalFee();
        
        // 计算持续时间
        this.durationSeconds = java.time.Duration.between(openTime, closeTime).getSeconds();
        
        // 计算已实现盈亏
        calculateRealizedPnl();
        
        // 更新状态
        if (closeQuantity >= openQuantity) {
            this.status = TradeStatus.CLOSED;
        } else {
            this.status = TradeStatus.PARTIALLY_CLOSED;
        }
    }
    
    /**
     * 计算已实现盈亏
     */
    private void calculateRealizedPnl() {
        if (closePrice == null || closeQuantity == null) {
            return;
        }
        
        int actualQuantity = Math.min(openQuantity, closeQuantity);
        
        // 根据开仓方向计算盈亏
        if (openSide.equals("BUY")) {
            // 买入开仓，卖出平仓
            this.realizedPnl = (closePrice - openPrice) * actualQuantity - totalCommission;
        } else {
            // 卖出开仓，买入平仓
            this.realizedPnl = (openPrice - closePrice) * actualQuantity - totalCommission;
        }
        
        // 计算盈亏百分比
        double investedAmount = openPrice * actualQuantity;
        if (investedAmount > 0) {
            this.pnlPercentage = (realizedPnl / investedAmount) * 100;
        }
    }
    
    /**
     * 判断是否为开仓状态
     * 
     * @return 是否为开仓状态
     */
    public boolean isOpen() {
        return status == TradeStatus.OPEN;
    }
    
    /**
     * 判断是否已平仓
     * 
     * @return 是否已平仓
     */
    public boolean isClosed() {
        return status == TradeStatus.CLOSED;
    }
    
    /**
     * 判断是否部分平仓
     * 
     * @return 是否部分平仓
     */
    public boolean isPartiallyClosed() {
        return status == TradeStatus.PARTIALLY_CLOSED;
    }
    
    /**
     * 获取交易方向
     * 
     * @return 交易方向（多头/空头）
     */
    public String getTradeDirection() {
        return openSide.equals("BUY") ? "多头" : "空头";
    }
    
    /**
     * 获取交易持续时间（格式化）
     * 
     * @return 格式化的持续时间
     */
    public String getFormattedDuration() {
        if (durationSeconds == null) {
            return "进行中";
        }
        
        long hours = durationSeconds / 3600;
        long minutes = (durationSeconds % 3600) / 60;
        long seconds = durationSeconds % 60;
        
        if (hours > 0) {
            return String.format("%d小时%d分钟", hours, minutes);
        } else if (minutes > 0) {
            return String.format("%d分钟%d秒", minutes, seconds);
        } else {
            return String.format("%d秒", seconds);
        }
    }
    
    /**
     * 获取盈亏状态
     * 
     * @return 盈亏状态
     */
    public String getPnlStatus() {
        if (realizedPnl > 0) {
            return "盈利";
        } else if (realizedPnl < 0) {
            return "亏损";
        } else {
            return "持平";
        }
    }
    
    /**
     * 获取投资回报率
     * 
     * @return 投资回报率（百分比）
     */
    public double getReturnRate() {
        if (pnlPercentage != null) {
            return pnlPercentage;
        }
        return 0.0;
    }
    
    /**
     * 获取剩余开仓数量
     * 
     * @return 剩余开仓数量
     */
    public int getRemainingQuantity() {
        if (closeQuantity == null) {
            return openQuantity;
        }
        return Math.max(0, openQuantity - closeQuantity);
    }
    
    /**
     * 检查是否可以平仓指定数量
     * 
     * @param quantity 平仓数量
     * @return 是否可以平仓
     */
    public boolean canClose(int quantity) {
        return getRemainingQuantity() >= quantity;
    }
    
    @Override
    public String toString() {
        if (isClosed()) {
            return String.format("Trade[%s %s %s %.2f->%.2f 盈亏:%.2f(%.2f%%)]",
                    symbol, getTradeDirection(), status.getDescription(),
                    openPrice, closePrice, realizedPnl, getReturnRate());
        } else {
            return String.format("Trade[%s %s %s %.2f 数量:%d]",
                    symbol, getTradeDirection(), status.getDescription(),
                    openPrice, getRemainingQuantity());
        }
    }
}