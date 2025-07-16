package com.quantcapital.data;

import com.quantcapital.entities.Bar;
import com.quantcapital.entities.constant.Frequency;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

/**
 * 数据处理接口
 * 
 * 定义了从Python生成的多种数据格式读取数据的方法。
 * 支持Parquet、DuckDB、SQLite等格式。
 * 
 * @author QuantCapital Team
 */
public interface DataHandler {
    
    /**
     * 从Parquet文件读取K线数据
     * 
     * @param symbol 股票代码
     * @param startDate 开始日期
     * @param endDate 结束日期
     * @param frequency 数据频率
     * @return K线数据列表
     */
    List<Bar> readKlineData(String symbol, LocalDate startDate, LocalDate endDate, Frequency frequency);
    
    /**
     * 从DuckDB读取技术指标数据
     * 
     * @param symbol 股票代码
     * @param date 日期
     * @return 技术指标Map
     */
    Map<String, Double> readIndicators(String symbol, LocalDate date);
    
    /**
     * 批量读取技术指标数据
     * 
     * @param symbol 股票代码
     * @param startDate 开始日期
     * @param endDate 结束日期
     * @return 日期->指标Map的Map
     */
    Map<LocalDate, Map<String, Double>> readIndicatorsBatch(String symbol, LocalDate startDate, LocalDate endDate);
    
    /**
     * 从SQLite读取股票基本信息
     * 
     * @param symbol 股票代码
     * @return 股票信息Map
     */
    Map<String, Object> readStockInfo(String symbol);
    
    /**
     * 获取交易日历
     * 
     * @param startDate 开始日期
     * @param endDate 结束日期
     * @return 交易日列表
     */
    List<LocalDate> getTradingCalendar(LocalDate startDate, LocalDate endDate);
    
    /**
     * 检查是否为交易日
     * 
     * @param date 日期
     * @return 是否交易日
     */
    boolean isTradingDay(LocalDate date);
    
    /**
     * 获取所有股票代码列表
     * 
     * @return 股票代码列表
     */
    List<String> getAllSymbols();
    
    /**
     * 获取最新的实时行情（用于实盘）
     * 
     * @param symbol 股票代码
     * @return 最新Bar数据
     */
    Bar getLatestBar(String symbol);
    
    /**
     * 获取最新的实时行情（用于实盘，指定频率）
     * 
     * @param symbol 股票代码
     * @param frequency 数据频率
     * @return 最新Bar数据
     */
    Bar getLatestBar(String symbol, Frequency frequency);
    
    /**
     * 订阅实时行情
     * 
     * @param symbol 股票代码
     * @param listener 行情监听器
     */
    void subscribeMarketData(String symbol, MarketDataListener listener);
    
    /**
     * 取消订阅实时行情
     * 
     * @param symbol 股票代码
     */
    void unsubscribeMarketData(String symbol);
    
    /**
     * 获取复权因子
     * 
     * @param symbol 股票代码
     * @param date 日期
     * @return 复权因子
     */
    double getAdjustFactor(String symbol, LocalDate date);
    
    /**
     * 预加载数据到内存
     * 
     * @param symbols 股票代码列表
     * @param startDate 开始日期
     * @param endDate 结束日期
     */
    void preloadData(List<String> symbols, LocalDate startDate, LocalDate endDate);
    
    /**
     * 清理缓存
     */
    void clearCache();
    
    /**
     * 市场数据监听器接口
     */
    interface MarketDataListener {
        /**
         * 接收新的市场数据
         * 
         * @param bar K线数据
         */
        void onMarketData(Bar bar);
        
        /**
         * 数据错误回调
         * 
         * @param symbol 股票代码
         * @param error 错误信息
         */
        void onError(String symbol, String error);
    }
}