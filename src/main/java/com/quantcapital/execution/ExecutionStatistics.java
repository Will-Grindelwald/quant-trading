package com.quantcapital.execution;

import lombok.Data;
import java.time.LocalDateTime;
import java.util.concurrent.atomic.AtomicLong;

/**
 * 执行统计信息
 * 
 * 记录和统计执行器的运行指标，提供性能监控和分析数据。
 * 
 * @author QuantCapital Team
 */
@Data
public class ExecutionStatistics {
    
    /** 订单总数 */
    private final AtomicLong orderCount = new AtomicLong(0);
    
    /** 成交总数 */
    private final AtomicLong fillCount = new AtomicLong(0);
    
    /** 取消总数 */
    private final AtomicLong cancelCount = new AtomicLong(0);
    
    /** 拒绝总数 */
    private final AtomicLong rejectCount = new AtomicLong(0);
    
    /** 开始时间 */
    private final LocalDateTime startTime = LocalDateTime.now();
    
    /** 最后更新时间 */
    private LocalDateTime lastUpdateTime = LocalDateTime.now();
    
    /**
     * 增加订单计数
     */
    public void incrementOrderCount() {
        orderCount.incrementAndGet();
        updateTimestamp();
    }
    
    /**
     * 增加成交计数
     */
    public void incrementFillCount() {
        fillCount.incrementAndGet();
        updateTimestamp();
    }
    
    /**
     * 增加取消计数
     */
    public void incrementCancelCount() {
        cancelCount.incrementAndGet();
        updateTimestamp();
    }
    
    /**
     * 增加拒绝计数
     */
    public void incrementRejectCount() {
        rejectCount.incrementAndGet();
        updateTimestamp();
    }
    
    /**
     * 记录成交
     * 
     * @param fill 成交信息
     */
    public void recordFill(com.quantcapital.entities.Fill fill) {
        incrementFillCount();
    }
    
    /**
     * 记录拒绝
     */
    public void recordRejection() {
        incrementRejectCount();
    }
    
    /**
     * 记录取消
     */
    public void recordCancellation() {
        incrementCancelCount();
    }
    
    /**
     * 记录错误
     */
    public void recordError() {
        incrementRejectCount(); // 错误也归类为拒绝
    }
    
    /**
     * 获取成交率
     * 
     * @return 成交率（百分比）
     */
    public double getFillRate() {
        long total = orderCount.get();
        return total > 0 ? (fillCount.get() * 100.0 / total) : 0.0;
    }
    
    /**
     * 获取取消率
     * 
     * @return 取消率（百分比）
     */
    public double getCancelRate() {
        long total = orderCount.get();
        return total > 0 ? (cancelCount.get() * 100.0 / total) : 0.0;
    }
    
    /**
     * 获取拒绝率
     * 
     * @return 拒绝率（百分比）
     */
    public double getRejectRate() {
        long total = orderCount.get();
        return total > 0 ? (rejectCount.get() * 100.0 / total) : 0.0;
    }
    
    /**
     * 重置统计信息
     */
    public void reset() {
        orderCount.set(0);
        fillCount.set(0);
        cancelCount.set(0);
        rejectCount.set(0);
        lastUpdateTime = LocalDateTime.now();
    }
    
    private void updateTimestamp() {
        lastUpdateTime = LocalDateTime.now();
    }
    
    @Override
    public String toString() {
        return String.format("ExecutionStatistics[订单:%d 成交:%d 取消:%d 拒绝:%d 成交率:%.2f%%]",
                orderCount.get(), fillCount.get(), cancelCount.get(), rejectCount.get(), getFillRate());
    }
}