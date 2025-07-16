package com.quantcapital.execution;

import com.quantcapital.engine.EventEngine;
import com.quantcapital.entities.Order;
import com.quantcapital.entities.Bar;
import com.quantcapital.entities.constant.OrderType;
import com.quantcapital.entities.constant.OrderSide;
import lombok.extern.slf4j.Slf4j;

import java.time.LocalDateTime;
import java.util.Map;
import java.util.Random;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

/**
 * 模拟执行处理器
 * 
 * 用于回测环境的订单执行模拟，提供接近真实市场的执行行为。
 * 支持滑点、部分成交、延迟执行等市场特性的模拟。
 * 
 * @author QuantCapital Team
 */
@Slf4j
public class SimulatedExecutionHandler extends ExecutionHandler {
    
    /** 当前市场数据 */
    private final Map<String, Bar> currentMarketData;
    
    /** 执行配置 */
    private final SimulationConfig config;
    
    /** 随机数生成器（用于模拟不确定性） */
    private final Random random;
    
    /** 定时任务执行器（用于延迟执行） */
    private final ScheduledExecutorService scheduler;
    
    /**
     * 模拟配置类
     */
    public static class SimulationConfig {
        /** 基础滑点（比例） */
        public double baseSlippage = 0.0001; // 0.01%
        
        /** 最大滑点（比例） */
        public double maxSlippage = 0.002; // 0.2%
        
        /** 部分成交概率 */
        public double partialFillProbability = 0.1; // 10%
        
        /** 最小部分成交比例 */
        public double minPartialFillRatio = 0.3; // 30%
        
        /** 执行延迟范围（毫秒） */
        public int minExecutionDelayMs = 10;
        public int maxExecutionDelayMs = 100;
        
        /** 拒绝概率（极小） */
        public double rejectionProbability = 0.001; // 0.1%
        
        /** 是否启用延迟执行 */
        public boolean enableDelayedExecution = false;
        
        /** 是否启用滑点 */
        public boolean enableSlippage = true;
        
        /** 是否启用部分成交 */
        public boolean enablePartialFill = false;
    }
    
    /**
     * 构造函数
     * 
     * @param eventEngine 事件引擎
     */
    public SimulatedExecutionHandler(EventEngine eventEngine) {
        this(eventEngine, new SimulationConfig());
    }
    
    /**
     * 构造函数（带配置）
     * 
     * @param eventEngine 事件引擎
     * @param config 模拟配置
     */
    public SimulatedExecutionHandler(EventEngine eventEngine, SimulationConfig config) {
        super("SimulatedExecution", eventEngine);
        this.currentMarketData = new ConcurrentHashMap<>();
        this.config = config;
        this.random = new Random();
        this.scheduler = Executors.newScheduledThreadPool(2);
        
        log.info("模拟执行处理器初始化完成，配置: {}", config);
    }
    
    @Override
    protected void doExecuteOrder(Order order) {
        // 检查是否有当前市场数据
        Bar currentBar = currentMarketData.get(order.getSymbol());
        if (currentBar == null) {
            rejectOrder(order, "缺少市场数据: " + order.getSymbol());
            return;
        }
        
        // 模拟随机拒绝
        if (config.rejectionProbability > 0 && random.nextDouble() < config.rejectionProbability) {
            rejectOrder(order, "模拟市场拒绝");
            return;
        }
        
        // 决定是否延迟执行
        if (config.enableDelayedExecution) {
            int delay = config.minExecutionDelayMs + 
                       random.nextInt(config.maxExecutionDelayMs - config.minExecutionDelayMs + 1);
            
            scheduler.schedule(() -> executeOrderImmediately(order, currentBar), 
                             delay, TimeUnit.MILLISECONDS);
        } else {
            executeOrderImmediately(order, currentBar);
        }
    }
    
    /**
     * 立即执行订单
     * 
     * @param order 订单
     * @param marketBar 市场数据
     */
    private void executeOrderImmediately(Order order, Bar marketBar) {
        try {
            // 计算执行价格
            double executionPrice = calculateExecutionPrice(order, marketBar);
            
            // 决定成交数量
            int fillQuantity = calculateFillQuantity(order);
            
            // 处理成交
            processOrderFill(order, fillQuantity, executionPrice);
            
            log.debug("模拟执行完成: {} 价格: {} 数量: {}", 
                     order.getOrderId(), executionPrice, fillQuantity);
                     
        } catch (Exception e) {
            log.error("模拟执行失败: {}", order, e);
            rejectOrder(order, "执行异常: " + e.getMessage());
        }
    }
    
