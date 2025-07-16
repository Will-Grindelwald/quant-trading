# 量化交易框架 Python → Java 重写指导

## 项目概述
将现有的 Python 量化交易框架重写为 Java 版本，采用混合架构：Python 负责数据获取，Java 负责核心业务逻辑。

## 技术栈映射

### 保留 Python 部分
- **数据获取**: 继续使用 AKShare 获取 A股/ETF/指数数据
- **数据预处理**: Python 完成技术指标计算和数据清洗
- **存储输出**: 写入 Parquet/DuckDB/SQLite

### Java 技术栈
```
SpringBoot 3 + JDK 21 + ZGC + Maven
├── 数据处理: Tablesaw (替代 Pandas)
├── 存储访问: Apache Parquet + DuckDB JDBC + SQLite JDBC  
├── 内存优化: Apache Arrow
├── JSON 处理: Jackson
├── 代码简化: Lombok
└── 并发模型: BlockingQueue + ThreadPool + 虚拟线程
```

## 核心重写任务

### 1. 实体类重写 (`entities` 包)
```java
// 示例：将 Python dataclass 转为 Java record/class
@Data @Builder
public class Bar {
    private String symbol;
    private LocalDateTime datetime;
    private Frequency frequency;
    private BigDecimal open, high, low, close;
    private Long volume;
    // 技术指标字段...
}
```

### 2. 事件驱动引擎 (`engine` 包)
- 将 Python `queue.Queue + threading` 转为 Java `BlockingQueue + CompletableFuture`
- 使用 JDK 21 虚拟线程替代传统线程池
- 保持异步事件分发和故障隔离特性

### 3. 数据访问层 (`data` 包)
```java
// 数据处理示例
Table bars = Table.read().csv("data.csv");
Table filtered = bars.where(bars.dateColumn("datetime").isAfter(startDate));
// 使用 Tablesaw 替代 Pandas 数据操作
```

### 4. 策略框架 (`strategy` 包)
- 保持抽象基类设计：`BaseStrategy extends EventHandler`
- 维持策略分类：开单策略、止损策略、通用止损
- 确保动态标的池管理逻辑一致

### 5. 组合风控 (`portfolio` 包)
- 信号处理、去重、冲突解决逻辑
- 仓位管理和资金分配算法
- 风险检查规则引擎

### 6. 执行系统 (`execution` 包)
- 模拟执行器：滑点、手续费、延迟模拟
- 实盘执行器框架：预留第三方接口

## 关键实现要求

### 数据处理转换
```java
// Python: df['ma20'] = df['close'].rolling(20).mean()
// Java (Tablesaw):
DoubleColumn ma20 = bars.doubleColumn("close").rolling(20).mean();
bars.addColumns(ma20.setName("ma20"));
```

### 配置管理
- 使用 Spring Boot `@ConfigurationProperties` 替代 Python 的 JSON 配置
- 支持多环境配置（回测/实盘）

### 事件系统
```java
// 保持事件类型一致
public sealed interface Event permits MarketEvent, SignalEvent, OrderEvent, FillEvent {
    String type();
    LocalDateTime timestamp();
    Map<String, Object> data();
}
```

### 性能优化点
- 使用 ZGC 确保低延迟垃圾回收
- 合理使用 Apache Arrow 进行列式数据处理
- 利用虚拟线程处理高并发事件

## 架构保持一致性

### 模块职责不变
```
DataHandler (抽象) → BacktestDataHandler / LiveDataHandler
ExecutionHandler (抽象) → SimulatedExecution / LiveExecution  
PortfolioRiskManager → 信号转订单、风控检查
StrategyManager → 策略协调和生命周期管理
```

### 回测/实盘一致性
- 策略代码无需区分运行环境
- 通过不同的 Handler 实现切换数据源和执行方式
- 保持相同的事件流：`MarketEvent → SignalEvent → OrderEvent → FillEvent`

## 验证要求

1. **功能验证**: 重写后的 Java 版本能运行相同的均线交叉策略
2. **性能验证**: 事件分发延迟、内存占用、GC 停顿时间
3. **数据一致性**: Java 读取的数据与 Python 处理的数据完全一致
4. **接口兼容**: 新增策略的开发体验与原 Python 版本类似

## 输出成果

- 完整的 Java Maven 项目结构
- 核心模块的单元测试
- 一个可运行的回测示例
- 性能对比报告
- 迁移文档和 API 说明

**目标**: 在保持原有架构设计优势的基础上，提供更高的性能、更好的类型安全性和企业级特性。 