package com.quantcapital.entities.event;

import com.quantcapital.entities.constant.EventType;
import com.quantcapital.entities.Signal;
import com.quantcapital.entities.constant.SignalDirection;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/**
 * 信号事件
 * 
 * 策略产生交易信号时触发的事件。
 * 组合风控模块监听此事件，进行信号处理和订单生成。
 * 
 * @author QuantCapital Team
 */
@Data
@EqualsAndHashCode(callSuper = true)
@NoArgsConstructor
public class SignalEvent extends Event {
    
    /** 交易信号 */
    private Signal signal;
    
    /** 触发策略的市场事件ID（可选，用于追溯） */
    private String triggerMarketEventId;
    
    /**
     * 构造函数
     * 
     * @param timestamp 事件时间
     * @param signal 交易信号
     */
    public SignalEvent(LocalDateTime timestamp, Signal signal) {
        super(EventType.SIGNAL, timestamp, signal.getSymbol());
        this.signal = signal;
        // 信号事件的优先级基于信号强度
        this.setPriority(calculatePriority(signal.getStrength()));
    }
    
    /**
     * 构造函数（带触发事件ID）
     * 
     * @param timestamp 事件时间
     * @param signal 交易信号
     * @param triggerMarketEventId 触发的市场事件ID
     */
    public SignalEvent(LocalDateTime timestamp, Signal signal, String triggerMarketEventId) {
        this(timestamp, signal);
        this.triggerMarketEventId = triggerMarketEventId;
    }
    
    @Override
    public String getDescription() {
        return String.format("策略信号: %s 来自策略%s %s", 
                signal.toString(), signal.getStrategyId(), signal.getReason());
    }
    
    /**
     * 获取信号ID
     * 
     * @return 信号ID
     */
    public String getSignalId() {
        return signal != null ? signal.getSignalId() : null;
    }
    
    /**
     * 获取策略ID
     * 
     * @return 策略ID
     */
    public String getStrategyId() {
        return signal != null ? signal.getStrategyId() : null;
    }
    
    /**
     * 获取信号方向
     * 
     * @return 信号方向
     */
    public SignalDirection getSignalDirection() {
        return signal != null ? signal.getDirection() : null;
    }
    
    /**
     * 获取信号强度
     * 
     * @return 信号强度
     */
    public double getSignalStrength() {
        return signal != null ? signal.getStrength() : 0.0;
    }
    
    /**
     * 获取参考价格
     * 
     * @return 参考价格
     */
    public double getReferencePrice() {
        return signal != null ? signal.getReferencePrice() : 0.0;
    }
    
    /**
     * 判断是否为买入信号
     * 
     * @return 是否为买入信号
     */
    public boolean isBuySignal() {
        return signal != null && signal.isBuySignal();
    }
    
    /**
     * 判断是否为卖出信号
     * 
     * @return 是否为卖出信号
     */
    public boolean isSellSignal() {
        return signal != null && signal.isSellSignal();
    }
    
    /**
     * 判断是否为持有信号
     * 
     * @return 是否为持有信号
     */
    public boolean isHoldSignal() {
        return signal != null && signal.isHoldSignal();
    }
    
    /**
     * 检查信号是否已过期
     * 
     * @param currentTime 当前时间
     * @return 是否过期
     */
    public boolean isSignalExpired(LocalDateTime currentTime) {
        return signal != null && signal.isExpired(currentTime);
    }
    
    /**
     * 根据信号强度计算事件优先级
     * 信号强度越高，优先级越高（数值越小）
     * 
     * @param strength 信号强度 (0.0-1.0)
     * @return 优先级 (1-10)
     */
    private int calculatePriority(double strength) {
        if (strength >= 0.9) {
            return 1; // 最高优先级
        } else if (strength >= 0.8) {
            return 2;
        } else if (strength >= 0.6) {
            return 3;
        } else if (strength >= 0.4) {
            return 5; // 中等优先级
        } else if (strength >= 0.2) {
            return 7;
        } else {
            return 9; // 低优先级
        }
    }
    
    /**
     * 验证事件数据的完整性
     * 
     * @return 事件数据是否有效
     */
    public boolean isValidEvent() {
        return signal != null && signal.isValid() && getTimestamp() != null;
    }
}