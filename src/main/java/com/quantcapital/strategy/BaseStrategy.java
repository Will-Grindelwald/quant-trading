package com.quantcapital.strategy;

import com.quantcapital.entities.*;
import com.quantcapital.engine.EventHandler;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

/**
 * 策略基类接口
 * 
 * 所有交易策略的基础接口，定义策略的标准行为。
 * 策略通过监听市场事件和成交事件来产生交易信号。
 * 
 * @author QuantCapital Team
 */
public interface BaseStrategy extends EventHandler {
    
    /**
     * 获取策略ID
     * 
     * @return 策略ID
     */
    String getStrategyId();
    
    /**
     * 获取策略类型
     * 
     * @return 策略类型
     */
    StrategyType getStrategyType();
    
    /**
     * 获取策略关注的标的列表
     * 根据策略类型和当前持仓情况动态返回
     * 
     * @return 关注的标的列表
     */
    List<String> getWatchSymbols();
    
    /**
     * 处理市场数据事件
     * 策略的核心逻辑，基于市场数据产生交易信号
     * 
     * @param marketEvent 市场数据事件
     * @return 产生的信号列表，可以为空
     */
    List<Signal> onMarketEvent(MarketEvent marketEvent);
    
    /**
     * 处理成交事件
     * 更新策略内部状态，如持仓跟踪等
     * 
     * @param fillEvent 成交事件
     */
    void onFillEvent(FillEvent fillEvent);
    
    /**
     * 处理定时器事件
     * 执行定时任务，如风控检查、信号清理等
     * 
     * @param timerEvent 定时器事件
     */
    void onTimerEvent(TimerEvent timerEvent);
    
    /**
     * 策略初始化
     * 在策略启动前调用，进行必要的初始化工作
     * 
     * @param config 策略配置参数
     * @throws Exception 初始化异常
     */
    void initialize(Map<String, Object> config) throws Exception;
    
    /**
     * 策略启动
     * 开始接收和处理事件
     */
    void start();
    
    /**
     * 策略停止
     * 停止接收事件，清理资源
     */
    void stop();
    
    /**
     * 获取策略当前状态
     * 
     * @return 策略状态
     */
    StrategyStatus getStatus();
    
    /**
     * 获取策略统计信息
     * 
     * @return 统计信息映射
     */
    Map<String, Object> getStatistics();
    
    /**
     * 获取策略配置参数
     * 
     * @return 配置参数映射
     */
    Map<String, Object> getConfig();
    
    /**
     * 更新策略配置
     * 热更新策略参数
     * 
     * @param config 新的配置参数
     */
    void updateConfig(Map<String, Object> config);
    
    /**
     * 获取策略最后活跃时间
     * 
     * @return 最后活跃时间
     */
    LocalDateTime getLastActiveTime();
    
    /**
     * 重置策略状态
     * 清除历史数据，重新开始
     */
    void reset();
    
    /**
     * 导出策略运行数据
     * 用于分析和复盘
     * 
     * @return 运行数据
     */
    Map<String, Object> exportData();
}

/**
 * 策略类型枚举
 */
enum StrategyType {
    /** 开单策略 - 寻找开仓机会 */
    ENTRY("开单策略"),
    
    /** 止盈止损策略 - 管理已有持仓 */
    EXIT("止盈止损策略"),
    
    /** 通用强制止损策略 - 兜底风控 */
    UNIVERSAL_STOP("通用强制止损");
    
    private final String description;
    
    StrategyType(String description) {
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

/**
 * 策略状态枚举
 */
enum StrategyStatus {
    /** 未初始化 */
    NOT_INITIALIZED("未初始化"),
    
    /** 已初始化 */
    INITIALIZED("已初始化"),
    
    /** 运行中 */
    RUNNING("运行中"),
    
    /** 已暂停 */
    PAUSED("已暂停"),
    
    /** 已停止 */
    STOPPED("已停止"),
    
    /** 错误状态 */
    ERROR("错误状态");
    
    private final String description;
    
    StrategyStatus(String description) {
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