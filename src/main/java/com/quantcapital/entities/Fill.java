package com.quantcapital.entities;

import com.fasterxml.jackson.annotation.JsonFormat;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.UUID;

/**
 * 成交实体
 * 
 * 表示订单的一次成交记录，包含成交的数量、价格、手续费等信息。
 * 一个订单可能对应多次成交（部分成交的情况）。
 * 
 * @author QuantCapital Team
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class Fill {
    
    /** 成交ID，全局唯一 */
    private String fillId;
    
    /** 关联的订单ID */
    private String orderId;
    
    /** 标的代码 */
    private String symbol;
    
    /** 买卖方向 */
    private OrderSide side;
    
    /** 成交数量（股） */
    private int quantity;
    
    /** 成交价格 */
    private double price;
    
    /** 成交金额（不含手续费） */
    private double amount;
    
    /** 手续费 */
    private double commission;
    
    /** 印花税（卖出时收取） */
    private double stampTax;
    
    /** 过户费 */
    private double transferFee;
    
    /** 总费用（手续费 + 印花税 + 过户费） */
    private double totalFee;
    
    /** 净成交金额（成交金额 - 总费用，买入为负，卖出为正） */
    private double netAmount;
    
    /** 成交时间 */
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private LocalDateTime timestamp;
    
    /** 策略ID */
    private String strategyId;
    
    /** 交易所成交编号（实盘交易时使用） */
    private String exchangeTradeId;
    
    /** 是否为模拟成交 */
    private boolean isSimulated;
    
    /**
     * 构造函数
     * 
     * @param orderId 订单ID
     * @param symbol 标的代码
     * @param side 买卖方向
     * @param quantity 成交数量
     * @param price 成交价格
     * @param timestamp 成交时间
     * @param strategyId 策略ID
     */
    public Fill(String orderId, String symbol, OrderSide side, 
                int quantity, double price, LocalDateTime timestamp, String strategyId) {
        this.fillId = UUID.randomUUID().toString();
        this.orderId = orderId;
        this.symbol = symbol;
        this.side = side;
        this.quantity = quantity;
        this.price = price;
        this.timestamp = timestamp;
        this.strategyId = strategyId;
        this.isSimulated = true; // 默认为模拟成交
        
        // 计算成交金额和费用
        calculateAmountAndFees();
    }
    
    /**
     * 计算成交金额和各项费用
     */
    private void calculateAmountAndFees() {
        // 成交金额
        this.amount = quantity * price;
        
        // 手续费（双向收取，最低5元）
        this.commission = Math.max(amount * 0.0003, 5.0);
        
        // 印花税（仅卖出收取）
        this.stampTax = (side == OrderSide.SELL) ? amount * 0.001 : 0.0;
        
        // 过户费（按成交金额的0.002%，最低1元）
        this.transferFee = Math.max(amount * 0.00002, 1.0);
        
        // 总费用
        this.totalFee = commission + stampTax + transferFee;
        
        // 净成交金额（买入为负数，卖出为正数）
        if (side == OrderSide.BUY) {
            this.netAmount = -(amount + totalFee);
        } else {
            this.netAmount = amount - totalFee;
        }
    }
    
    /**
     * 使用自定义费率计算费用
     * 
     * @param commissionRate 手续费率
     * @param stampTaxRate 印花税率（仅卖出）
     * @param transferFeeRate 过户费率
     * @param minCommission 最低手续费
     */
    public void calculateFeesWithCustomRates(double commissionRate, double stampTaxRate, 
                                           double transferFeeRate, double minCommission) {
        // 手续费
        this.commission = Math.max(amount * commissionRate, minCommission);
        
        // 印花税（仅卖出收取）
        this.stampTax = (side == OrderSide.SELL) ? amount * stampTaxRate : 0.0;
        
        // 过户费
        this.transferFee = amount * transferFeeRate;
        
        // 总费用
        this.totalFee = commission + stampTax + transferFee;
        
        // 重新计算净成交金额
        if (side == OrderSide.BUY) {
            this.netAmount = -(amount + totalFee);
        } else {
            this.netAmount = amount - totalFee;
        }
    }
    
    /**
     * 计算实际收益率（相对于成交金额）
     * 
     * @return 收益率
     */
    public double getReturnRate() {
        if (amount <= 0) {
            return 0.0;
        }
        return netAmount / amount;
    }
    
    /**
     * 计算费用率（总费用占成交金额的比例）
     * 
     * @return 费用率
     */
    public double getFeeRate() {
        if (amount <= 0) {
            return 0.0;
        }
        return totalFee / amount;
    }
    
    /**
     * 判断是否为买入成交
     * 
     * @return 是否为买入成交
     */
    public boolean isBuyFill() {
        return side == OrderSide.BUY;
    }
    
    /**
     * 判断是否为卖出成交
     * 
     * @return 是否为卖出成交
     */
    public boolean isSellFill() {
        return side == OrderSide.SELL;
    }
    
    /**
     * 获取成交的市值影响（买入为正，卖出为负）
     * 
     * @return 市值影响
     */
    public double getMarketValueImpact() {
        return isBuyFill() ? amount : -amount;
    }
    
    /**
     * 获取现金流影响（买入为负，卖出为正）
     * 
     * @return 现金流影响
     */
    public double getCashFlowImpact() {
        return netAmount;
    }
    
    /**
     * 验证成交数据的有效性
     * 
     * @return 成交数据是否有效
     */
    public boolean isValid() {
        return fillId != null && !fillId.trim().isEmpty()
                && orderId != null && !orderId.trim().isEmpty()
                && symbol != null && !symbol.trim().isEmpty()
                && side != null
                && quantity > 0
                && price > 0
                && amount > 0
                && timestamp != null;
    }
    
    /**
     * 创建成交的反向操作（用于撤销等场景）
     * 
     * @return 反向成交
     */
    public Fill createReverse() {
        OrderSide reverseSide = (side == OrderSide.BUY) ? OrderSide.SELL : OrderSide.BUY;
        Fill reverse = new Fill(orderId, symbol, reverseSide, quantity, price, 
                               LocalDateTime.now(), strategyId);
        reverse.setSimulated(this.isSimulated);
        return reverse;
    }
    
    @Override
    public String toString() {
        return String.format("Fill[%s %s %s %d@%.2f 净额%.2f 费用%.2f]",
                symbol, side, quantity, price, netAmount, totalFee, fillId.substring(0, 8));
    }
}