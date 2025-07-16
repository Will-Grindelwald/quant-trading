package com.quantcapital.execution;

import com.quantcapital.engine.EventEngine;
import com.quantcapital.entities.Order;
import com.quantcapital.entities.Fill;
import com.quantcapital.entities.constant.EventType;
import com.quantcapital.entities.constant.OrderStatus;
import com.quantcapital.entities.event.Event;
import com.quantcapital.entities.event.OrderEvent;
import com.quantcapital.entities.event.FillEvent;
import lombok.extern.slf4j.Slf4j;

import java.time.LocalDateTime;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 订单执行处理器基类
 * 
 * 负责处理订单执行逻辑，将策略生成的订单转换为实际的交易指令。
 * 支持回测和实盘两种模式。
 * 
 * @author QuantCapital Team
 */
@Slf4j
public abstract class ExecutionHandler {
    
    /** 事件引擎 */
    protected final EventEngine eventEngine;
    
    /** 待执行订单缓存 */
    protected final Map<String, Order> pendingOrders = new ConcurrentHashMap<>();
    
    /** 执行统计 */
    protected final ExecutionStatistics statistics;
    
    /**
     * 构造函数
     * 
     * @param eventEngine 事件引擎
     */
    public ExecutionHandler(EventEngine eventEngine) {
        this.eventEngine = eventEngine;
        this.statistics = new ExecutionStatistics();
        
        // 注册事件监听
        eventEngine.register(EventType.ORDER, this::onOrderEvent);
        log.info("执行处理器初始化完成: {}", this.getClass().getSimpleName());
    }
    
    /**
     * 处理订单事件
     * 
     * @param event 事件
     */
    public void onOrderEvent(Event event) {
        if (event instanceof OrderEvent orderEvent) {
            Order order = orderEvent.getOrder();
            
            try {
                // 验证订单
                validateOrder(order);
                
                // 缓存订单
                pendingOrders.put(order.getOrderId(), order);
                
                // 执行订单
                executeOrder(order);
                
                log.info("订单执行请求已提交: {}", order.getOrderId());
                
            } catch (Exception e) {
                handleOrderError(order, e);
            }
        }
    }
    
    /**
     * 验证订单有效性
     * 
     * @param order 订单
     * @throws IllegalArgumentException 如果订单无效
     */
    protected void validateOrder(Order order) {
        if (order.getQuantity() <= 0) {
            throw new IllegalArgumentException("订单数量必须大于0");
        }
        
        if (order.getPrice() < 0) {
            throw new IllegalArgumentException("订单价格不能为负数");
        }
        
        log.debug("订单验证通过: {}", order.getOrderId());
    }
    
    /**
     * 执行订单（由子类实现具体逻辑）
     * 
     * @param order 订单
     * @throws Exception 执行异常
     */
    protected abstract void executeOrder(Order order) throws Exception;
    
    /**
     * 处理订单成交
     * 
     * @param order 原订单
     * @param fill 成交信息
     */
    protected void handleOrderFilled(Order order, Fill fill) {
        // 更新订单状态
        order.setStatus(OrderStatus.FILLED);
        order.setFilledQuantity(fill.getQuantity());
        order.setFilledPrice(fill.getPrice());
        order.setFilledTime(fill.getExecutionTime());
        
        // 从待执行缓存中移除
        pendingOrders.remove(order.getOrderId());
        
        log.info("订单已成交: {} - {} {} @ {}",
                order.getOrderId(), fill.getQuantity(), 
                order.getSymbol(), fill.getPrice());
        
        // 发送成交事件
        publishFillEvent(fill);
        
        // 更新统计
        statistics.recordFill(fill);
    }
    
    /**
     * 处理部分成交
     * 
     * @param order 原订单
     * @param fill 成交信息
     */
    protected void handlePartialFill(Order order, Fill fill) {
        // 更新部分成交状态
        order.setStatus(OrderStatus.PARTIALLY_FILLED);
        order.updatePartialFill(fill.getQuantity(), fill.getPrice());
        
        log.info("订单部分成交: {} - {} {} @ {}",
                order.getOrderId(), fill.getQuantity(),
                order.getSymbol(), fill.getPrice());
        
        // 发送成交事件
        publishFillEvent(fill);
        
        // 更新统计
        statistics.recordFill(fill);
    }
    
