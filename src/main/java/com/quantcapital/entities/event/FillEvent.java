package com.quantcapital.entities.event;

import com.quantcapital.entities.Fill;
import com.quantcapital.entities.constant.EventType;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/**
 * 成交事件
 * 
 * 当订单成交时触发，包含成交的详细信息。
 * 由执行模块生成，组合风控模块监听并更新账户状态。
 * 
 * @author QuantCapital Team
 */
@Data
@EqualsAndHashCode(callSuper = true)
@NoArgsConstructor
public class FillEvent extends Event {
    
    /**
     * 成交信息
     */
    private Fill fill;
    
    /**
     * 构造函数
     * 
     * @param timestamp 事件时间
     * @param fill 成交记录
     */
    public FillEvent(LocalDateTime timestamp, Fill fill) {
        super(EventType.FILL, timestamp, fill.getSymbol());
        this.fill = fill;
    }
    
    @Override
    public String getDescription() {
        return String.format("成交事件: %s %s %d@%.2f 净额:%.2f", 
                fill.getSymbol(), fill.getSide(), fill.getQuantity(), 
                fill.getPrice(), fill.getNetAmount());
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
     * 获取成交金额
     * 
     * @return 成交金额
     */
    public double getAmount() {
        return fill != null ? fill.getAmount() : 0.0;
    }
    
    /**
     * 获取手续费
     * 
     * @return 手续费
     */
    public double getCommission() {
        return fill != null ? fill.getTotalFee() : 0.0;
    }
    
    @Override
    public String toString() {
        return String.format("FillEvent[%s %s]", 
                getEventId(), fill != null ? fill.toString() : "null");
    }
}