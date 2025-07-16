package com.quantcapital.data;

import com.quantcapital.entities.Bar;
import com.quantcapital.entities.constant.Frequency;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import javax.annotation.PostConstruct;
import javax.annotation.PreDestroy;
import java.sql.Connection;
import java.sql.Date;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 基于Parquet的数据处理器实现
 * 
 * 读取Python生成的Parquet文件、DuckDB和SQLite数据。
 * 支持数据缓存和批量读取优化。
 * 
 * @author QuantCapital Team
 */
@Slf4j
@Service
public class ParquetDataHandler implements DataHandler {
    
    @Value("${quantcapital.data.parquet-path}")
    private String parquetPath;
    
    @Value("${quantcapital.data.duckdb-url}")
    private String duckdbUrl;
    
    @Value("${quantcapital.data.sqlite-path}")
    private String sqlitePath;
    
    @Value("${quantcapital.data.preload-days:100}")
    private int preloadDays;
    
    // 数据缓存
    private final Map<String, List<Bar>> klineCache = new ConcurrentHashMap<>();
    private final Map<String, Map<LocalDate, Map<String, Double>>> indicatorCache = new ConcurrentHashMap<>();
    private final Map<String, Map<String, Object>> stockInfoCache = new ConcurrentHashMap<>();
    private final Set<LocalDate> tradingDays = new HashSet<>();
    
    // 数据库连接
    private Connection duckdbConn;
    private Connection sqliteConn;
    
    // 实时行情监听器
    private final Map<String, List<MarketDataListener>> marketDataListeners = new ConcurrentHashMap<>();
    
    @PostConstruct
    public void init() {
        try {
            // 初始化DuckDB连接
            Class.forName("org.duckdb.DuckDBDriver");
            duckdbConn = DriverManager.getConnection(duckdbUrl);
            
            // 初始化SQLite连接
            Class.forName("org.sqlite.JDBC");
            sqliteConn = DriverManager.getConnection("jdbc:sqlite:" + sqlitePath);
            
            // 加载交易日历
            loadTradingCalendar();
            
            log.info("数据处理器初始化成功");
        } catch (Exception e) {
            log.error("数据处理器初始化失败", e);
            throw new RuntimeException("数据处理器初始化失败", e);
        }
    }
    
    @PreDestroy
    public void destroy() {
        try {
            if (duckdbConn != null && !duckdbConn.isClosed()) {
                duckdbConn.close();
            }
            if (sqliteConn != null && !sqliteConn.isClosed()) {
                sqliteConn.close();
            }
        } catch (SQLException e) {
            log.error("关闭数据库连接失败", e);
        }
    }
    
    @Override
    public List<Bar> readKlineData(String symbol, LocalDate startDate, LocalDate endDate, Frequency frequency) {
        // 检查缓存
        String cacheKey = buildKlineCacheKey(symbol, frequency);
        List<Bar> cachedBars = klineCache.get(cacheKey);
        
        if (cachedBars != null) {
            return filterBarsByDateRange(cachedBars, startDate, endDate);
        }
        
        // 从Parquet文件读取
        List<Bar> bars = readParquetFile(symbol, frequency);
        
        // 缓存数据
        if (!bars.isEmpty()) {
            klineCache.put(cacheKey, bars);
        }
        
        return filterBarsByDateRange(bars, startDate, endDate);
    }
    
    @Override
    public Map<String, Double> readIndicators(String symbol, LocalDate date) {
        Map<LocalDate, Map<String, Double>> indicatorMap = indicatorCache.get(symbol);
        
        if (indicatorMap != null && indicatorMap.containsKey(date)) {
            return indicatorMap.get(date);
        }
        
        // 从DuckDB读取
        try {
            String sql = "SELECT * FROM indicators WHERE symbol = ? AND date = ?";
            PreparedStatement stmt = duckdbConn.prepareStatement(sql);
            stmt.setString(1, symbol);
            stmt.setDate(2, Date.valueOf(date));
            
            ResultSet rs = stmt.executeQuery();
            if (rs.next()) {
                Map<String, Double> indicators = new HashMap<>();
                indicators.put("ma5", rs.getDouble("ma5"));
                indicators.put("ma10", rs.getDouble("ma10"));
                indicators.put("ma20", rs.getDouble("ma20"));
                indicators.put("ma60", rs.getDouble("ma60"));
                indicators.put("macd_dif", rs.getDouble("macd_dif"));
                indicators.put("macd_dea", rs.getDouble("macd_dea"));
                indicators.put("macd_histogram", rs.getDouble("macd_histogram"));
                indicators.put("rsi_14", rs.getDouble("rsi_14"));
                indicators.put("boll_upper", rs.getDouble("boll_upper"));
                indicators.put("boll_middle", rs.getDouble("boll_middle"));
                indicators.put("boll_lower", rs.getDouble("boll_lower"));
                
                // 缓存结果
                indicatorCache.computeIfAbsent(symbol, k -> new ConcurrentHashMap<>()).put(date, indicators);
                
                return indicators;
            }
        } catch (SQLException e) {
            log.error("读取技术指标失败: {}", symbol, e);
        }
        
        return new HashMap<>();
    }
    
