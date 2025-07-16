package com.quantcapital.entities;

import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/**
 * 订单事件
 * 
 * 组合风控模块生成订单时触发的事件。
 * 执行模块监听此事件，进行订单的实际执行。
 * 
 * @author QuantCapital Team
 */
@Data
@EqualsAndHashCode(callSuper = true)
@NoArgsConstructor
public class OrderEvent extends Event {
    
    /** 订单对象 */
    private Order order;
    
    /** 触发此订单的信号ID（可选，用于追溯） */
    private String triggerSignalId;
    
    /** 订单操作类型 */
    private OrderAction action;
    
    /**
     * 构造函数
     * 
     * @param timestamp 事件时间
     * @param order 订单对象
     * @param action 订单操作类型
     */
    public OrderEvent(LocalDateTime timestamp, Order order, OrderAction action) {
        super(EventType.ORDER, timestamp, order.getSymbol());
        this.order = order;
        this.action = action;
        // 订单事件具有较高优先级
        this.setPriority(2);
    }
    
    /**
     * 构造函数（带触发信号ID）
     * 
     * @param timestamp 事件时间
     * @param order 订单对象
     * @param action 订单操作类型
     * @param triggerSignalId 触发的信号ID
     */
    public OrderEvent(LocalDateTime timestamp, Order order, OrderAction action, String triggerSignalId) {
        this(timestamp, order, action);
        this.triggerSignalId = triggerSignalId;
    }
    
    @Override
    public String getDescription() {
        return String.format("订单事件: %s %s %s", 
                action.getDescription(), order.toString(), 
                triggerSignalId != null ? "信号:" + triggerSignalId : "");
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
    
    /**
     * 获取订单方向
     * 
     * @return 订单方向
     */
    public OrderSide getOrderSide() {
        return order != null ? order.getSide() : null;
    }
    
    /**
     * 获取订单数量
     * 
     * @return 订单数量
     */
    public int getOrderQuantity() {
        return order != null ? order.getQuantity() : 0;
    }
    
    /**
     * 获取订单价格
     * 
     * @return 订单价格
     */
    public double getOrderPrice() {
        return order != null ? order.getPrice() : 0.0;
    }
    
    /**
     * 判断是否为新订单事件
     * 
     * @return 是否为新订单事件
     */
    public boolean isNewOrder() {
        return action == OrderAction.NEW;
    }
    
    /**
     * 判断是否为取消订单事件
     * 
     * @return 是否为取消订单事件
     */
    public boolean isCancelOrder() {
        return action == OrderAction.CANCEL;
    }
    
    /**
     * 判断是否为修改订单事件
     * 
     * @return 是否为修改订单事件
     */
    public boolean isModifyOrder() {
        return action == OrderAction.MODIFY;
    }
    
    /**
     * 验证事件数据的完整性
     * 
     * @return 事件数据是否有效
     */
    public boolean isValidEvent() {
        return order != null && action != null && getTimestamp() != null;
    }
}

/**
 * 订单操作类型枚举
 */
enum OrderAction {
    /** 新建订单 */
    NEW("新建订单"),
    
    /** 取消订单 */
    CANCEL("取消订单"),
    
    /** 修改订单 */
    MODIFY("修改订单");
    
    private final String description;
    
    OrderAction(String description) {
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