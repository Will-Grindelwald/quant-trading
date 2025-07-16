package com.quantcapital.entities.event;

import com.quantcapital.entities.constant.EventType;
import com.quantcapital.entities.constant.TimerType;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/**
 * 定时器事件
 * 
 * 系统定时触发的事件，用于执行定时任务，如数据更新、风控检查等。
 * 
 * @author QuantCapital Team
 */
@Data
@EqualsAndHashCode(callSuper = true)
@NoArgsConstructor
public class TimerEvent extends Event {
    
    /** 定时器类型 */
    private TimerType timerType;
    
    /** 触发间隔（毫秒） */
    private long intervalMs;
    
    /** 下次触发时间 */
    private LocalDateTime nextTriggerTime;
    
    /** 任务参数 */
    private Object taskParams;
    
    /**
     * 构造函数
     * 
     * @param timestamp 事件时间
     * @param timerType 定时器类型
     * @param intervalMs 触发间隔（毫秒）
     */
    public TimerEvent(LocalDateTime timestamp, TimerType timerType, long intervalMs) {
        super(EventType.TIMER, timestamp);
        this.timerType = timerType;
        this.intervalMs = intervalMs;
        this.nextTriggerTime = timestamp.plusNanos(intervalMs * 1_000_000);
        // 根据定时器类型设置优先级
        this.setPriority(calculatePriority(timerType));
    }
    
    /**
     * 构造函数（带任务参数）
     * 
     * @param timestamp 事件时间
     * @param timerType 定时器类型
     * @param intervalMs 触发间隔（毫秒）
     * @param taskParams 任务参数
     */
    public TimerEvent(LocalDateTime timestamp, TimerType timerType, long intervalMs, Object taskParams) {
        this(timestamp, timerType, intervalMs);
        this.taskParams = taskParams;
    }
    
    @Override
    public String getDescription() {
        return String.format("定时器事件: %s 间隔%dms", timerType.getDescription(), intervalMs);
    }
    
    /**
     * 判断是否到达触发时间
     * 
     * @param currentTime 当前时间
     * @return 是否到达触发时间
     */
    public boolean isTimeToTrigger(LocalDateTime currentTime) {
        return !currentTime.isBefore(nextTriggerTime);
    }
    
    /**
     * 更新下次触发时间
     */
    public void updateNextTriggerTime() {
        this.nextTriggerTime = this.nextTriggerTime.plusNanos(intervalMs * 1_000_000);
    }
    
    /**
     * 重置下次触发时间到指定时间
     * 
     * @param nextTime 下次触发时间
     */
    public void resetNextTriggerTime(LocalDateTime nextTime) {
        this.nextTriggerTime = nextTime;
    }
    
    /**
     * 获取距离下次触发的毫秒数
     * 
     * @param currentTime 当前时间
     * @return 距离下次触发的毫秒数
     */
    public long getMillisecondsToNextTrigger(LocalDateTime currentTime) {
        return java.time.Duration.between(currentTime, nextTriggerTime).toMillis();
    }
    
    /**
     * 根据定时器类型计算优先级
     * 
     * @param timerType 定时器类型
     * @return 优先级
     */
    private int calculatePriority(TimerType timerType) {
        return switch (timerType) {
            case MARKET_DATA_UPDATE -> 3;     // 市场数据更新优先级较高
            case RISK_CHECK -> 4;             // 风控检查中等优先级
            case HEARTBEAT -> 8;              // 心跳检测优先级较低
            case CLEANUP -> 9;                // 清理任务优先级最低
            case STRATEGY_TIMER -> 5;         // 策略定时器中等优先级
            case PORTFOLIO_REBALANCE -> 6;    // 组合再平衡中等偏低优先级
        };
    }
}
