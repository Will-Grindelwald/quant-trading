package com.quantcapital.entities;

import com.fasterxml.jackson.annotation.JsonFormat;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.UUID;

/**
 * 订单实体
 * 
 * 交易订单的数据结构，包含订单的所有属性和状态。
 * 支持限价单、市价单等不同类型的订单。
 * 
 * @author QuantCapital Team
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class Order {
    
    /** 订单ID，全局唯一 */
    private String orderId;
    
    /** 标的代码 */
    private String symbol;
    
    /** 订单类型 */
    private OrderType orderType;
    
    /** 买卖方向 */
    private OrderSide side;
    
    /** 订单数量（股） */
    private int quantity;
    
    /** 限价价格（限价单使用） */
    private double price;
    
    /** 订单状态 */
    private OrderStatus status;
    
    /** 创建时间 */
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private LocalDateTime createdTime;
    
    /** 提交时间（发送到交易所的时间） */
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private LocalDateTime submittedTime;
    
    /** 最后更新时间 */
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private LocalDateTime lastUpdateTime;
    
    /** 已成交数量 */
    private int filledQuantity;
    
    /** 剩余数量 */
    private int remainingQuantity;
    
    /** 平均成交价格 */
    private double avgFillPrice;
    
    /** 总成交金额 */
    private double totalFillAmount;
    
    /** 关联的信号ID（可选） */
    private String signalId;
    
    /** 策略ID */
    private String strategyId;
    
    /** 订单标签/备注 */
    private String tag;
    
    /** 订单有效期类型 */
    private TimeInForce timeInForce;
    
    /** 订单过期时间（GTT类型使用） */
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private LocalDateTime expireTime;
    
    /** 取消原因（如果订单被取消） */
    private String cancelReason;
    
    /**
     * 构造函数
     * 
     * @param symbol 标的代码
     * @param orderType 订单类型
     * @param side 买卖方向
     * @param quantity 数量
     * @param price 价格
     * @param strategyId 策略ID
     */
    public Order(String symbol, OrderType orderType, OrderSide side, 
                 int quantity, double price, String strategyId) {
        this.orderId = UUID.randomUUID().toString();
        this.symbol = symbol;
        this.orderType = orderType;
        this.side = side;
        this.quantity = quantity;
        this.price = price;
        this.strategyId = strategyId;
        this.status = OrderStatus.PENDING;
        this.createdTime = LocalDateTime.now();
        this.lastUpdateTime = LocalDateTime.now();
        this.filledQuantity = 0;
        this.remainingQuantity = quantity;
        this.avgFillPrice = 0.0;
        this.totalFillAmount = 0.0;
        this.timeInForce = TimeInForce.DAY; // 默认当日有效
    }
    
    /**
     * 更新订单状态
     * 
     * @param newStatus 新状态
     */
    public void updateStatus(OrderStatus newStatus) {
        this.status = newStatus;
        this.lastUpdateTime = LocalDateTime.now();
        
        if (newStatus == OrderStatus.SUBMITTED) {
            this.submittedTime = LocalDateTime.now();
        }
    }
    
    /**
     * 部分成交更新
     * 
     * @param fillQuantity 成交数量
     * @param fillPrice 成交价格
     */
    public void addFill(int fillQuantity, double fillPrice) {
        if (fillQuantity <= 0 || fillPrice <= 0) {
            return;
        }
        
        // 更新成交统计
        double fillAmount = fillQuantity * fillPrice;
        this.totalFillAmount += fillAmount;
        this.filledQuantity += fillQuantity;
        this.remainingQuantity = Math.max(0, this.quantity - this.filledQuantity);
        
        // 计算平均成交价格
        if (this.filledQuantity > 0) {
            this.avgFillPrice = this.totalFillAmount / this.filledQuantity;
        }
        
        // 更新订单状态
        if (this.remainingQuantity == 0) {
            updateStatus(OrderStatus.FILLED);
        } else if (this.filledQuantity > 0) {
            updateStatus(OrderStatus.PARTIALLY_FILLED);
        }
    }
    
    /**
     * 取消订单
     * 
     * @param reason 取消原因
     */
    public void cancel(String reason) {
        this.cancelReason = reason;
        updateStatus(OrderStatus.CANCELLED);
    }
    
    /**
     * 拒绝订单
     * 
     * @param reason 拒绝原因
     */
    public void reject(String reason) {
        this.cancelReason = reason;
        updateStatus(OrderStatus.REJECTED);
    }
    
    /**
     * 计算订单总价值
     * 
     * @return 订单总价值
     */
    public double getTotalValue() {
        return quantity * price;
    }
    
    /**
     * 计算剩余价值
     * 
     * @return 剩余价值
     */
    public double getRemainingValue() {
        return remainingQuantity * price;
    }
    
    /**
     * 获取成交比例
     * 
     * @return 成交比例 (0.0-1.0)
     */
    public double getFillRatio() {
        if (quantity <= 0) {
            return 0.0;
        }
        return (double) filledQuantity / quantity;
    }
    
    /**
     * 判断是否为买单
     * 
     * @return 是否为买单
     */
    public boolean isBuyOrder() {
        return side == OrderSide.BUY;
    }
    
    /**
     * 判断是否为卖单
     * 
     * @return 是否为卖单
     */
    public boolean isSellOrder() {
        return side == OrderSide.SELL;
    }
    
    /**
     * 判断订单是否有效
     * 
     * @param currentTime 当前时间
     * @return 是否有效
     */
    public boolean isValid(LocalDateTime currentTime) {
        // 检查基本数据有效性
        if (orderId == null || symbol == null || symbol.trim().isEmpty()
                || orderType == null || side == null
                || quantity <= 0 || price <= 0
                || status == null || createdTime == null) {
            return false;
        }
        
        // 检查订单是否过期
        if (timeInForce == TimeInForce.GTT && expireTime != null) {
            return !currentTime.isAfter(expireTime);
        }
        
        // 检查订单状态
        return status != OrderStatus.REJECTED && status != OrderStatus.CANCELLED;
    }
    
    /**
     * 判断订单是否已完成（不再变化）
     * 
     * @return 是否已完成
     */
    public boolean isFinished() {
        return status == OrderStatus.FILLED 
                || status == OrderStatus.CANCELLED 
                || status == OrderStatus.REJECTED;
    }
    
    /**
     * 判断订单是否可取消
     * 
     * @return 是否可取消
     */
    public boolean isCancellable() {
        return status == OrderStatus.PENDING 
                || status == OrderStatus.SUBMITTED 
                || status == OrderStatus.PARTIALLY_FILLED;
    }
    
    @Override
    public String toString() {
        return String.format("Order[%s %s %s %d@%.2f %s]",
                symbol, side, orderType, quantity, price, status);
    }
}

/**
 * 订单类型枚举
 */
enum OrderType {
    /** 限价单 */
    LIMIT("限价单"),
    
    /** 市价单 */
    MARKET("市价单"),
    
    /** 止损单 */
    STOP("止损单"),
    
    /** 止损限价单 */
    STOP_LIMIT("止损限价单");
    
    private final String description;
    
    OrderType(String description) {
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





