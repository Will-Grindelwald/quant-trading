"""
回测报告生成器

生成专业的回测分析报告，包括：
- 业绩指标统计
- 收益曲线图表
- 回撤分析
- 交易记录分析
- 持仓分布分析
"""

import json
import logging
from datetime import datetime
from pathlib import Path
from typing import Dict, List, Optional, Union

import matplotlib.pyplot as plt
import numpy as np
import pandas as pd
import seaborn as sns
from matplotlib.dates import DateFormatter

logger = logging.getLogger(__name__)


class BacktestReport:
    """回测报告生成器"""
    
    def __init__(self, output_dir: str = "./reports"):
        """
        初始化报告生成器
        
        Args:
            output_dir: 报告输出目录
        """
        self.output_dir = Path(output_dir)
        self.output_dir.mkdir(parents=True, exist_ok=True)
        
        # 设置中文字体
        plt.rcParams['font.sans-serif'] = ['SimHei']
        plt.rcParams['axes.unicode_minus'] = False
        
        # 设置风格
        sns.set_style("whitegrid")
        
    def generate_report(self, 
                       backtest_result: Dict,
                       strategy_name: str = "未命名策略") -> str:
        """
        生成回测报告
        
        Args:
            backtest_result: 回测结果数据
            strategy_name: 策略名称
            
        Returns:
            报告文件路径
        """
        try:
            # 创建报告目录
            timestamp = datetime.now().strftime("%Y%m%d_%H%M%S")
            report_dir = self.output_dir / f"{strategy_name}_{timestamp}"
            report_dir.mkdir(parents=True, exist_ok=True)
            
            # 解析回测数据
            summary = backtest_result.get('summary', {})
            equity_curve = backtest_result.get('equity_curve', pd.DataFrame())
            trades = backtest_result.get('trades', pd.DataFrame())
            positions = backtest_result.get('positions', pd.DataFrame())
            
            # 生成各部分报告
            self._generate_summary_report(summary, report_dir)
            self._plot_equity_curve(equity_curve, report_dir)
            self._plot_drawdown(equity_curve, report_dir)
            self._analyze_trades(trades, report_dir)
            self._analyze_positions(positions, report_dir)
            
            # 生成HTML总报告
            html_path = self._generate_html_report(
                strategy_name, summary, report_dir
            )
            
            logger.info(f"回测报告生成成功: {html_path}")
            return str(html_path)
            
        except Exception as e:
            logger.error(f"生成回测报告失败: {e}")
            raise
            
    def _generate_summary_report(self, summary: Dict, report_dir: Path):
        """生成业绩摘要"""
        # 保存JSON格式
        with open(report_dir / "summary.json", 'w', encoding='utf-8') as f:
            json.dump(summary, f, ensure_ascii=False, indent=2)
            
        # 生成文本报告
        with open(report_dir / "summary.txt", 'w', encoding='utf-8') as f:
            f.write("=" * 50 + "\n")
            f.write("回测业绩摘要\n")
            f.write("=" * 50 + "\n\n")
            
            # 基础指标
            f.write(f"回测期间: {summary.get('start_date')} ~ {summary.get('end_date')}\n")
            f.write(f"初始资金: {summary.get('initial_capital', 0):,.2f}\n")
            f.write(f"最终资金: {summary.get('final_capital', 0):,.2f}\n")
            f.write(f"总收益率: {summary.get('total_return', 0):.2%}\n")
            f.write(f"年化收益率: {summary.get('annual_return', 0):.2%}\n\n")
            
            # 风险指标
            f.write(f"夏普比率: {summary.get('sharpe_ratio', 0):.3f}\n")
            f.write(f"最大回撤: {summary.get('max_drawdown', 0):.2%}\n")
            f.write(f"胜率: {summary.get('win_rate', 0):.2%}\n")
            f.write(f"盈亏比: {summary.get('profit_loss_ratio', 0):.2f}\n\n")
            
            # 交易统计
            f.write(f"总交易次数: {summary.get('total_trades', 0)}\n")
            f.write(f"盈利次数: {summary.get('winning_trades', 0)}\n")
            f.write(f"亏损次数: {summary.get('losing_trades', 0)}\n")
            
    def _plot_equity_curve(self, equity_curve: pd.DataFrame, report_dir: Path):
        """绘制资金曲线"""
        if equity_curve.empty:
            return
            
        fig, ax = plt.subplots(figsize=(12, 6))
        
        # 绘制资金曲线
        ax.plot(equity_curve.index, equity_curve['equity'], 
                label='资金曲线', linewidth=2)
        
        # 添加基准线（如果有）
        if 'benchmark' in equity_curve.columns:
            ax.plot(equity_curve.index, equity_curve['benchmark'],
                   label='基准', linewidth=1, alpha=0.7)
                   
        ax.set_xlabel('日期')
        ax.set_ylabel('资金')
        ax.set_title('策略资金曲线')
        ax.legend()
        ax.grid(True, alpha=0.3)
        
        # 格式化日期
        ax.xaxis.set_major_formatter(DateFormatter('%Y-%m'))
        plt.xticks(rotation=45)
        
        plt.tight_layout()
        plt.savefig(report_dir / 'equity_curve.png', dpi=300)
        plt.close()
        
    def _plot_drawdown(self, equity_curve: pd.DataFrame, report_dir: Path):
        """绘制回撤图"""
        if equity_curve.empty or 'equity' not in equity_curve.columns:
            return
            
        # 计算回撤
        rolling_max = equity_curve['equity'].expanding().max()
        drawdown = (equity_curve['equity'] - rolling_max) / rolling_max
        
        fig, ax = plt.subplots(figsize=(12, 6))
        
        # 填充回撤区域
        ax.fill_between(drawdown.index, 0, drawdown.values,
                       color='red', alpha=0.3, label='回撤')
        ax.plot(drawdown.index, drawdown.values,
               color='red', linewidth=1)
               
        ax.set_xlabel('日期')
        ax.set_ylabel('回撤比例')
        ax.set_title('策略回撤分析')
        ax.legend()
        ax.grid(True, alpha=0.3)
        
        # 格式化日期
        ax.xaxis.set_major_formatter(DateFormatter('%Y-%m'))
        plt.xticks(rotation=45)
        
        # 显示最大回撤
        max_dd = drawdown.min()
        max_dd_date = drawdown.idxmin()
        ax.annotate(f'最大回撤: {max_dd:.2%}',
                   xy=(max_dd_date, max_dd),
                   xytext=(max_dd_date, max_dd - 0.05),
                   arrowprops=dict(arrowstyle='->', color='red'))
                   
        plt.tight_layout()
        plt.savefig(report_dir / 'drawdown.png', dpi=300)
        plt.close()
        
    def _analyze_trades(self, trades: pd.DataFrame, report_dir: Path):
        """分析交易记录"""
        if trades.empty:
            return
            
        # 交易盈亏分布
        fig, (ax1, ax2) = plt.subplots(1, 2, figsize=(12, 5))
        
        # 盈亏分布直方图
        profits = trades['profit']
        ax1.hist(profits, bins=30, edgecolor='black', alpha=0.7)
        ax1.axvline(x=0, color='red', linestyle='--', label='盈亏分界')
        ax1.set_xlabel('盈亏金额')
        ax1.set_ylabel('交易次数')
        ax1.set_title('交易盈亏分布')
        ax1.legend()
        
        # 累计盈亏曲线
        cumulative_profit = profits.cumsum()
        ax2.plot(range(len(cumulative_profit)), cumulative_profit)
        ax2.fill_between(range(len(cumulative_profit)), 0, cumulative_profit,
                        where=cumulative_profit >= 0, color='green', alpha=0.3)
        ax2.fill_between(range(len(cumulative_profit)), 0, cumulative_profit,
                        where=cumulative_profit < 0, color='red', alpha=0.3)
        ax2.set_xlabel('交易次数')
        ax2.set_ylabel('累计盈亏')
        ax2.set_title('累计盈亏曲线')
        ax2.grid(True, alpha=0.3)
        
        plt.tight_layout()
        plt.savefig(report_dir / 'trade_analysis.png', dpi=300)
        plt.close()
        
        # 保存交易记录
        trades.to_csv(report_dir / 'trades.csv', index=False, encoding='utf-8')
        
    def _analyze_positions(self, positions: pd.DataFrame, report_dir: Path):
        """分析持仓情况"""
        if positions.empty:
            return
            
        # 持仓分布饼图
        fig, ax = plt.subplots(figsize=(8, 8))
        
        # 按股票统计持仓市值
        position_values = positions.groupby('symbol')['market_value'].sum()
        position_values = position_values.sort_values(ascending=False).head(10)
        
        # 绘制饼图
        ax.pie(position_values.values, labels=position_values.index,
              autopct='%1.1f%%', startangle=90)
        ax.set_title('持仓分布（前10）')
        
        plt.tight_layout()
        plt.savefig(report_dir / 'position_distribution.png', dpi=300)
        plt.close()
        
    def _generate_html_report(self, 
                            strategy_name: str,
                            summary: Dict,
                            report_dir: Path) -> Path:
        """生成HTML总报告"""
        html_content = f"""
<!DOCTYPE html>
<html>
<head>
    <meta charset="utf-8">
    <title>{strategy_name} - 回测报告</title>
    <style>
        body {{
            font-family: Arial, sans-serif;
            margin: 20px;
            background-color: #f5f5f5;
        }}
        .container {{
            max-width: 1200px;
            margin: 0 auto;
            background-color: white;
            padding: 20px;
            box-shadow: 0 0 10px rgba(0,0,0,0.1);
        }}
        h1, h2 {{
            color: #333;
        }}
        .summary {{
            display: grid;
            grid-template-columns: repeat(3, 1fr);
            gap: 20px;
            margin: 20px 0;
        }}
        .metric {{
            background-color: #f8f8f8;
            padding: 15px;
            border-radius: 5px;
        }}
        .metric-value {{
            font-size: 24px;
            font-weight: bold;
            color: #2196F3;
        }}
        .metric-label {{
            font-size: 14px;
            color: #666;
        }}
        .chart {{
            margin: 20px 0;
            text-align: center;
        }}
        img {{
            max-width: 100%;
            height: auto;
        }}
    </style>
</head>
<body>
    <div class="container">
        <h1>{strategy_name} - 回测报告</h1>
        <p>生成时间: {datetime.now().strftime('%Y-%m-%d %H:%M:%S')}</p>
        
        <h2>业绩摘要</h2>
        <div class="summary">
            <div class="metric">
                <div class="metric-label">总收益率</div>
                <div class="metric-value">{summary.get('total_return', 0):.2%}</div>
            </div>
            <div class="metric">
                <div class="metric-label">年化收益率</div>
                <div class="metric-value">{summary.get('annual_return', 0):.2%}</div>
            </div>
            <div class="metric">
                <div class="metric-label">夏普比率</div>
                <div class="metric-value">{summary.get('sharpe_ratio', 0):.3f}</div>
            </div>
            <div class="metric">
                <div class="metric-label">最大回撤</div>
                <div class="metric-value">{summary.get('max_drawdown', 0):.2%}</div>
            </div>
            <div class="metric">
                <div class="metric-label">胜率</div>
                <div class="metric-value">{summary.get('win_rate', 0):.2%}</div>
            </div>
            <div class="metric">
                <div class="metric-label">总交易次数</div>
                <div class="metric-value">{summary.get('total_trades', 0)}</div>
            </div>
        </div>
        
        <h2>资金曲线</h2>
        <div class="chart">
            <img src="equity_curve.png" alt="资金曲线">
        </div>
        
        <h2>回撤分析</h2>
        <div class="chart">
            <img src="drawdown.png" alt="回撤分析">
        </div>
        
        <h2>交易分析</h2>
        <div class="chart">
            <img src="trade_analysis.png" alt="交易分析">
        </div>
        
        <h2>持仓分布</h2>
        <div class="chart">
            <img src="position_distribution.png" alt="持仓分布">
        </div>
    </div>
</body>
</html>
        """
        
        html_path = report_dir / 'report.html'
        with open(html_path, 'w', encoding='utf-8') as f:
            f.write(html_content)
            
        return html_path