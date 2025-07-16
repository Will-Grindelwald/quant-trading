package com.quantcapital.portfolio;

import com.quantcapital.config.QuantCapitalConfig;
import com.quantcapital.data.DataHandler;
import com.quantcapital.engine.EventEngine;
import com.quantcapital.engine.EventHandler;
import com.quantcapital.entities.*;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;

/**
 * 组合风控管理器
 * <p>
 * 负责：
 * 1. 监听信号事件，进行风控检查
 * 2. 将合规信号转换为订单
 * 3. 监听成交事件，更新持仓和账户
 * 4. 实时风险监控和仓位管理
 * 5. 强制止损和风控措施
 *
 * @author QuantCapital Team
 */
@Component
@Slf4j
public class PortfolioManager implements EventHandler {

    @Autowired
    private EventEngine eventEngine;

    @Autowired
    private DataHandler dataHandler;

    @Autowired
    private QuantCapitalConfig config;

    // 账户信息
    private Account account;

    // 持仓管理
    private final Map<String, Position> positions = new ConcurrentHashMap<>();

    // 风控状态
    private final Map<String, RiskStatus> symbolRiskStatus = new ConcurrentHashMap<>();

    // 统计信息
    private final AtomicLong totalSignals = new AtomicLong(0);
    private final AtomicLong passedSignals = new AtomicLong(0);
    private final AtomicLong rejectedSignals = new AtomicLong(0);
    private final AtomicLong generatedOrders = new AtomicLong(0);

    /**
     * 单标的风控状态
     */
    private static class RiskStatus {
        private volatile double dailyPnL = 0.0;
        private volatile double maxDrawdown = 0.0;
        private volatile LocalDateTime lastTradeTime;
        private volatile boolean isBlocked = false;
        private volatile String blockReason;

        public boolean isRiskExceeded(double maxDailyLoss, double maxDrawdownThreshold) {
            return dailyPnL < -maxDailyLoss || maxDrawdown > maxDrawdownThreshold;
        }
    }

    @Override
    public String getName() {
        return "PortfolioManager";
    }

    @Override
    public void initialize() {
        log.info("初始化组合风控管理器...");

        // 初始化账户
        initializeAccount();

        // 注册事件处理器
        eventEngine.registerHandler(EventType.SIGNAL.name(), this);
        eventEngine.registerHandler(EventType.FILL.name(), this);
        eventEngine.registerHandler(EventType.TIMER.name(), this);

        log.info("组合风控管理器初始化完成");
    }

    @Override
    public void destroy() {
        log.info("销毁组合风控管理器...");

        // 注销事件处理器
        eventEngine.unregisterHandler(EventType.SIGNAL.name(), this);
        eventEngine.unregisterHandler(EventType.FILL.name(), this);
        eventEngine.unregisterHandler(EventType.TIMER.name(), this);

        log.info("组合风控管理器已销毁");
    }

    @Override
    public void handleEvent(Event event) throws Exception {
        switch (event.getType()) {
            case SIGNAL -> handleSignalEvent((SignalEvent) event);
            case FILL -> handleFillEvent((FillEvent) event);
            case TIMER -> handleTimerEvent((TimerEvent) event);
            default -> log.debug("忽略未处理的事件类型: {}", event.getType());
        }
    }

    /**
     * 获取持仓信息
     *
     * @param symbol 标的代码
     * @return 持仓信息，如果没有持仓返回null
     */
    public Position getPosition(String symbol) {
        return positions.get(symbol);
    }

    /**
     * 获取所有持仓
     *
     * @return 持仓映射
     */
    public Map<String, Position> getAllPositions() {
        return new HashMap<>(positions);
    }

    /**
     * 获取账户信息
     *
     * @return 账户信息
     */
    public Account getAccount() {
        return account;
    }

    /**
     * 获取可用资金
     *
     * @return 可用资金
     */
    public double getAvailableCash() {
        return account.getAvailableCash();
    }

    /**
     * 获取总资产
     *
     * @return 总资产
     */
    public double getTotalAssets() {
        double totalValue = account.getCash();

        // 加上持仓市值
        for (Position position : positions.values()) {
            if (position.getQuantity() != 0) {
                Bar latestBar = dataHandler.getLatestBar(position.getSymbol(), Frequency.DAILY);
                if (latestBar != null) {
                    totalValue += position.getQuantity() * latestBar.getClose();
                }
            }
        }

        return totalValue;
    }

