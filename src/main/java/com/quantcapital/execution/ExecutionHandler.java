package com.quantcapital.execution;

import com.quantcapital.engine.EventEngine;
import com.quantcapital.engine.EventHandler;
import com.quantcapital.entities.Order;
import com.quantcapital.entities.Fill;
import com.quantcapital.entities.event.OrderEvent;
import com.quantcapital.entities.event.FillEvent;
import com.quantcapital.entities.constant.EventType;
import com.quantcapital.entities.constant.OrderStatus;
import lombok.extern.slf4j.Slf4j;

import java.time.LocalDateTime;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 执行处理器基类
 * 
 * 负责订单的实际执行，是策略逻辑和市场交易的最终桥梁。
 * 支持回测模拟执行和实盘真实执行。
 * 
 * @author QuantCapital Team
 */
@Slf4j
public abstract class ExecutionHandler extends EventHandler {
    
    /** 事件引擎 */
    protected final EventEngine eventEngine;
    
    /** 活跃订单列表 */
    protected final Map<String, Order> activeOrders;
    
    /** 执行统计 */
    protected final ExecutionStatistics statistics;
    
    /**
     * 构造函数
     * 
     * @param name 执行器名称
     * @param eventEngine 事件引擎
     */
    public ExecutionHandler(String name, EventEngine eventEngine) {
        super(name);
        this.eventEngine = eventEngine;
        this.activeOrders = new ConcurrentHashMap<>();
        this.statistics = new ExecutionStatistics();
        
        // 注册事件处理
        this.eventEngine.register(EventType.ORDER.name(), this);
        log.info("执行处理器初始化完成: {}", name);
    }
    
    @Override
    public void handleEvent(Object event) {
        if (event instanceof OrderEvent) {
            OrderEvent orderEvent = (OrderEvent) event;
            executeOrder(orderEvent.getOrder());
        }
    }
    
    /**
     * 执行订单
     * 
     * @param order 订单
     */
    public void executeOrder(Order order) {
        try {
            log.info("开始执行订单: {}", order);
            
            // 验证订单
            if (!validateOrder(order)) {
                rejectOrder(order, "订单验证失败");
                return;
            }
            
            // 将订单加入活跃列表
            activeOrders.put(order.getOrderId(), order);
            
            // 更新订单状态为已提交
            order.updateStatus(OrderStatus.SUBMITTED);
            
            // 执行具体的订单处理逻辑
            doExecuteOrder(order);
            
            // 更新统计信息
            statistics.incrementOrderCount();
            
        } catch (Exception e) {
            log.error("执行订单失败: {}", order, e);
            rejectOrder(order, "执行异常: " + e.getMessage());
        }
    }
    
    /**
     * 具体的订单执行逻辑（由子类实现）
     * 
     * @param order 订单
     */
    protected abstract void doExecuteOrder(Order order);
    
    /**
     * 取消订单
     * 
     * @param orderId 订单ID
     * @return 是否取消成功
     */
    public boolean cancelOrder(String orderId) {
        Order order = activeOrders.get(orderId);
        if (order == null) {
            log.warn("订单不存在，无法取消: {}", orderId);
            return false;
        }
        
        if (!order.isCancellable()) {
            log.warn("订单状态不允许取消: {}", order);
            return false;
        }
        
        try {
            // 执行具体的取消逻辑
            boolean cancelled = doCancelOrder(order);
            if (cancelled) {
                order.updateStatus(OrderStatus.CANCELLED);
                activeOrders.remove(orderId);
                statistics.incrementCancelCount();
                log.info("订单取消成功: {}", orderId);
            }
            return cancelled;
        } catch (Exception e) {
            log.error("取消订单失败: {}", orderId, e);
            return false;
        }
    }
    
    /**
     * 具体的订单取消逻辑（由子类实现）
     * 
     * @param order 订单
     * @return 是否取消成功
     */
    protected abstract boolean doCancelOrder(Order order);
    
