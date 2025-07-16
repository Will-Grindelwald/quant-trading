package com.quantcapital.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;

import java.util.List;

/**
 * QuantCapital框架配置类
 * 
 * 映射application.yml中的quantcapital配置节点。
 * 提供框架各模块的配置参数。
 * 
 * @author QuantCapital Team
 */
@Data
@Configuration
@ConfigurationProperties(prefix = "quantcapital")
public class QuantCapitalConfig {
    
    /** 运行模式：backtest, live */
    private String mode = "backtest";
    
    /** 数据配置 */
    private DataConfig data = new DataConfig();
    
    /** 事件引擎配置 */
    private EngineConfig engine = new EngineConfig();
    
    /** 账户配置 */
    private AccountConfig account = new AccountConfig();
    
    /** 组合风控配置 */
    private PortfolioConfig portfolio = new PortfolioConfig();
    
    /** 执行引擎配置 */
    private ExecutionConfig execution = new ExecutionConfig();
    
    /** 策略配置 */
    private StrategyConfig strategy = new StrategyConfig();
    
    /** 回测配置 */
    private BacktestConfig backtest = new BacktestConfig();
    
    /** 实盘配置 */
    private LiveConfig live = new LiveConfig();
    
    @Data
    public static class DataConfig {
        /** 数据根目录 */
        private String rootPath = "./.data";
        
        /** Parquet文件路径 */
        private String parquetPath = "./.data/kline";
        
        /** DuckDB连接URL */
        private String duckdbUrl = "jdbc:duckdb:";
        
        /** SQLite数据库路径 */
        private String sqlitePath = "./.data/business.db";
        
        /** 预加载历史数据天数 */
        private int preloadDays = 100;
    }
    
    @Data
    public static class EngineConfig {
        /** 事件队列容量 */
        private int queueCapacity = 10000;
        
        /** 工作线程数 */
        private int workerThreads = 4;
        
        /** 批处理大小 */
        private int batchSize = 100;
        
        /** 超时时间（毫秒） */
        private long timeoutMs = 5000;
    }
    
    @Data
    public static class AccountConfig {
        /** 初始资金 */
        private double initialCapital = 1000000.0;
        
        /** 账户ID */
        private String accountId = "default_account";
    }
    
    @Data
    public static class PortfolioConfig {
        /** 单个标的最大仓位比例 */
        private double maxPositionPercent = 5.0;
        
        /** 总仓位比例上限 */
        private double maxTotalPositionPercent = 95.0;
        
        /** 最小下单金额 */
        private double minOrderAmount = 1000.0;
        
        /** 仓位计算方法 */
        private String positionSizeMethod = "fixed_amount";
        
        /** 默认仓位大小 */
        private double defaultPositionSize = 10000.0;
        
        /** 风控参数 */
        private RiskConfig risk = new RiskConfig();
    }
    
    @Data
    public static class RiskConfig {
        /** 日最大亏损比例 */
        private double maxDailyLossPercent = 2.0;
        
        /** 最大回撤比例 */
        private double maxDrawdownPercent = 10.0;
        
        /** 最大相关性 */
        private double maxCorrelation = 0.7;
    }
    
    @Data
    public static class ExecutionConfig {
        /** 执行器类型 */
        private String type = "simulated";
        
        /** 滑点 */
        private double slippage = 0.001;
        
        /** 手续费率 */
        private double commissionRate = 0.0003;
        
        /** 执行延迟（毫秒） */
        private long delayMs = 1000;
        
        /** 最大重试次数 */
        private int maxRetryCount = 3;
        
        /** MiniQMT配置 */
        private MiniQMTConfig miniQMT = new MiniQMTConfig();
    }
    
    @Data
    public static class MiniQMTConfig {
        /** 服务器URL */
        private String serverUrl = "localhost:8001";
        
        /** 账户ID */
        private String accountId = "your_account";
        
        /** 超时时间（毫秒） */
        private long timeoutMs = 5000;
    }
    
    @Data
    public static class StrategyConfig {
        /** 最大策略数量 */
        private int maxStrategies = 100;
        
        /** 信号超时时间（秒） */
        private long signalTimeoutSeconds = 30;
    }
    
    @Data
    public static class BacktestConfig {
        /** 回测开始日期 */
        private String startDate = "2023-01-01";
        
        /** 回测结束日期 */
        private String endDate = "2023-12-31";
        
        /** 测试标的池 */
        private List<String> universe = List.of("000001.SZ", "000002.SZ", "399001.SZ");
        
        /** 回测频率 */
        private String frequency = "daily";
    }
    
    @Data
    public static class LiveConfig {
        /** 交易时间 */
        private TradingHours tradingHours = new TradingHours();
        
        /** 风控配置 */
        private LiveRisk risk = new LiveRisk();
    }
    
    @Data
    public static class TradingHours {
        /** 早盘开始时间 */
        private String morningStart = "09:30:00";
        
        /** 早盘结束时间 */
        private String morningEnd = "11:30:00";
        
        /** 午盘开始时间 */
        private String afternoonStart = "13:00:00";
        
        /** 午盘结束时间 */
        private String afternoonEnd = "15:00:00";
    }
    
    @Data
    public static class LiveRisk {
        /** 仓位检查间隔（秒） */
        private int positionCheckInterval = 60;
        
        /** 开启日内亏损检查 */
        private boolean dailyLossCheck = true;
    }
}