    /**
     * 获取总持仓比例
     *
     * @return 总持仓比例
     */
    public double getTotalPositionRatio() {
        double totalAssets = getTotalAssets();
        if (totalAssets <= 0) {
            return 0.0;
        }
        return (totalAssets - account.getCash()) / totalAssets;
    }

    /**
     * 获取统计信息
     *
     * @return 统计信息
     */
    public Map<String, Object> getStatistics() {
        Map<String, Object> stats = new HashMap<>();
        stats.put("totalSignals", totalSignals.get());
        stats.put("passedSignals", passedSignals.get());
        stats.put("rejectedSignals", rejectedSignals.get());
        stats.put("generatedOrders", generatedOrders.get());
        stats.put("signalPassRate", totalSignals.get() > 0 ? 
                (double) passedSignals.get() / totalSignals.get() : 0.0);

        // 账户统计
        stats.put("totalAssets", getTotalAssets());
        stats.put("availableCash", getAvailableCash());
        stats.put("totalPositionRatio", getTotalPositionRatio());
        stats.put("positionCount", positions.size());

        // 风控统计
        long blockedSymbols = symbolRiskStatus.values().stream()
                .mapToLong(status -> status.isBlocked ? 1 : 0)
                .sum();
        stats.put("blockedSymbols", blockedSymbols);

        return stats;
    }

    // ==================== 私有方法 ====================

    private void initializeAccount() {
        double initialCapital = config.getAccount().getInitialCapital();
        String accountId = config.getAccount().getAccountId();

        this.account = Account.builder()
                .accountId(accountId)
                .cash(initialCapital)
                .availableCash(initialCapital)
                .frozenCash(0.0)
                .totalAssets(initialCapital)
                .build();

        log.info("账户初始化完成: ID={}, 初始资金={}", accountId, initialCapital);
    }

    private void handleSignalEvent(SignalEvent signalEvent) {
        totalSignals.incrementAndGet();
        
        Signal signal = signalEvent.getSignal();
        if (signal == null || !signal.isValid()) {
            log.warn("无效信号，已忽略: {}", signal);
            rejectedSignals.incrementAndGet();
            return;
        }

        log.debug("接收到信号: {} {} {} 强度:{}", 
                signal.getSymbol(), signal.getDirection(), 
                signal.getReferencePrice(), signal.getStrength());

        // 风控检查
        if (!passRiskCheck(signal)) {
            rejectedSignals.incrementAndGet();
            return;
        }

        // 转换为订单
        Order order = convertSignalToOrder(signal);
        if (order != null) {
            publishOrderEvent(order, signalEvent.getSignalId());
            passedSignals.incrementAndGet();
            generatedOrders.incrementAndGet();
        } else {
            rejectedSignals.incrementAndGet();
        }
    }

    private void handleFillEvent(FillEvent fillEvent) {
        Fill fill = fillEvent.getFill();
        if (fill == null || !fill.isValid()) {
            log.warn("无效成交，已忽略: {}", fill);
            return;
        }

        log.debug("接收到成交: {} {} {}@{}", 
                fill.getSymbol(), fill.getSide(), fill.getQuantity(), fill.getPrice());

        // 更新持仓
        updatePosition(fill);

        // 更新账户
        updateAccount(fill);

        // 更新风控状态
        updateRiskStatus(fill);
    }

    private void handleTimerEvent(TimerEvent timerEvent) {
        if (timerEvent.getTimerType() == TimerType.RISK_CHECK) {
            performRiskCheck();
        }
    }

    private boolean passRiskCheck(Signal signal) {
        String symbol = signal.getSymbol();

        // 1. 检查标的是否被风控屏蔽
        RiskStatus riskStatus = symbolRiskStatus.get(symbol);
        if (riskStatus != null && riskStatus.isBlocked) {
            log.warn("标的被风控屏蔽，信号被拒绝: {} 原因: {}", symbol, riskStatus.blockReason);
            return false;
        }

        // 2. 检查信号有效期
        if (signal.isExpired(LocalDateTime.now())) {
            log.warn("信号已过期，被拒绝: {}", signal);
            return false;
        }

        // 3. 检查仓位限制
        if (!checkPositionLimits(signal)) {
            return false;
        }

        // 4. 检查资金限制
        if (!checkCashLimits(signal)) {
            return false;
        }

        // 5. 检查日内风控限制
        if (!checkDailyRiskLimits(signal)) {
            return false;
        }

        return true;
    }

