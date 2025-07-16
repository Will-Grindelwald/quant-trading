# 量化交易框架 Python → Java 混合架构迁移指导

## 项目概述
将现有的 Python 量化交易框架迁移为混合架构：**Python 负责数据获取与存储，Java 负责事件驱动引擎及整个回测/实盘交易流程**。这不是全重写，而是架构分层优化。
最终是一个混合语言代码的仓库，Python 和 Java 代码都有。

## 架构分工

### Python 职责范围 (保留并独立运行)
```
数据层 (独立进程)
├── AKShare 数据获取: A股/ETF/指数实时和历史数据
├── 技术指标计算: MA、MACD、RSI、布林带等
├── 数据清洗与验证: 数据质量检查、异常值处理
├── 存储管理: Parquet文件、DuckDB、SQLite业务库
└── 数据更新调度: 定时任务、增量更新
```

### Java 职责范围 (核心交易引擎)
```
SpringBoot 3 + JDK 21 + ZGC + Maven
├── 数据处理: Tablesaw (替代 Pandas)
├── 存储访问: Apache Parquet + DuckDB JDBC + SQLite JDBC  
├── 内存优化: Apache Arrow
├── JSON 处理: Jackson
├── 代码简化: Lombok
└── 并发模型: BlockingQueue + ThreadPool + 虚拟线程


├── 事件驱动引擎: EventEngine、EventHandler、Timer
├── 策略执行框架: BaseStrategy、StrategyManager
├── 组合风控系统: PortfolioRiskManager、Account
├── 交易执行引擎: ExecutionHandler (回测/实盘)
├── 回测引擎: BacktestEngine、MarketSimulator
├── 数据读取层: Tablesaw + Apache Parquet/DuckDB JDBC
├── 配置与监控: Spring Boot配置、日志、性能监控
└── API接口: RESTful API (可选，用于外部交互)
```

## Java 核心模块迁移任务

### 1. 事件驱动引擎 (`engine` 包) - 核心重点


### 2. 数据读取适配层 (`data` 包)


### 3. 交易实体重构 (`entities` 包)


### 4. 策略执行框架 (`strategy` 包)


### 5. 组合风控引擎 (`portfolio` 包)


### 6. 回测引擎 (`backtest` 包)


### 7. 执行引擎 (`execution` 包)


## 系统集成与通信

### Python-Java 数据交互
```yaml
# 数据流向：Python → 存储介质 → Java
Python数据服务:
  输出: Parquet文件 + DuckDB + SQLite
  位置: /.data/kline/, /.data/business.db
  
Java交易引擎:
  输入: 读取上述存储
  实时性: 通过文件监控或定时轮询获取最新数据
```

### 关键技术实现要点

#### 1. 事件系统设计



#### 3. 配置管理


#### 4. 性能优化策略
- **ZGC 低延迟**: `-XX:+UseZGC -XX:+UnlockExperimentalVMOptions`
- **虚拟线程**: 使用 `Executors.newVirtualThreadPerTaskExecutor()` 处理事件
- **内存映射文件**: 对于大文件使用 NIO 的 MappedByteBuffer
- **数据预加载**: 启动时预加载常用的历史数据到内存

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

## 部署运行模式

### 开发/测试环境
```bash
# 1. 启动 Python 数据服务（独立进程）
cd python-data-service && python data_updater.py

# 2. 启动 Java 交易引擎
cd java-trading-engine && mvn spring-boot:run -Dspring.profiles.active=backtest
```

### 生产环境
```bash
# Python 数据服务（后台运行）
nohup python data_updater.py > data_service.log 2>&1 &

# Java 交易引擎（实盘模式）
java -XX:+UseZGC -Xmx8g -jar trading-engine.jar --spring.profiles.active=live
```

## 验证要求

### 1. 架构验证
- **进程独立性**: Python 数据服务与 Java 交易引擎可独立启停
- **数据一致性**: Java 读取的 K线数据与 Python 生成的完全一致
- **事件完整性**: 事件驱动流程 `MarketEvent → SignalEvent → OrderEvent → FillEvent` 运行正常

### 2. 功能验证  
- **策略迁移**: 原 Python 均线交叉策略在 Java 中产生相同的交易信号
- **回测结果**: 相同数据、相同策略的回测结果误差 < 0.1%
- **风控有效**: 仓位限制、风险检查等规则正确执行

### 3. 性能验证
- **事件延迟**: 事件分发延迟 < 1ms (vs Python 的 10-50ms)
- **内存使用**: 稳定运行时内存占用，无内存泄漏
- **GC 停顿**: ZGC 停顿时间 < 10ms
- **并发能力**: 支持 > 1000 个策略并发运行

### 4. 集成验证
- **数据同步**: Python 更新数据后，Java 能在 1 秒内读取到最新数据
- **异常恢复**: 任一服务重启后能自动恢复正常工作
- **监控完整**: 日志、性能指标、异常告警正常工作

## 交付成果

### 代码交付
- **Java 项目**: 完整的 Spring Boot Maven 项目
- **配置文件**: application.yml (回测/实盘环境)
- **单元测试**: 核心模块测试覆盖率 > 80%
- **集成测试**: 端到端回测流程测试

### 文档交付
- **架构文档**: 混合架构设计和模块职责说明
- **API 文档**: Java 服务的接口文档
- **部署指南**: 开发和生产环境部署步骤
- **性能报告**: Java vs Python 性能对比分析

### 示例交付
- **回测示例**: 可运行的均线交叉策略回测
- **策略模板**: 新策略开发的脚手架代码
- **监控面板**: 交易和性能监控页面（可选）

**最终目标**: 保持 Python 数据处理优势，获得 Java 高性能和企业级特性，实现真正的混合架构优势互补。
