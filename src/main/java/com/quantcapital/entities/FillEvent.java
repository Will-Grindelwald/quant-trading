package com.quantcapital.entities;

import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/**
 * 成交事件
 * 
 * 订单成交时触发的事件。
 * 组合风控模块和策略模块监听此事件，更新持仓和账户信息。
 * 
 * @author QuantCapital Team
 */
@Data
@EqualsAndHashCode(callSuper = true)
@NoArgsConstructor
public class FillEvent extends Event {
    
    /** 成交对象 */
    private Fill fill;
    
    /** 触发此成交的订单ID */
    private String triggerOrderId;
    
    /**
     * 构造函数
     * 
     * @param timestamp 事件时间
     * @param fill 成交对象
     */
    public FillEvent(LocalDateTime timestamp, Fill fill) {
        super(EventType.FILL, timestamp, fill.getSymbol());
        this.fill = fill;
        this.triggerOrderId = fill.getOrderId();
        // 成交事件具有最高优先级，需要立即处理
        this.setPriority(1);
    }
    
    @Override
    public String getDescription() {
        return String.format("成交事件: %s", fill.toString());
    }
    
    /**
     * 获取成交ID
     * 
     * @return 成交ID
     */
    public String getFillId() {
        return fill != null ? fill.getFillId() : null;
    }
    
    /**
     * 获取订单ID
     * 
     * @return 订单ID
     */
    public String getOrderId() {
        return fill != null ? fill.getOrderId() : null;
    }
    
    /**
     * 获取策略ID
     * 
     * @return 策略ID
     */
    public String getStrategyId() {
        return fill != null ? fill.getStrategyId() : null;
    }
    
    /**
     * 获取成交方向
     * 
     * @return 成交方向
     */
    public OrderSide getFillSide() {
        return fill != null ? fill.getSide() : null;
    }
    
    /**
     * 获取成交数量
     * 
     * @return 成交数量
     */
    public int getFillQuantity() {
        return fill != null ? fill.getQuantity() : 0;
    }
    
    /**
     * 获取成交价格
     * 
     * @return 成交价格
     */
    public double getFillPrice() {
        return fill != null ? fill.getPrice() : 0.0;
    }
    
    /**
     * 获取成交金额
     * 
     * @return 成交金额
     */
    public double getFillAmount() {
        return fill != null ? fill.getAmount() : 0.0;
    }
    
    /**
     * 获取净成交金额（扣除费用后）
     * 
     * @return 净成交金额
     */
    public double getNetAmount() {
        return fill != null ? fill.getNetAmount() : 0.0;
    }
    
    /**
     * 获取总费用
     * 
     * @return 总费用
     */
    public double getTotalFee() {
        return fill != null ? fill.getTotalFee() : 0.0;
    }
    
    /**
     * 判断是否为买入成交
     * 
     * @return 是否为买入成交
     */
    public boolean isBuyFill() {
        return fill != null && fill.isBuyFill();
    }
    
    /**
     * 判断是否为卖出成交
     * 
     * @return 是否为卖出成交
     */
    public boolean isSellFill() {
        return fill != null && fill.isSellFill();
    }
    
    /**
     * 获取现金流影响
     * 
     * @return 现金流影响
     */
    public double getCashFlowImpact() {
        return fill != null ? fill.getCashFlowImpact() : 0.0;
    }
    
    /**
     * 获取市值影响
     * 
     * @return 市值影响
     */
    public double getMarketValueImpact() {
        return fill != null ? fill.getMarketValueImpact() : 0.0;
    }
    
    /**
     * 验证事件数据的完整性
     * 
     * @return 事件数据是否有效
     */
    public boolean isValidEvent() {
        return fill != null && fill.isValid() && getTimestamp() != null;
    }
}