    private boolean checkPositionLimits(Signal signal) {
        String symbol = signal.getSymbol();
        double maxPositionPercent = config.getPortfolio().getMaxPositionPercent() / 100.0;
        double maxTotalPositionPercent = config.getPortfolio().getMaxTotalPositionPercent() / 100.0;

        // 检查单标的仓位限制
        Position position = positions.get(symbol);
        if (position != null) {
            double totalAssets = getTotalAssets();
            double currentPositionValue = Math.abs(position.getQuantity() * signal.getReferencePrice());
            double currentPositionRatio = currentPositionValue / totalAssets;

            if (signal.isBuySignal() && currentPositionRatio >= maxPositionPercent) {
                log.warn("单标的仓位超限，信号被拒绝: {} 当前仓位比例: {:.2f}%, 限制: {:.2f}%", 
                        symbol, currentPositionRatio * 100, maxPositionPercent * 100);
                return false;
            }
        }

        // 检查总仓位限制
        if (signal.isBuySignal() && getTotalPositionRatio() >= maxTotalPositionPercent) {
            log.warn("总仓位超限，信号被拒绝: {} 当前总仓位比例: {:.2f}%, 限制: {:.2f}%", 
                    symbol, getTotalPositionRatio() * 100, maxTotalPositionPercent * 100);
            return false;
        }

        return true;
    }

    private boolean checkCashLimits(Signal signal) {
        if (!signal.isBuySignal()) {
            return true; // 卖出信号不需要检查资金
        }

        double minOrderAmount = config.getPortfolio().getMinOrderAmount();
        double orderAmount = calculateOrderAmount(signal);

        if (orderAmount < minOrderAmount) {
            log.warn("订单金额过小，信号被拒绝: {} 订单金额: {}, 最小金额: {}", 
                    signal.getSymbol(), orderAmount, minOrderAmount);
            return false;
        }

        if (orderAmount > getAvailableCash()) {
            log.warn("可用资金不足，信号被拒绝: {} 需要资金: {}, 可用资金: {}", 
                    signal.getSymbol(), orderAmount, getAvailableCash());
            return false;
        }

        return true;
    }

    private boolean checkDailyRiskLimits(Signal signal) {
        String symbol = signal.getSymbol();
        RiskStatus riskStatus = symbolRiskStatus.get(symbol);
        
        if (riskStatus != null) {
            double maxDailyLossPercent = config.getPortfolio().getRisk().getMaxDailyLossPercent() / 100.0;
            double maxDrawdownPercent = config.getPortfolio().getRisk().getMaxDrawdownPercent() / 100.0;
            double totalAssets = getTotalAssets();
            
            if (riskStatus.isRiskExceeded(totalAssets * maxDailyLossPercent, maxDrawdownPercent)) {
                log.warn("风控限制触发，信号被拒绝: {} 日内盈亏: {}, 最大回撤: {}", 
                        symbol, riskStatus.dailyPnL, riskStatus.maxDrawdown);
                return false;
            }
        }

        return true;
    }

    private double calculateOrderAmount(Signal signal) {
        // 使用配置的默认仓位大小或信号建议的仓位大小
        double positionSize = signal.getSuggestedPositionSize() != null ? 
                signal.getSuggestedPositionSize() : 
                config.getPortfolio().getDefaultPositionSize();
        
        return positionSize;
    }

    private Order convertSignalToOrder(Signal signal) {
        try {
            double orderAmount = calculateOrderAmount(signal);
            int quantity = (int) (orderAmount / signal.getReferencePrice() / 100) * 100; // 100股整数倍
            
            if (quantity <= 0) {
                log.warn("计算的订单数量为0，信号转换失败: {}", signal);
                return null;
            }

            OrderSide side = signal.isBuySignal() ? OrderSide.BUY : OrderSide.SELL;
            OrderType orderType = OrderType.LIMIT; // 默认使用限价单
            
            Order order = new Order(
                    signal.getSymbol(),
                    orderType,
                    side,
                    quantity,
                    signal.getReferencePrice(),
                    signal.getStrategyId()
            );
            
            order.setSignalId(signal.getSignalId());
            order.setTag("来自信号: " + signal.getReason());
            
            log.debug("信号转换为订单: {} {} {}@{}", 
                    order.getSymbol(), order.getSide(), order.getQuantity(), order.getPrice());
            
            return order;
            
        } catch (Exception e) {
            log.error("信号转换为订单失败: {}", signal, e);
            return null;
        }
    }

