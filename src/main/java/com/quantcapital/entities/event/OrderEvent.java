package com.quantcapital.entities.event;

import com.quantcapital.entities.Order;
import com.quantcapital.entities.constant.EventType;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/**
 * 订单事件
 * 
 * 当需要执行订单时触发，包含订单的详细信息。
 * 由组合风控模块生成，执行模块监听并处理。
 * 
 * @author QuantCapital Team
 */
@Data
@EqualsAndHashCode(callSuper = true)
@NoArgsConstructor
public class OrderEvent extends Event {
    
    /**
     * 订单信息
     */
    private Order order;
    
    /**
     * 构造函数
     * 
     * @param timestamp 事件时间
     * @param order 订单
     */
    public OrderEvent(LocalDateTime timestamp, Order order) {
        super(EventType.ORDER, timestamp, order.getSymbol());
        this.order = order;
    }
    
    @Override
    public String getDescription() {
        return String.format("订单事件: %s %s %d@%.2f", 
                order.getSymbol(), order.getSide(), order.getQuantity(), order.getPrice());
    }
    
    /**
     * 获取订单ID
     * 
     * @return 订单ID
     */
    public String getOrderId() {
        return order != null ? order.getOrderId() : null;
    }
    
    /**
     * 获取策略ID
     * 
     * @return 策略ID
     */
    public String getStrategyId() {
        return order != null ? order.getStrategyId() : null;
    }
    
    @Override
    public String toString() {
        return String.format("OrderEvent[%s %s]", 
                getEventId(), order != null ? order.toString() : "null");
    }
}
