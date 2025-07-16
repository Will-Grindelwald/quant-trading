package com.quantcapital.strategy;

import com.quantcapital.config.QuantCapitalConfig;
import com.quantcapital.engine.EventEngine;
import com.quantcapital.engine.EventHandler;
import com.quantcapital.entities.*;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;

/**
 * 策略管理器
 * <p>
 * 负责策略的生命周期管理：
 * 1. 策略注册和注销
 * 2. 策略状态管理
 * 3. 事件分发到策略
 * 4. 策略性能监控
 * 5. 策略配置热更新
 *
 * @author QuantCapital Team
 */
@Component
@Slf4j
public class StrategyManager implements EventHandler {

    @Autowired
    private EventEngine eventEngine;

    @Autowired
    private QuantCapitalConfig config;

    // 策略映射
    private final Map<String, BaseStrategy> strategies = new ConcurrentHashMap<>();
    private final Map<String, StrategyContext> strategyContexts = new ConcurrentHashMap<>();

    // 统计信息
    private final AtomicLong totalEvents = new AtomicLong(0);
    private final AtomicLong processedEvents = new AtomicLong(0);
    private final AtomicLong failedEvents = new AtomicLong(0);

    /**
     * 策略运行上下文
     */
    private static class StrategyContext {
        private final BaseStrategy strategy;
        private final Map<String, Object> config;
        private final LocalDateTime registeredTime;
        private volatile LocalDateTime lastActiveTime;
        private final AtomicLong receivedEvents;
        private final AtomicLong processedEvents;
        private final AtomicLong generatedSignals;
        private final AtomicLong errors;

        public StrategyContext(BaseStrategy strategy, Map<String, Object> config) {
            this.strategy = strategy;
            this.config = new HashMap<>(config);
            this.registeredTime = LocalDateTime.now();
            this.lastActiveTime = LocalDateTime.now();
            this.receivedEvents = new AtomicLong(0);
            this.processedEvents = new AtomicLong(0);
            this.generatedSignals = new AtomicLong(0);
            this.errors = new AtomicLong(0);
        }

        public void updateActivity() {
            this.lastActiveTime = LocalDateTime.now();
        }

        public Map<String, Object> getStatistics() {
            return Map.of(
                    "strategyId", strategy.getStrategyId(),
                    "strategyType", strategy.getStrategyType().toString(),
                    "status", strategy.getStatus().toString(),
                    "registeredTime", registeredTime,
                    "lastActiveTime", lastActiveTime,
                    "receivedEvents", receivedEvents.get(),
                    "processedEvents", processedEvents.get(),
                    "generatedSignals", generatedSignals.get(),
                    "errors", errors.get()
            );
        }
    }

    @Override
    public String getName() {
        return "StrategyManager";
    }

    @Override
    public void initialize() {
        log.info("初始化策略管理器...");
        
        // 注册为市场数据事件、成交事件和定时器事件的处理器
        eventEngine.registerHandler(EventType.MARKET.name(), this);
        eventEngine.registerHandler(EventType.FILL.name(), this);
        eventEngine.registerHandler(EventType.TIMER.name(), this);
        
        log.info("策略管理器初始化完成");
    }

    @Override
    public void destroy() {
        log.info("销毁策略管理器...");
        
        // 停止所有策略
        stopAllStrategies();
        
        // 注销事件处理器
        eventEngine.unregisterHandler(EventType.MARKET.name(), this);
        eventEngine.unregisterHandler(EventType.FILL.name(), this);
        eventEngine.unregisterHandler(EventType.TIMER.name(), this);
        
        log.info("策略管理器已销毁");
    }

    @Override
    public void handleEvent(Event event) throws Exception {
        totalEvents.incrementAndGet();

        try {
            switch (event.getType()) {
                case MARKET -> handleMarketEvent((MarketEvent) event);
                case FILL -> handleFillEvent((FillEvent) event);
                case TIMER -> handleTimerEvent((TimerEvent) event);
                default -> log.debug("忽略未处理的事件类型: {}", event.getType());
            }
            processedEvents.incrementAndGet();
        } catch (Exception e) {
            failedEvents.incrementAndGet();
            log.error("处理事件失败: {}", event, e);
            throw e;
        }
    }