    private void publishOrderEvent(Order order, String signalId) {
        try {
            OrderEvent orderEvent = new OrderEvent(LocalDateTime.now(), order, OrderAction.NEW, signalId);
            eventEngine.publishEvent(orderEvent);
            
            log.debug("发布订单事件: {} {} {}@{}", 
                    order.getSymbol(), order.getSide(), order.getQuantity(), order.getPrice());
                    
        } catch (Exception e) {
            log.error("发布订单事件失败: order={}", order, e);
        }
    }

    private void updatePosition(Fill fill) {
        String symbol = fill.getSymbol();
        Position position = positions.computeIfAbsent(symbol, k -> 
                Position.builder().symbol(symbol).quantity(0).avgPrice(0.0).build());

        // 更新持仓
        int oldQuantity = position.getQuantity();
        double oldAvgPrice = position.getAvgPrice();
        
        if (fill.getSide() == OrderSide.BUY) {
            // 买入更新
            double totalValue = oldQuantity * oldAvgPrice + fill.getQuantity() * fill.getPrice();
            int newQuantity = oldQuantity + fill.getQuantity();
            double newAvgPrice = newQuantity > 0 ? totalValue / newQuantity : 0.0;
            
            position.setQuantity(newQuantity);
            position.setAvgPrice(newAvgPrice);
        } else {
            // 卖出更新
            position.setQuantity(oldQuantity - fill.getQuantity());
            // 卖出不改变平均成本
        }
        
        position.setLastUpdateTime(fill.getTimestamp());
        
        log.debug("持仓更新: {} 数量: {} -> {} 均价: {:.3f} -> {:.3f}", 
                symbol, oldQuantity, position.getQuantity(), oldAvgPrice, position.getAvgPrice());
    }

    private void updateAccount(Fill fill) {
        double cashChange = fill.getNetAmount(); // 净现金流影响
        
        account.setCash(account.getCash() + cashChange);
        account.setAvailableCash(account.getAvailableCash() + cashChange);
        account.setTotalAssets(getTotalAssets());
        
        log.debug("账户更新: 现金变化: {:.2f}, 可用现金: {:.2f}, 总资产: {:.2f}", 
                cashChange, account.getAvailableCash(), account.getTotalAssets());
    }

    private void updateRiskStatus(Fill fill) {
        String symbol = fill.getSymbol();
        RiskStatus riskStatus = symbolRiskStatus.computeIfAbsent(symbol, k -> new RiskStatus());
        
        // 更新盈亏
        double pnl = fill.getNetAmount(); // 简化的盈亏计算
        riskStatus.dailyPnL += pnl;
        riskStatus.lastTradeTime = fill.getTimestamp();
        
        log.debug("风控状态更新: {} 日内盈亏: {:.2f}", symbol, riskStatus.dailyPnL);
    }

    private void performRiskCheck() {
        // 定期风控检查
        log.debug("执行定期风控检查...");
        
        // 检查各标的风控状态
        for (Map.Entry<String, RiskStatus> entry : symbolRiskStatus.entrySet()) {
            String symbol = entry.getKey();
            RiskStatus riskStatus = entry.getValue();
            
            double maxDailyLossPercent = config.getPortfolio().getRisk().getMaxDailyLossPercent() / 100.0;
            double maxDrawdownPercent = config.getPortfolio().getRisk().getMaxDrawdownPercent() / 100.0;
            double totalAssets = getTotalAssets();
            
            if (riskStatus.isRiskExceeded(totalAssets * maxDailyLossPercent, maxDrawdownPercent)) {
                if (!riskStatus.isBlocked) {
                    riskStatus.isBlocked = true;
                    riskStatus.blockReason = "风控限制触发";
                    log.warn("标的触发风控限制，已屏蔽: {} 日内盈亏: {:.2f} 最大回撤: {:.2f}%", 
                            symbol, riskStatus.dailyPnL, riskStatus.maxDrawdown * 100);
                }
            }
        }
    }
}