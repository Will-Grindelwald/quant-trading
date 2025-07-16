# QuantCapital 量化交易框架

一个模块化、事件驱动的Python量化交易框架，支持回测与实盘交易的一致性设计。

## 🎯 设计目标

- **模块化架构**: 各模块职责清晰，低耦合高内聚
- **事件驱动**: 通过事件总线实现模块间异步通信  
- **回测实盘一致性**: 策略代码不需要知道运行环境
- **高性能**: 针对A股市场优化的数据存储和访问

## 🏗️ 架构概览

```
┌─────────────────┐    ┌─────────────────┐    ┌─────────────────┐
│   数据更新进程   │    │    事件引擎     │    │   策略模块      │
│                │    │                │    │                │
│ • 定时数据获取   │◄──►│ • 事件分发     │◄──►│ • 信号生成      │
│ • 技术指标计算   │    │ • 异步处理     │    │ • 多策略协调    │
│ • 数据存储      │    │ • 故障隔离     │    │ • 动态标的池    │
└─────────────────┘    └─────────────────┘    └─────────────────┘
                              │
                              ▼
┌─────────────────┐    ┌─────────────────┐    ┌─────────────────┐
│   数据访问层     │    │  组合&风控模块   │    │   执行模块      │
│                │    │                │    │                │
│ • 统一数据接口   │◄──►│ • 信号处理     │◄──►│ • 订单执行      │
│ • 避免未来函数   │    │ • 仓位管理     │    │ • 成交回报      │
│ • 回测/实盘切换  │    │ • 风险控制     │    │ • 模拟/真实切换  │
└─────────────────┘    └─────────────────┘    └─────────────────┘
```

## 📦 核心模块

### 事件驱动引擎 (`quantcapital.engine`)
- **EventEngine**: 高性能事件分发器
- **EventHandler**: 事件处理器基类
- **Timer**: 定时器组件

### 数据模块 (`quantcapital.data`)
- **DataHandler**: 统一数据访问接口
- **DataUpdater**: 数据更新进程
- **Storage**: 多层存储架构（Parquet + DuckDB + SQLite）

### 执行模块 (`quantcapital.execution`) 
- **SimulatedExecutionHandler**: 回测模拟执行
- **LiveExecutionHandler**: 实盘执行（预留MiniQMT接口）

### 核心实体 (`quantcapital.entities`)
- **Event**: 事件体系（MarketEvent, SignalEvent, OrderEvent等）
- **Bar**: K线数据（含技术指标）
- **Order/Fill**: 订单和成交
- **Account**: 账户和持仓管理

## 🚀 快速开始

### 1. 安装依赖
```bash
# 克隆项目
git clone <your-repo-url>
cd quantcapital

# 安装依赖
pip install -r requirements.txt

# 运行测试确保安装正确
pytest tests/ -v
```

### 2. 快速体验（使用模拟数据）
```bash
# 立即体验框架功能，无需下载数据
python examples/quick_start.py
```

### 3. 真实数据回测
```bash
# 1. 下载真实历史数据（需要等待几分钟）
python examples/download_data.py

# 2. 查看下载的数据
python examples/check_data.py

# 3. 运行真实数据回测
python examples/run_backtest.py
```

### 4. 编写自己的策略
查看 **用户入门手册.md** 了解详细的策略开发指南

### 3. 基本使用

```python
from quantcapital import *

# 1. 初始化配置
config = ConfigManager(env='backtest')
setup_logging()

# 2. 创建事件引擎
event_engine = EventEngine()

# 3. 创建数据处理器
data_handler = BacktestDataHandler(
    data_root='./data',
    business_db_path='./data/business.db'
)

# 4. 创建执行器
execution_handler = SimulatedExecutionHandler(event_engine)

# 5. 启动框架
event_engine.start()
execution_handler.start()

# 6. 运行回测逻辑
# ... 您的策略代码 ...

# 7. 停止框架
execution_handler.stop()
event_engine.stop()
```

## 📊 数据架构

### 分层存储设计

```
数据层级:
├── Parquet 文件存储 (长期存档)
│   ├── frequency=daily/
│   ├── frequency=hourly/
│   └── frequency=weekly/
├── DuckDB 内存数据库 (快速查询)
└── SQLite 业务数据库 (股票池、日历等)
```

### 技术指标支持

- **移动平均**: MA5, MA20, MA60
- **MACD**: DIF, DEA, Histogram  
- **RSI**: 14日相对强弱指标
- **布林带**: 上轨、下轨、中轨

## 🔧 配置管理

支持环境隔离的配置管理：

```python
# 回测环境配置
config = ConfigManager(env='backtest')

# 实盘环境配置  
config = ConfigManager(env='live_trading')
```

主要配置项：
- `data_root`: 数据存储根目录
- `initial_capital`: 初始资金
- `execution.slippage`: 回测滑点设置
- `execution.commission_rate`: 手续费率

## 📈 开发路线图

### ✅ 第一阶段（已完成）
- [x] 核心实体定义
- [x] 事件驱动引擎
- [x] 数据存储和访问
- [x] 回测执行器

### 🚧 第二阶段（进行中）
- [ ] 策略模块
- [ ] 组合风控模块  
- [ ] 报告生成

### 📋 第三阶段（计划中）
- [ ] 完整回测流程
- [ ] 实盘接口对接
- [ ] 监控告警系统

## 🤝 贡献指南

欢迎提交 Issue 和 Pull Request！

## 📄 许可证

本项目采用 MIT 许可证。详见 [LICENSE](LICENSE) 文件。

---

**注意**: 本框架仅用于学习和研究目的，实盘交易需谨慎，风险自负。