    /**
     * 注册策略
     *
     * @param strategy 策略实例
     * @param config   策略配置
     */
    public void registerStrategy(BaseStrategy strategy, Map<String, Object> config) {
        if (strategy == null) {
            throw new IllegalArgumentException("策略不能为空");
        }

        String strategyId = strategy.getStrategyId();
        if (strategies.containsKey(strategyId)) {
            throw new IllegalArgumentException("策略ID已存在: " + strategyId);
        }

        // 检查策略数量限制
        if (strategies.size() >= this.config.getStrategy().getMaxStrategies()) {
            throw new IllegalStateException("策略数量已达上限: " + this.config.getStrategy().getMaxStrategies());
        }

        try {
            // 初始化策略
            strategy.initialize(config);
            
            // 创建策略上下文
            StrategyContext context = new StrategyContext(strategy, config);
            
            // 注册策略
            strategies.put(strategyId, strategy);
            strategyContexts.put(strategyId, context);
            
            log.info("策略注册成功: {} (类型: {})", strategyId, strategy.getStrategyType());
            
        } catch (Exception e) {
            log.error("策略注册失败: {}", strategyId, e);
            throw new RuntimeException("策略注册失败", e);
        }
    }

    /**
     * 注销策略
     *
     * @param strategyId 策略ID
     */
    public void unregisterStrategy(String strategyId) {
        BaseStrategy strategy = strategies.get(strategyId);
        if (strategy == null) {
            log.warn("策略不存在: {}", strategyId);
            return;
        }

        try {
            // 停止策略
            strategy.stop();
            
            // 移除策略
            strategies.remove(strategyId);
            strategyContexts.remove(strategyId);
            
            log.info("策略注销成功: {}", strategyId);
            
        } catch (Exception e) {
            log.error("策略注销失败: {}", strategyId, e);
        }
    }

    /**
     * 启动策略
     *
     * @param strategyId 策略ID
     */
    public void startStrategy(String strategyId) {
        BaseStrategy strategy = strategies.get(strategyId);
        if (strategy == null) {
            throw new IllegalArgumentException("策略不存在: " + strategyId);
        }

        try {
            strategy.start();
            log.info("策略启动成功: {}", strategyId);
        } catch (Exception e) {
            log.error("策略启动失败: {}", strategyId, e);
            throw new RuntimeException("策略启动失败", e);
        }
    }

    /**
     * 停止策略
     *
     * @param strategyId 策略ID
     */
    public void stopStrategy(String strategyId) {
        BaseStrategy strategy = strategies.get(strategyId);
        if (strategy == null) {
            log.warn("策略不存在: {}", strategyId);
            return;
        }

        try {
            strategy.stop();
            log.info("策略停止成功: {}", strategyId);
        } catch (Exception e) {
            log.error("策略停止失败: {}", strategyId, e);
        }
    }

    /**
     * 启动所有策略
     */
    public void startAllStrategies() {
        log.info("启动所有策略...");
        strategies.keySet().forEach(this::startStrategy);
        log.info("所有策略启动完成");
    }

    /**
     * 停止所有策略
     */
    public void stopAllStrategies() {
        log.info("停止所有策略...");
        strategies.keySet().forEach(this::stopStrategy);
        log.info("所有策略停止完成");
    }

    /**
     * 更新策略配置
     *
     * @param strategyId 策略ID
     * @param config     新配置
     */
    public void updateStrategyConfig(String strategyId, Map<String, Object> config) {
        BaseStrategy strategy = strategies.get(strategyId);
        StrategyContext context = strategyContexts.get(strategyId);
        
        if (strategy == null || context == null) {
            throw new IllegalArgumentException("策略不存在: " + strategyId);
        }

        try {
            strategy.updateConfig(config);
            context.config.putAll(config);
            context.updateActivity();
            
            log.info("策略配置更新成功: {}", strategyId);
        } catch (Exception e) {
            log.error("策略配置更新失败: {}", strategyId, e);
            throw new RuntimeException("策略配置更新失败", e);
        }
    }

    /**
     * 获取策略信息
     *
     * @param strategyId 策略ID
     * @return 策略信息
     */
    public Map<String, Object> getStrategyInfo(String strategyId) {
        BaseStrategy strategy = strategies.get(strategyId);
        StrategyContext context = strategyContexts.get(strategyId);
        
        if (strategy == null || context == null) {
            return null;
        }

        Map<String, Object> info = new HashMap<>(context.getStatistics());
        info.putAll(strategy.getStatistics());
        info.put("config", context.config);
        info.put("watchSymbols", strategy.getWatchSymbols());
        
        return info;
    }

    /**
     * 获取所有策略信息
     *
     * @return 策略信息列表
     */
    public List<Map<String, Object>> getAllStrategiesInfo() {
        return strategies.keySet().stream()
                .map(this::getStrategyInfo)
                .filter(Objects::nonNull)
                .toList();
    }

