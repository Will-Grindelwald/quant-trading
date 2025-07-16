package com.quantcapital.entities;

import com.fasterxml.jackson.annotation.JsonFormat;
import com.quantcapital.entities.constant.OrderSide;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

/**
 * 账户实体
 * 
 * 管理交易账户的资金和持仓信息，提供盈亏计算和风控数据支撑。
 * 是整个交易系统的资金和仓位管理核心。
 * 
 * @author QuantCapital Team
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class Account {
    
    /** 账户ID */
    private String accountId;
    
    /** 初始资金 */
    private double initialCapital;
    
    /** 当前现金 */
    private double cash;
    
    /** 冻结资金 */
    @Builder.Default
    private double frozenCash = 0.0;
    
    /** 持仓信息（线程安全） */
    @Builder.Default
    private Map<String, Position> positions = new ConcurrentHashMap<>();
    
    // ==================== 交易记录 ====================
    
    /** 订单记录 */
    @Builder.Default
    private Map<String, Order> orders = new ConcurrentHashMap<>();
    
    /** 成交记录 */
    @Builder.Default
    private List<Fill> fills = Collections.synchronizedList(new ArrayList<>());
    
    /** 交易记录 */
    @Builder.Default
    private List<Trade> trades = Collections.synchronizedList(new ArrayList<>());
    
    // ==================== 统计信息 ====================
    
    /** 总手续费 */
    @Builder.Default
    private double totalCommission = 0.0;
    
    /** 总已实现盈亏 */
    @Builder.Default
    private double totalRealizedPnl = 0.0;
    
    /** 账户创建时间 */
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    @Builder.Default
    private LocalDateTime createTime = LocalDateTime.now();
    
    /** 最后更新时间 */
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    @Builder.Default
    private LocalDateTime lastUpdateTime = LocalDateTime.now();
    
    /** 最大回撤 */
    @Builder.Default
    private double maxDrawdown = 0.0;
    
    /** 最大资金使用率 */
    @Builder.Default
    private double maxCapitalUtilization = 0.0;
    
    /**
     * 构造函数
     * 
     * @param accountId 账户ID
     * @param initialCapital 初始资金
     */
    public Account(String accountId, double initialCapital) {
        this.accountId = accountId;
        this.initialCapital = initialCapital;
        this.cash = initialCapital;
        this.frozenCash = 0.0;
        this.positions = new ConcurrentHashMap<>();
        this.orders = new ConcurrentHashMap<>();
        this.fills = Collections.synchronizedList(new ArrayList<>());
        this.trades = Collections.synchronizedList(new ArrayList<>());
        this.totalCommission = 0.0;
        this.totalRealizedPnl = 0.0;
        this.createTime = LocalDateTime.now();
        this.lastUpdateTime = LocalDateTime.now();
        
        validate();
    }
    
    /**
     * 数据验证
     */
    private void validate() {
        if (accountId == null || accountId.trim().isEmpty()) {
            throw new IllegalArgumentException("账户ID不能为空");
        }
        if (initialCapital <= 0) {
            throw new IllegalArgumentException("初始资金必须大于0");
        }
        if (cash < 0) {
            throw new IllegalArgumentException("现金不能为负数");
        }
    }
    
    /**
     * 获取可用资金
     * 
     * @return 可用资金
     */
    public double getAvailableCash() {
        return Math.max(0, cash - frozenCash);
    }
    
    /**
     * 设置可用资金（主要用于账户状态更新）
     * 注意：这个方法直接设置现金余额，会影响冻结资金计算
     * 
     * @param availableCash 可用资金
     */
    public void setAvailableCash(double availableCash) {
        this.cash = availableCash + frozenCash;
        lastUpdateTime = LocalDateTime.now();
    }
    
    /**
     * 获取总资产（现金 + 持仓市值）
     * 注意：这个方法需要实时价格信息，在没有价格时使用成本价估算
     * 
     * @return 总资产
     */
    public double getTotalAssets() {
        return getTotalAssets(null);
    }
    
    /**
     * 获取总资产（现金 + 持仓市值）
     * 
     * @param currentPrices 当前价格信息
     * @return 总资产
     */
    public double getTotalAssets(Map<String, Double> currentPrices) {
        double totalValue = cash; // 现金部分
        
        // 加上所有持仓的市值
        for (Position position : positions.values()) {
            if (currentPrices != null) {
                Double currentPrice = currentPrices.get(position.getSymbol());
                if (currentPrice != null) {
                    totalValue += position.getCurrentMarketValue(currentPrice);
                } else {
                    // 如果没有当前价格，使用成本价估算
                    totalValue += position.getMarketValue();
                }
            } else {
                // 没有价格信息时使用成本价
                totalValue += position.getMarketValue();
            }
        }
        
        return totalValue;
    }
    
    /**
     * 设置总资产（用于账户状态更新）
     * 注意：这个方法只是更新缓存值，不会改变实际的现金和持仓
     * 
     * @param totalAssets 总资产
     */
    public void setTotalAssets(double totalAssets) {
        // 这里我们不直接改变现金或持仓，只是记录这个值用于统计
        // 实际的资产计算应该通过getTotalAssets()方法
        lastUpdateTime = LocalDateTime.now();
    }
    
    /**
     * 冻结资金
     * 
     * @param amount 冻结金额
     * @param orderId 订单ID
     * @return 是否冻结成功
     */
    public synchronized boolean freezeCash(double amount, String orderId) {
        if (amount <= 0) {
            return false;
        }
        if (getAvailableCash() < amount) {
            return false;
        }
        
        frozenCash += amount;
        lastUpdateTime = LocalDateTime.now();
        return true;
    }
    
    /**
     * 解冻资金
     * 
     * @param amount 解冻金额
     */
    public synchronized void unfreezeCash(double amount) {
        if (amount > 0) {
            frozenCash = Math.max(0, frozenCash - amount);
            lastUpdateTime = LocalDateTime.now();
        }
    }
    
    /**
     * 获取持仓信息
     * 
     * @param symbol 标的代码
     * @return 持仓信息，如果不存在返回null
     */
    public Position getPosition(String symbol) {
        return positions.get(symbol);
    }
    
    /**
     * 获取持仓数量
     * 
     * @param symbol 标的代码
     * @return 持仓数量
     */
    public int getPositionQuantity(String symbol) {
        Position position = getPosition(symbol);
        return position != null ? position.getQuantity() : 0;
    }
    
    /**
     * 检查是否有持仓
     * 
     * @param symbol 标的代码
     * @return 是否有持仓
     */
    public boolean hasPosition(String symbol) {
        Position position = getPosition(symbol);
        return position != null && !position.isEmpty();
    }
    
    /**
     * 根据成交更新持仓
     * 
     * @param fill 成交记录
     */
    public synchronized void updatePosition(Fill fill) {
        String symbol = fill.getSymbol();
        int quantityChange = fill.getSide() == OrderSide.BUY ? fill.getQuantity() : -fill.getQuantity();
        
        Position position = positions.get(symbol);
        if (position == null) {
            if (quantityChange != 0) {
                position = new Position(symbol, quantityChange, fill.getPrice(), fill.getStrategyId());
                positions.put(symbol, position);
            }
        } else {
            position.updatePosition(quantityChange, fill.getPrice());
            // 如果仓位清零，删除持仓记录
            if (position.isEmpty()) {
                positions.remove(symbol);
            }
        }
        
        // 更新现金
        updateCashFromFill(fill);
        
        // 记录成交
        fills.add(fill);
        totalCommission += fill.getTotalFee();
        
        // 更新统计信息
        lastUpdateTime = LocalDateTime.now();
        updateStatistics();
    }
    
    /**
     * 根据成交更新现金
     * 
     * @param fill 成交记录
     */
    private void updateCashFromFill(Fill fill) {
        // 买入时减少现金，卖出时增加现金
        double cashChange = fill.getNetAmount();
        cash += cashChange;
        
        // 解冻对应的资金
        if (fill.getSide() == OrderSide.BUY) {
            double frozenAmount = fill.getAmount() + fill.getTotalFee();
            unfreezeCash(frozenAmount);
        }
    }
    
    /**
     * 添加订单
     * 
     * @param order 订单
     */
    public void addOrder(Order order) {
        orders.put(order.getOrderId(), order);
        
        // 如果是买单，冻结资金
        if (order.isBuyOrder()) {
            double requiredAmount = order.getQuantity() * order.getPrice() * 1.01; // 预留1%的费用空间
            freezeCash(requiredAmount, order.getOrderId());
        }
        
        lastUpdateTime = LocalDateTime.now();
    }
    
    /**
     * 计算账户总市值
     * 
     * @param currentPrices 当前价格信息
     * @return 总市值
     */
    public double getTotalMarketValue(Map<String, Double> currentPrices) {
        double totalValue = cash; // 现金部分
        
        // 加上所有持仓的市值
        for (Position position : positions.values()) {
            Double currentPrice = currentPrices.get(position.getSymbol());
            if (currentPrice != null) {
                totalValue += position.getCurrentMarketValue(currentPrice);
            } else {
                // 如果没有当前价格，使用成本价估算
                totalValue += position.getMarketValue();
            }
        }
        
        return totalValue;
    }
    
    /**
     * 计算总未实现盈亏
     * 
     * @param currentPrices 当前价格信息
     * @return 总未实现盈亏
     */
    public double getTotalUnrealizedPnl(Map<String, Double> currentPrices) {
        double totalUnrealizedPnl = 0.0;
        
        for (Position position : positions.values()) {
            Double currentPrice = currentPrices.get(position.getSymbol());
            if (currentPrice != null) {
                totalUnrealizedPnl += position.getUnrealizedPnl(currentPrice);
            }
        }
        
        return totalUnrealizedPnl;
    }
    
    /**
     * 计算总盈亏
     * 
     * @param currentPrices 当前价格信息
     * @return 总盈亏（已实现 + 未实现）
     */
    public double getTotalPnl(Map<String, Double> currentPrices) {
        return totalRealizedPnl + getTotalUnrealizedPnl(currentPrices);
    }
    
    /**
     * 计算收益率
     * 
     * @param currentPrices 当前价格信息
     * @return 收益率（百分比）
     */
    public double getReturnRate(Map<String, Double> currentPrices) {
        if (initialCapital == 0) {
            return 0.0;
        }
        
        double currentTotalValue = getTotalMarketValue(currentPrices);
        return ((currentTotalValue - initialCapital) / initialCapital) * 100;
    }
    
    /**
     * 获取资金使用率
     * 
     * @param currentPrices 当前价格信息
     * @return 资金使用率（百分比）
     */
    public double getCapitalUtilization(Map<String, Double> currentPrices) {
        double totalPositionValue = 0.0;
        
        for (Position position : positions.values()) {
            Double currentPrice = currentPrices.get(position.getSymbol());
            if (currentPrice != null) {
                totalPositionValue += Math.abs(position.getCurrentMarketValue(currentPrice));
            } else {
                totalPositionValue += position.getMarketValue();
            }
        }
        
        if (initialCapital == 0) {
            return 0.0;
        }
        
        return (totalPositionValue / initialCapital) * 100;
    }
    
    /**
     * 获取账户统计信息
     * 
     * @param currentPrices 当前价格信息
     * @return 统计信息
     */
    public Map<String, Object> getAccountStatistics(Map<String, Double> currentPrices) {
        Map<String, Object> stats = new HashMap<>();
        
        stats.put("accountId", accountId);
        stats.put("initialCapital", initialCapital);
        stats.put("currentCash", cash);
        stats.put("frozenCash", frozenCash);
        stats.put("availableCash", getAvailableCash());
        
        double totalMarketValue = getTotalMarketValue(currentPrices);
        double totalUnrealizedPnl = getTotalUnrealizedPnl(currentPrices);
        double totalPnl = getTotalPnl(currentPrices);
        double returnRate = getReturnRate(currentPrices);
        double capitalUtilization = getCapitalUtilization(currentPrices);
        
        stats.put("totalMarketValue", totalMarketValue);
        stats.put("totalRealizedPnl", totalRealizedPnl);
        stats.put("totalUnrealizedPnl", totalUnrealizedPnl);
        stats.put("totalPnl", totalPnl);
        stats.put("returnRate", returnRate);
        stats.put("capitalUtilization", capitalUtilization);
        stats.put("maxDrawdown", maxDrawdown);
        stats.put("maxCapitalUtilization", maxCapitalUtilization);
        
        stats.put("positionCount", positions.size());
        stats.put("orderCount", orders.size());
        stats.put("fillCount", fills.size());
        stats.put("tradeCount", trades.size());
        stats.put("totalCommission", totalCommission);
        
        stats.put("createTime", createTime);
        stats.put("lastUpdateTime", lastUpdateTime);
        
        return stats;
    }
    
    /**
     * 获取持仓统计
     * 
     * @param currentPrices 当前价格信息
     * @return 持仓统计列表
     */
    public List<Map<String, Object>> getPositionStatistics(Map<String, Double> currentPrices) {
        return positions.values().stream()
                .map(position -> {
                    Map<String, Object> posStats = new HashMap<>();
                    posStats.put("symbol", position.getSymbol());
                    posStats.put("quantity", position.getQuantity());
                    posStats.put("avgPrice", position.getAvgPrice());
                    posStats.put("marketValue", position.getMarketValue());
                    posStats.put("direction", position.getPositionDirection());
                    posStats.put("strategyId", position.getStrategyId());
                    
                    Double currentPrice = currentPrices.get(position.getSymbol());
                    if (currentPrice != null) {
                        posStats.put("currentPrice", currentPrice);
                        posStats.put("currentMarketValue", position.getCurrentMarketValue(currentPrice));
                        posStats.put("unrealizedPnl", position.getUnrealizedPnl(currentPrice));
                        posStats.put("unrealizedPnlPct", position.getUnrealizedPnlPct(currentPrice) * 100);
                    }
                    
                    return posStats;
                })
                .collect(Collectors.toList());
    }
    
    /**
     * 更新统计信息
     */
    private void updateStatistics() {
        // 计算总已实现盈亏
        totalRealizedPnl = trades.stream()
                .filter(Trade::isClosed)
                .mapToDouble(Trade::getRealizedPnl)
                .sum();
        
        // 这里可以添加更多统计信息的计算，如最大回撤等
    }
    
    /**
     * 清除历史数据（保留基本账户信息）
     */
    public void clearHistory() {
        orders.clear();
        fills.clear();
        trades.clear();
        totalCommission = 0.0;
        totalRealizedPnl = 0.0;
        lastUpdateTime = LocalDateTime.now();
    }
    
    /**
     * 重置账户（恢复到初始状态）
     */
    public void reset() {
        cash = initialCapital;
        frozenCash = 0.0;
        positions.clear();
        clearHistory();
        maxDrawdown = 0.0;
        maxCapitalUtilization = 0.0;
        lastUpdateTime = LocalDateTime.now();
    }
    
    /**
     * 检查账户状态是否正常
     * 
     * @return 是否正常
     */
    public boolean isHealthy() {
        return cash >= 0 
                && frozenCash >= 0 
                && frozenCash <= cash
                && positions != null
                && orders != null
                && fills != null
                && trades != null;
    }
    
    @Override
    public String toString() {
        return String.format("Account[%s 资金:%.2f 可用:%.2f 持仓:%d个]",
                accountId, cash, getAvailableCash(), positions.size());
    }
}