    /**
     * 计算执行价格（考虑滑点）
     * 
     * @param order 订单
     * @param marketBar 市场数据
     * @return 执行价格
     */
    private double calculateExecutionPrice(Order order, Bar marketBar) {
        double referencePrice;
        
        // 根据订单类型确定参考价格
        if (order.getOrderType() == OrderType.MARKET) {
            // 市价单使用当前价格
            referencePrice = order.getSide() == OrderSide.BUY ? 
                           marketBar.getHigh() : marketBar.getLow();
        } else {
            // 限价单检查是否能够成交
            referencePrice = order.getPrice();
            
            if (order.getSide() == OrderSide.BUY) {
                // 买入限价单：限价必须 >= 卖价
                if (referencePrice < marketBar.getLow()) {
                    throw new RuntimeException("买入限价低于市场价格");
                }
                referencePrice = Math.min(referencePrice, marketBar.getHigh());
            } else {
                // 卖出限价单：限价必须 <= 买价
                if (referencePrice > marketBar.getHigh()) {
                    throw new RuntimeException("卖出限价高于市场价格");
                }
                referencePrice = Math.max(referencePrice, marketBar.getLow());
            }
        }
        
        // 应用滑点
        if (config.enableSlippage) {
            double slippage = calculateSlippage(order, marketBar);
            if (order.getSide() == OrderSide.BUY) {
                referencePrice += referencePrice * slippage; // 买入时价格上滑
            } else {
                referencePrice -= referencePrice * slippage; // 卖出时价格下滑
            }
        }
        
        return Math.max(0.01, referencePrice); // 确保价格为正
    }
    
    /**
     * 计算滑点
     * 
     * @param order 订单
     * @param marketBar 市场数据
     * @return 滑点比例
     */
    private double calculateSlippage(Order order, Bar marketBar) {
        // 基础滑点
        double slippage = config.baseSlippage;
        
        // 根据订单大小调整滑点
        if (marketBar.getVolume() > 0) {
            double orderImpact = (double) order.getQuantity() / marketBar.getVolume();
            slippage += orderImpact * 0.001; // 每万分之一成交量增加0.1%滑点
        }
        
        // 添加随机成分
        slippage += random.nextGaussian() * config.baseSlippage * 0.5;
        
        // 限制在最大滑点范围内
        return Math.max(0, Math.min(slippage, config.maxSlippage));
    }
    
    /**
     * 计算成交数量
     * 
     * @param order 订单
     * @return 成交数量
     */
    private int calculateFillQuantity(Order order) {
        int remainingQuantity = order.getRemainingQuantity();
        
        // 是否部分成交
        if (config.enablePartialFill && random.nextDouble() < config.partialFillProbability) {
            // 部分成交：随机决定成交比例
            double fillRatio = config.minPartialFillRatio + 
                              random.nextDouble() * (1.0 - config.minPartialFillRatio);
            return Math.max(1, (int) (remainingQuantity * fillRatio));
        } else {
            // 全部成交
            return remainingQuantity;
        }
    }
    
    @Override
    protected boolean doCancelOrder(Order order) {
        // 模拟取消订单（总是成功）
        log.info("模拟取消订单: {}", order.getOrderId());
        return true;
    }
    
    /**
     * 更新市场数据
     * 
     * @param symbol 标的代码
     * @param bar K线数据
     */
    public void updateMarketData(String symbol, Bar bar) {
        currentMarketData.put(symbol, bar);
        log.debug("更新市场数据: {} -> {}", symbol, bar);
    }
    
    /**
     * 批量更新市场数据
     * 
     * @param marketData 市场数据
     */
    public void updateMarketData(Map<String, Bar> marketData) {
        this.currentMarketData.putAll(marketData);
        log.debug("批量更新市场数据: {} 个标的", marketData.size());
    }
    
    /**
     * 获取当前市场数据
     * 
     * @param symbol 标的代码
     * @return K线数据
     */
    public Bar getCurrentMarketData(String symbol) {
        return currentMarketData.get(symbol);
    }
    
    /**
     * 获取模拟配置
     * 
     * @return 模拟配置
     */
    public SimulationConfig getConfig() {
        return config;
    }
    
    /**
     * 设置滑点参数
     * 
     * @param baseSlippage 基础滑点
     * @param maxSlippage 最大滑点
     */
    public void setSlippageParams(double baseSlippage, double maxSlippage) {
        config.baseSlippage = baseSlippage;
        config.maxSlippage = maxSlippage;
        log.info("更新滑点参数: 基础滑点={}, 最大滑点={}", baseSlippage, maxSlippage);
    }
    
    /**
     * 启用/禁用部分成交
     * 
     * @param enabled 是否启用
     * @param probability 部分成交概率
     * @param minRatio 最小成交比例
     */
    public void setPartialFillConfig(boolean enabled, double probability, double minRatio) {
        config.enablePartialFill = enabled;
        config.partialFillProbability = probability;
        config.minPartialFillRatio = minRatio;
        log.info("更新部分成交配置: 启用={}, 概率={}, 最小比例={}", 
                enabled, probability, minRatio);
    }
    
    @Override
    public void stop() {
        super.stop();
        
        // 停止调度器
        scheduler.shutdown();
        try {
            if (!scheduler.awaitTermination(5, TimeUnit.SECONDS)) {
                scheduler.shutdownNow();
            }
        } catch (InterruptedException e) {
            scheduler.shutdownNow();
            Thread.currentThread().interrupt();
        }
        
        // 清理市场数据
        currentMarketData.clear();
        
        log.info("模拟执行处理器已停止");
    }
    
    @Override
    public String toString() {
        return String.format("SimulatedExecutionHandler[活跃订单:%d 市场数据:%d个标的]", 
                getActiveOrderCount(), currentMarketData.size());
    }
}