    /**
     * 获取运行中的策略数量
     *
     * @return 运行中的策略数量
     */
    public long getRunningStrategyCount() {
        return strategies.values().stream()
                .filter(strategy -> strategy.getStatus() == StrategyStatus.RUNNING)
                .count();
    }

    /**
     * 获取管理器统计信息
     *
     * @return 统计信息
     */
    public Map<String, Object> getStatistics() {
        Map<String, Object> stats = new HashMap<>();
        stats.put("totalStrategies", strategies.size());
        stats.put("runningStrategies", getRunningStrategyCount());
        stats.put("totalEvents", totalEvents.get());
        stats.put("processedEvents", processedEvents.get());
        stats.put("failedEvents", failedEvents.get());
        stats.put("successRate", processedEvents.get() > 0 ? 
                (double) processedEvents.get() / totalEvents.get() : 0.0);
        
        // 按策略类型统计
        Map<String, Long> typeStats = new HashMap<>();
        strategies.values().forEach(strategy -> {
            String type = strategy.getStrategyType().toString();
            typeStats.merge(type, 1L, Long::sum);
        });
        stats.put("strategiesByType", typeStats);
        
        return stats;
    }

    // ==================== 私有方法 ====================

    private void handleMarketEvent(MarketEvent marketEvent) {
        String symbol = marketEvent.getSymbol();
        
        // 分发给关注该标的的策略
        for (Map.Entry<String, BaseStrategy> entry : strategies.entrySet()) {
            String strategyId = entry.getKey();
            BaseStrategy strategy = entry.getValue();
            StrategyContext context = strategyContexts.get(strategyId);
            
            if (context == null || strategy.getStatus() != StrategyStatus.RUNNING) {
                continue;
            }
            
            // 检查策略是否关注该标的
            if (!strategy.getWatchSymbols().contains(symbol)) {
                continue;
            }
            
            try {
                context.receivedEvents.incrementAndGet();
                
                // 调用策略处理市场事件
                List<Signal> signals = strategy.onMarketEvent(marketEvent);
                
                // 发布生成的信号
                if (signals != null && !signals.isEmpty()) {
                    for (Signal signal : signals) {
                        publishSignalEvent(signal, marketEvent.getEventId());
                        context.generatedSignals.incrementAndGet();
                    }
                }
                
                context.processedEvents.incrementAndGet();
                context.updateActivity();
                
            } catch (Exception e) {
                context.errors.incrementAndGet();
                log.error("策略处理市场事件失败: strategyId={}, symbol={}", strategyId, symbol, e);
            }
        }
    }

    private void handleFillEvent(FillEvent fillEvent) {
        String strategyId = fillEvent.getStrategyId();
        if (strategyId == null) {
            return;
        }
        
        BaseStrategy strategy = strategies.get(strategyId);
        StrategyContext context = strategyContexts.get(strategyId);
        
        if (strategy == null || context == null) {
            return;
        }
        
        try {
            context.receivedEvents.incrementAndGet();
            strategy.onFillEvent(fillEvent);
            context.processedEvents.incrementAndGet();
            context.updateActivity();
            
        } catch (Exception e) {
            context.errors.incrementAndGet();
            log.error("策略处理成交事件失败: strategyId={}", strategyId, e);
        }
    }

    private void handleTimerEvent(TimerEvent timerEvent) {
        // 分发给所有运行中的策略
        for (Map.Entry<String, BaseStrategy> entry : strategies.entrySet()) {
            String strategyId = entry.getKey();
            BaseStrategy strategy = entry.getValue();
            StrategyContext context = strategyContexts.get(strategyId);
            
            if (context == null || strategy.getStatus() != StrategyStatus.RUNNING) {
                continue;
            }
            
            try {
                context.receivedEvents.incrementAndGet();
                strategy.onTimerEvent(timerEvent);
                context.processedEvents.incrementAndGet();
                context.updateActivity();
                
            } catch (Exception e) {
                context.errors.incrementAndGet();
                log.error("策略处理定时器事件失败: strategyId={}", strategyId, e);
            }
        }
    }

    private void publishSignalEvent(Signal signal, String triggerMarketEventId) {
        try {
            SignalEvent signalEvent = new SignalEvent(LocalDateTime.now(), signal, triggerMarketEventId);
            eventEngine.publishEvent(signalEvent);
            
            log.debug("发布信号事件: strategyId={}, symbol={}, direction={}", 
                    signal.getStrategyId(), signal.getSymbol(), signal.getDirection());
                    
        } catch (Exception e) {
            log.error("发布信号事件失败: signal={}", signal, e);
        }
    }
}