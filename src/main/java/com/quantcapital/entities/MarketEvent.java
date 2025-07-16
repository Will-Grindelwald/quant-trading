package com.quantcapital.entities;

import com.fasterxml.jackson.annotation.JsonFormat;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/**
 * 市场数据事件
 * 
 * 当市场数据更新时触发，包含最新的K线数据。
 * 是整个交易流程的起点，策略模块监听此事件进行信号计算。
 * 
 * @author QuantCapital Team
 */
@Data
@EqualsAndHashCode(callSuper = true)
@NoArgsConstructor
public class MarketEvent extends Event {
    
    /**
     * K线数据
     */
    private Bar bar;
    
    /**
     * 数据频率
     */
    private Frequency frequency;
    
    /**
     * 是否为实时数据
     */
    private boolean isLive;
    
    /**
     * 构造函数
     * 
     * @param timestamp 事件时间
     * @param bar K线数据
     * @param frequency 数据频率
     */
    public MarketEvent(LocalDateTime timestamp, Bar bar, Frequency frequency) {
        super(EventType.MARKET, timestamp, bar.getSymbol());
        this.bar = bar;
        this.frequency = frequency;
        this.isLive = false;
    }
    
    /**
     * 构造函数（指定是否实时）
     * 
     * @param timestamp 事件时间
     * @param bar K线数据
     * @param frequency 数据频率
     * @param isLive 是否为实时数据
     */
    public MarketEvent(LocalDateTime timestamp, Bar bar, Frequency frequency, boolean isLive) {
        this(timestamp, bar, frequency);
        this.isLive = isLive;
    }
    
    @Override
    public String getDescription() {
        return String.format("市场数据更新: %s %s价格%.2f 成交量%d", 
                bar.getSymbol(), frequency, bar.getClose(), bar.getVolume());
    }
    
    /**
     * 获取当前价格（收盘价）
     * 
     * @return 当前价格
     */
    public double getCurrentPrice() {
        return bar.getClose();
    }
    
    /**
     * 获取价格变化幅度
     * 
     * @return 价格变化幅度 (close - open) / open
     */
    public double getPriceChange() {
        if (bar.getOpen() <= 0) {
            return 0.0;
        }
        return (bar.getClose() - bar.getOpen()) / bar.getOpen();
    }
    
    /**
     * 判断是否为涨停
     * 
     * @return 是否涨停
     */
    public boolean isLimitUp() {
        return Math.abs(getPriceChange() - 0.10) < 0.001; // 10%涨停，考虑浮点精度
    }
    
    /**
     * 判断是否为跌停
     * 
     * @return 是否跌停
     */
    public boolean isLimitDown() {
        return Math.abs(getPriceChange() + 0.10) < 0.001; // 10%跌停，考虑浮点精度
    }
}