    @Override
    public Map<LocalDate, Map<String, Double>> readIndicatorsBatch(String symbol, LocalDate startDate, LocalDate endDate) {
        Map<LocalDate, Map<String, Double>> result = new HashMap<>();
        
        try {
            String sql = "SELECT * FROM indicators WHERE symbol = ? AND date >= ? AND date <= ? ORDER BY date";
            PreparedStatement stmt = duckdbConn.prepareStatement(sql);
            stmt.setString(1, symbol);
            stmt.setDate(2, Date.valueOf(startDate));
            stmt.setDate(3, Date.valueOf(endDate));
            
            ResultSet rs = stmt.executeQuery();
            while (rs.next()) {
                LocalDate date = rs.getDate("date").toLocalDate();
                Map<String, Double> indicators = new HashMap<>();
                
                indicators.put("ma5", rs.getDouble("ma5"));
                indicators.put("ma10", rs.getDouble("ma10"));
                indicators.put("ma20", rs.getDouble("ma20"));
                indicators.put("ma60", rs.getDouble("ma60"));
                indicators.put("macd_dif", rs.getDouble("macd_dif"));
                indicators.put("macd_dea", rs.getDouble("macd_dea"));
                indicators.put("macd_histogram", rs.getDouble("macd_histogram"));
                indicators.put("rsi_14", rs.getDouble("rsi_14"));
                indicators.put("boll_upper", rs.getDouble("boll_upper"));
                indicators.put("boll_middle", rs.getDouble("boll_middle"));
                indicators.put("boll_lower", rs.getDouble("boll_lower"));
                
                result.put(date, indicators);
            }
            
            // 批量缓存
            Map<LocalDate, Map<String, Double>> cachedMap = 
                indicatorCache.computeIfAbsent(symbol, k -> new ConcurrentHashMap<>());
            cachedMap.putAll(result);
            
        } catch (SQLException e) {
            log.error("批量读取技术指标失败: {}", symbol, e);
        }
        
        return result;
    }
    
    @Override
    public Map<String, Object> readStockInfo(String symbol) {
        // 检查缓存
        if (stockInfoCache.containsKey(symbol)) {
            return stockInfoCache.get(symbol);
        }
        
        try {
            String sql = "SELECT * FROM stock_info WHERE symbol = ?";
            PreparedStatement stmt = sqliteConn.prepareStatement(sql);
            stmt.setString(1, symbol);
            
            ResultSet rs = stmt.executeQuery();
            if (rs.next()) {
                Map<String, Object> info = new HashMap<>();
                info.put("symbol", rs.getString("symbol"));
                info.put("name", rs.getString("name"));
                info.put("exchange", rs.getString("exchange"));
                info.put("industry", rs.getString("industry"));
                info.put("market_cap", rs.getDouble("market_cap"));
                info.put("circulating_cap", rs.getDouble("circulating_cap"));
                info.put("list_date", rs.getString("list_date"));
                info.put("update_time", rs.getString("update_time"));
                
                // 缓存结果
                stockInfoCache.put(symbol, info);
                
                return info;
            }
        } catch (SQLException e) {
            log.error("读取股票信息失败: {}", symbol, e);
        }
        
        return new HashMap<>();
    }
    
    @Override
    public List<LocalDate> getTradingCalendar(LocalDate startDate, LocalDate endDate) {
        List<LocalDate> result = new ArrayList<>();
        
        for (LocalDate date = startDate; !date.isAfter(endDate); date = date.plusDays(1)) {
            if (tradingDays.contains(date)) {
                result.add(date);
            }
        }
        
        return result;
    }
    