    /**
     * 处理订单拒绝
     * 
     * @param order 订单
     * @param reason 拒绝原因
     */
    protected void handleOrderRejected(Order order, String reason) {
        // 更新状态
        order.setStatus(OrderStatus.REJECTED);
        order.setRejectedReason(reason);
        
        // 从待执行缓存中移除
        pendingOrders.remove(order.getOrderId());
        
        log.warn("订单被拒绝: {} - {}", order.getOrderId(), reason);
        
        // 更新统计
        statistics.recordRejection();
    }
    
    /**
     * 处理订单取消
     * 
     * @param order 订单
     * @param reason 取消原因
     */
    protected void handleOrderCancelled(Order order, String reason) {
        // 更新状态
        order.setStatus(OrderStatus.CANCELLED);
        order.setCancelReason(reason);
        
        // 从待执行缓存中移除
        pendingOrders.remove(order.getOrderId());
        
        log.info("订单已取消: {} - {}", order.getOrderId(), reason);
        
        // 更新统计
        statistics.recordCancellation();
    }
    
    /**
     * 处理订单错误
     * 
     * @param order 订单
     * @param e 异常
     */
    protected void handleOrderError(Order order, Exception e) {
        log.error("订单执行错误: {} - {}", order.getOrderId(), e.getMessage(), e);
        
        // 标记订单失败
        order.setStatus(OrderStatus.FAILED);
        order.setRejectedReason(e.getMessage());
        
        // 更新统计
        statistics.recordError();
    }
    
    /**
     * 发布成交事件
     * 
     * @param fill 成交信息
     */
    protected void publishFillEvent(Fill fill) {
        FillEvent fillEvent = new FillEvent(LocalDateTime.now(), fill);
        eventEngine.put(fillEvent);
        
        log.debug("成交事件已发布: {}", fill.getFillId());
    }
    
    /**
     * 创建成交信息
     * 
     * @param order 订单
     * @param quantity 成交数量
     * @param price 成交价格
     * @return 成交信息
     */
    protected Fill createFill(Order order, int quantity, double price) {
        return Fill.builder()
                .fillId(UUID.randomUUID().toString())
                .orderId(order.getOrderId())
                .symbol(order.getSymbol())
                .side(order.getSide())
                .quantity(quantity)
                .price(price)
                .commission(calculateCommission(quantity, price))
                .strategyId(order.getStrategyId())
                .executionTime(LocalDateTime.now())
                .build();
    }
    
    /**
     * 计算手续费
     * 
     * @param quantity 数量
     * @param price 价格
     * @return 手续费
     */
    protected abstract double calculateCommission(int quantity, double price);
    
    /**
     * 取消订单
     * 
     * @param orderId 订单ID
     * @param reason 取消原因
     * @return 是否成功
     */
    public boolean cancelOrder(String orderId, String reason) {
        Order order = pendingOrders.get(orderId);
        if (order == null) {
            log.warn("订单不存在或已完成: {}", orderId);
            return false;
        }
        
        // 检查订单状态
        if (order.getStatus() != OrderStatus.PENDING && 
            order.getStatus() != OrderStatus.SUBMITTED) {
            log.warn("订单状态不允许取消: {} - {}", orderId, order.getStatus());
            return false;
        }
        
        try {
            // 执行取消操作（由子类实现）
            doCancelOrder(order);
            
            // 处理取消成功
            handleOrderCancelled(order, reason);
            
            return true;
        } catch (Exception e) {
            log.error("取消订单失败: {} - {}", orderId, e.getMessage(), e);
            return false;
        }
    }
    
    /**
     * 执行取消订单操作（由子类实现）
     * 
     * @param order 订单
     * @throws Exception 取消异常
     */
    protected abstract void doCancelOrder(Order order) throws Exception;
    
    /**
     * 启动执行处理器
     */
    public void start() {
        log.info("执行处理器启动: {}", this.getClass().getSimpleName());
        statistics.reset();
    }
    
    /**
     * 停止执行处理器
     */
    public void stop() {
        log.info("执行处理器停止");
        
        // 取消所有待执行订单
        pendingOrders.values().forEach(order -> {
            handleOrderCancelled(order, "执行处理器停止");
        });
        
        pendingOrders.clear();
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
     * 获取待执行订单数量
     * 
     * @return 订单数量
     */
    public int getPendingOrderCount() {
        return pendingOrders.size();
    }
}