    /**
     * 查询订单状态
     * 
     * @param orderId 订单ID
     * @return 订单状态，如果订单不存在返回null
     */
    public OrderStatus getOrderStatus(String orderId) {
        Order order = activeOrders.get(orderId);
        return order != null ? order.getStatus() : null;
    }
    
    /**
     * 验证订单
     * 
     * @param order 订单
     * @return 是否有效
     */
    protected boolean validateOrder(Order order) {
        if (order == null) {
            log.warn("订单为空");
            return false;
        }
        
        if (!order.isValid(LocalDateTime.now())) {
            log.warn("订单数据无效: {}", order);
            return false;
        }
        
        // 子类可以覆盖此方法添加更多验证逻辑
        return true;
    }
    
    /**
     * 拒绝订单
     * 
     * @param order 订单
     * @param reason 拒绝原因
     */
    protected void rejectOrder(Order order, String reason) {
        order.updateStatus(OrderStatus.REJECTED);
        order.setCancelReason(reason);
        statistics.incrementRejectCount();
        log.warn("订单被拒绝: {} 原因: {}", order, reason);
    }
    
    /**
     * 发送成交事件
     * 
     * @param fill 成交记录
     */
    protected void sendFillEvent(Fill fill) {
        FillEvent fillEvent = new FillEvent();
        fillEvent.setEventId(java.util.UUID.randomUUID().toString());
        fillEvent.setType(EventType.FILL);
        fillEvent.setTimestamp(LocalDateTime.now());
        fillEvent.setSymbol(fill.getSymbol());
        fillEvent.setFill(fill);
        
        eventEngine.put(fillEvent);
        statistics.incrementFillCount();
        log.info("发送成交事件: {}", fill);
    }
    
    /**
     * 处理订单成交
     * 
     * @param order 订单
     * @param fillQuantity 成交数量
     * @param fillPrice 成交价格
     */
    protected void processOrderFill(Order order, int fillQuantity, double fillPrice) {
        // 创建成交记录
        Fill fill = new Fill(
                order.getOrderId(),
                order.getSymbol(),
                order.getSide(),
                fillQuantity,
                fillPrice,
                LocalDateTime.now(),
                order.getStrategyId()
        );
        
        // 更新订单状态
        order.updatePartialFill(fillQuantity, fillPrice);
        
        // 检查订单是否完全成交
        if (order.getRemainingQuantity() == 0) {
            order.updateStatus(OrderStatus.FILLED);
            activeOrders.remove(order.getOrderId());
        } else {
            order.updateStatus(OrderStatus.PARTIALLY_FILLED);
        }
        
        // 发送成交事件
        sendFillEvent(fill);
        
        log.info("订单部分成交: {} 成交数量: {} 成交价格: {}", 
                order.getOrderId(), fillQuantity, fillPrice);
    }
    
    /**
     * 获取活跃订单数量
     * 
     * @return 活跃订单数量
     */
    public int getActiveOrderCount() {
        return activeOrders.size();
    }
    
    /**
     * 获取执行统计信息
     * 
     * @return 统计信息
     */
    public ExecutionStatistics getStatistics() {
        return statistics;
    }
    
    /**
     * 清理已完成的订单
     */
    public void cleanupFinishedOrders() {
        activeOrders.entrySet().removeIf(entry -> {
            Order order = entry.getValue();
            return order.isFinished();
        });
    }
    
    /**
     * 获取所有活跃订单
     * 
     * @return 活跃订单映射
     */
    public Map<String, Order> getActiveOrders() {
        return new ConcurrentHashMap<>(activeOrders);
    }
    
    /**
     * 停止执行器
     */
    public void stop() {
        log.info("停止执行处理器: {}", getName());
        
        // 取消所有活跃订单
        for (String orderId : activeOrders.keySet()) {
            cancelOrder(orderId);
        }
        
        // 清理资源
        activeOrders.clear();
        
        log.info("执行处理器已停止，统计信息: {}", statistics);
    }
    
    @Override
    public String toString() {
        return String.format("ExecutionHandler[%s 活跃订单:%d]", 
                getName(), activeOrders.size());
    }
}