    @Override
    public boolean isTradingDay(LocalDate date) {
        return tradingDays.contains(date);
    }
    
    @Override
    public List<String> getAllSymbols() {
        List<String> symbols = new ArrayList<>();
        
        try {
            String sql = "SELECT DISTINCT symbol FROM stock_info ORDER BY symbol";
            Statement stmt = sqliteConn.createStatement();
            ResultSet rs = stmt.executeQuery(sql);
            
            while (rs.next()) {
                symbols.add(rs.getString("symbol"));
            }
        } catch (SQLException e) {
            log.error("获取股票列表失败", e);
        }
        
        return symbols;
    }
    
    @Override
    public Bar getLatestBar(String symbol) {
        // 获取最新的日线数据
        return getLatestBar(symbol, Frequency.DAILY);
    }
    
    @Override
    public Bar getLatestBar(String symbol, Frequency frequency) {
        try {
            // 实现获取最新Bar数据的逻辑
            // 这里暂时返回null，实际实现需要根据具体的数据存储结构
            log.warn("getLatestBar method not fully implemented for symbol: {}, frequency: {}", symbol, frequency);
            return null;
        } catch (Exception e) {
            log.error("获取最新Bar数据失败: symbol={}, frequency={}", symbol, frequency, e);
            return null;
        }
    }
    
    @Override
    public void subscribeMarketData(String symbol, MarketDataListener listener) {
        marketDataListeners.computeIfAbsent(symbol, k -> new ArrayList<>()).add(listener);
        log.info("订阅实时行情: {}", symbol);
    }
    
    @Override
    public void unsubscribeMarketData(String symbol) {
        marketDataListeners.remove(symbol);
        log.info("取消订阅实时行情: {}", symbol);
    }
    
    @Override
    public double getAdjustFactor(String symbol, LocalDate date) {
        // TODO: 实现复权因子读取
        return 1.0;
    }
    
    @Override
    public void preloadData(List<String> symbols, LocalDate startDate, LocalDate endDate) {
        log.info("预加载数据: {} 只股票, {} 到 {}", symbols.size(), startDate, endDate);
        
        for (String symbol : symbols) {
            // 预加载K线数据
            readKlineData(symbol, startDate, endDate, Frequency.DAILY);
            
            // 预加载技术指标
            readIndicatorsBatch(symbol, startDate, endDate);
            
            // 预加载股票信息
            readStockInfo(symbol);
        }
        
        log.info("数据预加载完成");
    }
    
    @Override
    public void clearCache() {
        klineCache.clear();
        indicatorCache.clear();
        stockInfoCache.clear();
        log.info("数据缓存已清理");
    }
    
    /**
     * 加载交易日历
     */
    private void loadTradingCalendar() throws SQLException {
        String sql = "SELECT date FROM trading_calendar WHERE is_trading = 1";
        Statement stmt = sqliteConn.createStatement();
        ResultSet rs = stmt.executeQuery(sql);
        
        while (rs.next()) {
            tradingDays.add(rs.getDate("date").toLocalDate());
        }
        
        log.info("加载交易日历完成，共 {} 个交易日", tradingDays.size());
    }
    
    /**
     * 从Parquet文件读取K线数据
     */
    private List<Bar> readParquetFile(String symbol, Frequency frequency) {
        List<Bar> bars = new ArrayList<>();
        
        try {
            // TODO: 实现Parquet文件读取
            // 使用Apache Parquet库读取文件
            String filePath = String.format("%s/%s.parquet", parquetPath, symbol);
            
            // 这里需要添加实际的Parquet读取逻辑
            log.warn("Parquet读取功能待实现: {}", filePath);
            
        } catch (Exception e) {
            log.error("读取Parquet文件失败: {}", symbol, e);
        }
        
        return bars;
    }
    
    /**
     * 构建K线缓存键
     */
    private String buildKlineCacheKey(String symbol, Frequency frequency) {
        return String.format("%s:%s", symbol, frequency);
    }
    
    /**
     * 按日期范围过滤K线数据
     */
    private List<Bar> filterBarsByDateRange(List<Bar> bars, LocalDate startDate, LocalDate endDate) {
        return bars.stream()
            .filter(bar -> {
                LocalDate barDate = bar.getDatetime().toLocalDate();
                return !barDate.isBefore(startDate) && !barDate.isAfter(endDate);
            })
            .toList();
    }
}