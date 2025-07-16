package com.quantcapital.entities;

import com.fasterxml.jackson.annotation.JsonFormat;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.*;
import java.util.stream.Collectors;

/**
 * 股票池实体
 * 
 * 管理股票池的组成和更新，支持动态股票池管理。
 * 提供股票的添加、删除、查询等功能，是选股策略的基础组件。
 * 
 * @author QuantCapital Team
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class Universe {
    
    /** 股票池名称 */
    private String name;
    
    /** 股票代码集合 */
    @Builder.Default
    private Set<String> symbols = new HashSet<>();
    
    /** 更新时间 */
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    @Builder.Default
    private LocalDateTime updateTime = LocalDateTime.now();
    
    /** 创建时间 */
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    @Builder.Default
    private LocalDateTime createTime = LocalDateTime.now();
    
    /** 股票池描述 */
    private String description;
    
    /** 股票池类型 */
    private UniverseType type;
    
    /** 最大股票数量限制 */
    private Integer maxSize;
    
    /** 股票筛选条件 */
    private Map<String, Object> filters;
    
    /** 是否启用 */
    @Builder.Default
    private boolean enabled = true;
    
    /**
     * 股票池类型枚举
     */
    public enum UniverseType {
        /** 静态股票池 */
        STATIC("静态"),
        /** 动态股票池 */
        DYNAMIC("动态"),
        /** 指数成分股 */
        INDEX("指数"),
        /** 行业股票池 */
        SECTOR("行业"),
        /** 自定义股票池 */
        CUSTOM("自定义");
        
        private final String description;
        
        UniverseType(String description) {
            this.description = description;
        }
        
        public String getDescription() {
            return description;
        }
    }
    
    /**
     * 构造函数
     * 
     * @param name 股票池名称
     */
    public Universe(String name) {
        this.name = name;
        this.symbols = new HashSet<>();
        this.updateTime = LocalDateTime.now();
        this.createTime = LocalDateTime.now();
        this.enabled = true;
    }
    
    /**
     * 构造函数（带初始股票列表）
     * 
     * @param name 股票池名称
     * @param initialSymbols 初始股票列表
     */
    public Universe(String name, Collection<String> initialSymbols) {
        this(name);
        if (initialSymbols != null) {
            this.symbols.addAll(initialSymbols);
        }
    }
    
    /**
     * 添加股票
     * 
     * @param symbol 股票代码
     * @return 是否添加成功
     */
    public boolean addSymbol(String symbol) {
        if (symbol == null || symbol.trim().isEmpty()) {
            return false;
        }
        
        String cleanSymbol = symbol.trim().toUpperCase();
        
        // 检查最大数量限制
        if (maxSize != null && symbols.size() >= maxSize && !symbols.contains(cleanSymbol)) {
            return false;
        }
        
        boolean added = symbols.add(cleanSymbol);
        if (added) {
            updateTime = LocalDateTime.now();
        }
        return added;
    }
    
    /**
     * 批量添加股票
     * 
     * @param newSymbols 新股票代码列表
     * @return 实际添加的股票数量
     */
    public int addSymbols(Collection<String> newSymbols) {
        if (newSymbols == null || newSymbols.isEmpty()) {
            return 0;
        }
        
        int addedCount = 0;
        for (String symbol : newSymbols) {
            if (addSymbol(symbol)) {
                addedCount++;
            }
        }
        return addedCount;
    }
    
    /**
     * 移除股票
     * 
     * @param symbol 股票代码
     * @return 是否移除成功
     */
    public boolean removeSymbol(String symbol) {
        if (symbol == null || symbol.trim().isEmpty()) {
            return false;
        }
        
        String cleanSymbol = symbol.trim().toUpperCase();
        boolean removed = symbols.remove(cleanSymbol);
        if (removed) {
            updateTime = LocalDateTime.now();
        }
        return removed;
    }
    
    /**
     * 批量移除股票
     * 
     * @param symbolsToRemove 要移除的股票代码列表
     * @return 实际移除的股票数量
     */
    public int removeSymbols(Collection<String> symbolsToRemove) {
        if (symbolsToRemove == null || symbolsToRemove.isEmpty()) {
            return 0;
        }
        
        int removedCount = 0;
        for (String symbol : symbolsToRemove) {
            if (removeSymbol(symbol)) {
                removedCount++;
            }
        }
        return removedCount;
    }
    
    /**
     * 检查是否包含股票
     * 
     * @param symbol 股票代码
     * @return 是否包含
     */
    public boolean contains(String symbol) {
        if (symbol == null || symbol.trim().isEmpty()) {
            return false;
        }
        return symbols.contains(symbol.trim().toUpperCase());
    }
    
    /**
     * 获取股票列表（有序）
     * 
     * @return 股票代码列表
     */
    public List<String> getSymbolsList() {
        return symbols.stream()
                .sorted()
                .collect(Collectors.toList());
    }
    
    /**
     * 获取股票集合（副本）
     * 
     * @return 股票代码集合
     */
    public Set<String> getSymbolsSet() {
        return new HashSet<>(symbols);
    }
    
    /**
     * 股票池大小
     * 
     * @return 股票数量
     */
    public int size() {
        return symbols.size();
    }
    
    /**
     * 是否为空
     * 
     * @return 是否为空
     */
    public boolean isEmpty() {
        return symbols.isEmpty();
    }
    
    /**
     * 批量更新股票池
     * 
     * @param newSymbols 新的股票列表
     */
    public void updateSymbols(Collection<String> newSymbols) {
        this.symbols.clear();
        if (newSymbols != null) {
            addSymbols(newSymbols);
        }
        this.updateTime = LocalDateTime.now();
    }
    
    /**
     * 清空股票池
     */
    public void clear() {
        this.symbols.clear();
        this.updateTime = LocalDateTime.now();
    }
    
    /**
     * 获取随机股票样本
     * 
     * @param sampleSize 样本大小
     * @return 随机股票列表
     */
    public List<String> getRandomSample(int sampleSize) {
        if (sampleSize <= 0 || symbols.isEmpty()) {
            return new ArrayList<>();
        }
        
        List<String> symbolsList = new ArrayList<>(symbols);
        Collections.shuffle(symbolsList);
        
        int actualSize = Math.min(sampleSize, symbolsList.size());
        return symbolsList.subList(0, actualSize);
    }
    
    /**
     * 获取股票池统计信息
     * 
     * @return 统计信息
     */
    public Map<String, Object> getStatistics() {
        Map<String, Object> stats = new HashMap<>();
        stats.put("name", name);
        stats.put("type", type != null ? type.getDescription() : "未设置");
        stats.put("size", size());
        stats.put("maxSize", maxSize);
        stats.put("enabled", enabled);
        stats.put("createTime", createTime);
        stats.put("updateTime", updateTime);
        
        // 按首字母统计
        Map<String, Long> prefixCount = symbols.stream()
                .collect(Collectors.groupingBy(
                        symbol -> symbol.substring(0, 1),
                        Collectors.counting()
                ));
        stats.put("prefixDistribution", prefixCount);
        
        return stats;
    }
    
    /**
     * 与另一个股票池求交集
     * 
     * @param other 另一个股票池
     * @return 交集股票池
     */
    public Universe intersect(Universe other) {
        if (other == null) {
            return new Universe(this.name + "_empty");
        }
        
        Set<String> intersection = new HashSet<>(this.symbols);
        intersection.retainAll(other.symbols);
        
        Universe result = new Universe(this.name + "_intersect_" + other.name);
        result.symbols = intersection;
        return result;
    }
    
    /**
     * 与另一个股票池求并集
     * 
     * @param other 另一个股票池
     * @return 并集股票池
     */
    public Universe union(Universe other) {
        if (other == null) {
            return this.copy();
        }
        
        Set<String> union = new HashSet<>(this.symbols);
        union.addAll(other.symbols);
        
        Universe result = new Universe(this.name + "_union_" + other.name);
        result.symbols = union;
        return result;
    }
    
    /**
     * 与另一个股票池求差集
     * 
     * @param other 另一个股票池
     * @return 差集股票池（当前池中有但other中没有的）
     */
    public Universe difference(Universe other) {
        if (other == null) {
            return this.copy();
        }
        
        Set<String> difference = new HashSet<>(this.symbols);
        difference.removeAll(other.symbols);
        
        Universe result = new Universe(this.name + "_diff_" + other.name);
        result.symbols = difference;
        return result;
    }
    
    /**
     * 复制股票池
     * 
     * @return 股票池副本
     */
    public Universe copy() {
        Universe copy = new Universe(this.name + "_copy");
        copy.symbols = new HashSet<>(this.symbols);
        copy.type = this.type;
        copy.description = this.description;
        copy.maxSize = this.maxSize;
        copy.enabled = this.enabled;
        copy.filters = this.filters != null ? new HashMap<>(this.filters) : null;
        return copy;
    }
    
    /**
     * 验证股票池配置
     * 
     * @return 是否有效
     */
    public boolean isValid() {
        return name != null && !name.trim().isEmpty()
                && symbols != null
                && (maxSize == null || maxSize > 0)
                && (maxSize == null || symbols.size() <= maxSize);
    }
    
    @Override
    public String toString() {
        return String.format("Universe[%s: %d支股票, 类型:%s, 启用:%s]",
                name, size(),
                type != null ? type.getDescription() : "未设置",
                enabled ? "是" : "否");
    }
}