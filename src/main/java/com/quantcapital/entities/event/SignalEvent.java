package com.quantcapital.entities.event;

import com.quantcapital.entities.Signal;
import com.quantcapital.entities.constant.EventType;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/**
 * 信号事件
 * 
 * 当策略产生交易信号时触发，包含信号的详细信息。
 * 由策略模块生成，组合风控模块监听并转换为订单。
 * 
 * @author QuantCapital Team
 */
@Data
@EqualsAndHashCode(callSuper = true)
@NoArgsConstructor
public class SignalEvent extends Event {
    
    /**
     * 交易信号
     */
    private Signal signal;
    
    /**
     * 构造函数
     * 
     * @param timestamp 事件时间
     * @param signal 交易信号
     */
    public SignalEvent(LocalDateTime timestamp, Signal signal) {
        super(EventType.SIGNAL, timestamp, signal.getSymbol());
        this.signal = signal;
    }
    
    @Override
    public String getDescription() {
        return String.format("信号事件: %s %s 强度:%.2f 价格:%.2f 原因:%s", 
                signal.getSymbol(), signal.getDirection(), signal.getStrength(),
                signal.getReferencePrice(), signal.getReason());
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
    
    @Override
    public String toString() {
        return String.format("SignalEvent[%s %s]", 
                getEventId(), signal != null ? signal.toString() : "null");
    }
}