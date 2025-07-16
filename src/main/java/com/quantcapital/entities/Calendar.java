package com.quantcapital.entities;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.*;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.stream.Collectors;

/**
 * 交易日历实体
 * 
 * 管理交易日和交易时间信息，支持不同市场的交易时间配置。
 * 提供交易日判断、交易时间段查询、下一交易日计算等功能。
 * 
 * @author QuantCapital Team
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class Calendar {
    
    /** 市场名称 */
    @Builder.Default
    private String market = "A_SHARE";
    
    /** 时区 */
    @Builder.Default
    private ZoneId timezone = ZoneId.of("Asia/Shanghai");
    
    // ==================== A股交易时间配置 ====================
    
    /** 上午开盘时间 */
    @Builder.Default
    private LocalTime morningStart = LocalTime.of(9, 30);
    
    /** 上午收盘时间 */
    @Builder.Default
    private LocalTime morningEnd = LocalTime.of(11, 30);
    
    /** 下午开盘时间 */
    @Builder.Default
    private LocalTime afternoonStart = LocalTime.of(13, 0);
    
    /** 下午收盘时间 */
    @Builder.Default
    private LocalTime afternoonEnd = LocalTime.of(15, 0);
    
    /** 集合竞价开始时间 */
    @Builder.Default
    private LocalTime auctionStart = LocalTime.of(9, 15);
    
    /** 集合竞价结束时间 */
    @Builder.Default
    private LocalTime auctionEnd = LocalTime.of(9, 25);
    
    // ==================== 节假日配置 ====================
    
    /** 节假日集合 */
    @Builder.Default
    private Set<LocalDate> holidays = new HashSet<>();
    
    /** 调休工作日集合（原本是周末但调休为工作日） */
    @Builder.Default
    private Set<LocalDate> workingWeekends = new HashSet<>();
    
    // ==================== 其他配置 ====================
    
    /** 是否启用半日交易（如除夕） */
    @Builder.Default
    private Map<LocalDate, TradingSession> halfDayTradingSessions = new HashMap<>();
    
    /**
     * 交易时段定义
     */
    @Data
    @AllArgsConstructor
    @NoArgsConstructor
    public static class TradingSession {
        /** 开始时间 */
        private LocalTime start;
        
        /** 结束时间 */
        private LocalTime end;
        
        /** 时段名称 */
        private String name;
        
        /**
         * 检查指定时间是否在此交易时段内
         * 
         * @param time 时间
         * @return 是否在交易时段内
         */
        public boolean contains(LocalTime time) {
            return !time.isBefore(start) && !time.isAfter(end);
        }
    }
    
    /**
     * 市场类型枚举
     */
    public enum MarketType {
        /** A股市场 */
        A_SHARE("A股", ZoneId.of("Asia/Shanghai")),
        /** 港股市场 */
        HK_STOCK("港股", ZoneId.of("Asia/Hong_Kong")),
        /** 美股市场 */
        US_STOCK("美股", ZoneId.of("America/New_York")),
        /** 加密货币（24小时） */
        CRYPTO("加密货币", ZoneId.of("UTC"));
        
        private final String description;
        private final ZoneId timezone;
        
        MarketType(String description, ZoneId timezone) {
            this.description = description;
            this.timezone = timezone;
        }
        
        public String getDescription() {
            return description;
        }
        
        public ZoneId getTimezone() {
            return timezone;
        }
    }
    
    /**
     * 创建A股市场日历
     * 
     * @return A股交易日历
     */
    public static Calendar createAShareCalendar() {
        Calendar calendar = new Calendar();
        calendar.market = "A_SHARE";
        calendar.timezone = ZoneId.of("Asia/Shanghai");
        
        // 添加常见节假日（需要根据实际年份更新）
        calendar.addCommonChineseHolidays(Year.now().getValue());
        
        return calendar;
    }
    
    /**
     * 判断是否为交易日
     * 
     * @param date 日期
     * @return 是否为交易日
     */
    public boolean isTradingDay(LocalDate date) {
        // 检查是否在工作日调休名单中
        if (workingWeekends.contains(date)) {
            return true;
        }
        
        // 周末不是交易日（除非在调休名单中）
        DayOfWeek dayOfWeek = date.getDayOfWeek();
        if (dayOfWeek == DayOfWeek.SATURDAY || dayOfWeek == DayOfWeek.SUNDAY) {
            return false;
        }
        
        // 节假日不是交易日
        return !holidays.contains(date);
    }
    
    /**
     * 获取交易时间段
     * 
     * @param date 日期
     * @return 交易时间段列表
     */
    public List<TradingSession> getTradingSessions(LocalDate date) {
        if (!isTradingDay(date)) {
            return new ArrayList<>();
        }
        
        // 检查是否有特殊的半日交易安排
        if (halfDayTradingSessions.containsKey(date)) {
            TradingSession halfDay = halfDayTradingSessions.get(date);
            return Arrays.asList(halfDay);
        }
        
        // 正常交易日的时间段
        List<TradingSession> sessions = new ArrayList<>();
        sessions.add(new TradingSession(morningStart, morningEnd, "上午盘"));
        sessions.add(new TradingSession(afternoonStart, afternoonEnd, "下午盘"));
        
        return sessions;
    }
    
    /**
     * 获取当前时间是否在交易时间内
     * 
     * @param dateTime 日期时间
     * @return 是否在交易时间内
     */
    public boolean isInTradingHours(LocalDateTime dateTime) {
        LocalDate date = dateTime.toLocalDate();
        LocalTime time = dateTime.toLocalTime();
        
        if (!isTradingDay(date)) {
            return false;
        }
        
        List<TradingSession> sessions = getTradingSessions(date);
        return sessions.stream().anyMatch(session -> session.contains(time));
    }
    
    /**
     * 获取当前时间是否在集合竞价时间内
     * 
     * @param dateTime 日期时间
     * @return 是否在集合竞价时间内
     */
    public boolean isInAuctionTime(LocalDateTime dateTime) {
        LocalDate date = dateTime.toLocalDate();
        LocalTime time = dateTime.toLocalTime();
        
        if (!isTradingDay(date)) {
            return false;
        }
        
        return !time.isBefore(auctionStart) && !time.isAfter(auctionEnd);
    }
    
    /**
     * 获取下一个交易日
     * 
     * @param date 当前日期
     * @return 下一个交易日
     */
    public LocalDate getNextTradingDay(LocalDate date) {
        LocalDate nextDay = date.plusDays(1);
        while (!isTradingDay(nextDay)) {
            nextDay = nextDay.plusDays(1);
        }
        return nextDay;
    }
    
    /**
     * 获取上一个交易日
     * 
     * @param date 当前日期
     * @return 上一个交易日
     */
    public LocalDate getPreviousTradingDay(LocalDate date) {
        LocalDate prevDay = date.minusDays(1);
        while (!isTradingDay(prevDay)) {
            prevDay = prevDay.minusDays(1);
        }
        return prevDay;
    }
    
    /**
     * 获取指定期间内的所有交易日
     * 
     * @param startDate 开始日期（包含）
     * @param endDate 结束日期（包含）
     * @return 交易日列表
     */
    public List<LocalDate> getTradingDays(LocalDate startDate, LocalDate endDate) {
        return startDate.datesUntil(endDate.plusDays(1))
                .filter(this::isTradingDay)
                .collect(Collectors.toList());
    }
    
    /**
     * 计算两个日期之间的交易日天数
     * 
     * @param startDate 开始日期
     * @param endDate 结束日期
     * @return 交易日天数
     */
    public long countTradingDays(LocalDate startDate, LocalDate endDate) {
        return getTradingDays(startDate, endDate).size();
    }
    
    /**
     * 添加节假日
     * 
     * @param holiday 节假日
     */
    public void addHoliday(LocalDate holiday) {
        holidays.add(holiday);
    }
    
    /**
     * 批量添加节假日
     * 
     * @param holidays 节假日列表
     */
    public void addHolidays(Collection<LocalDate> holidays) {
        this.holidays.addAll(holidays);
    }
    
    /**
     * 移除节假日
     * 
     * @param holiday 节假日
     */
    public void removeHoliday(LocalDate holiday) {
        holidays.remove(holiday);
    }
    
    /**
     * 添加调休工作日
     * 
     * @param workingWeekend 调休工作日
     */
    public void addWorkingWeekend(LocalDate workingWeekend) {
        workingWeekends.add(workingWeekend);
    }
    
    /**
     * 添加半日交易安排
     * 
     * @param date 日期
     * @param session 交易时段
     */
    public void addHalfDayTradingSession(LocalDate date, TradingSession session) {
        halfDayTradingSessions.put(date, session);
    }
    
    /**
     * 添加常见中国节假日（需要根据实际年份调整）
     * 
     * @param year 年份
     */
    private void addCommonChineseHolidays(int year) {
        // 元旦
        addHoliday(LocalDate.of(year, 1, 1));
        
        // 春节（示例日期，实际需要根据农历计算）
        // 这里只是示例，实际应用中需要根据官方发布的节假日安排进行配置
        
        // 清明节（通常在4月4日或5日）
        addHoliday(LocalDate.of(year, 4, 5));
        
        // 劳动节
        addHoliday(LocalDate.of(year, 5, 1));
        
        // 端午节（需要根据农历计算）
        
        // 中秋节（需要根据农历计算）
        
        // 国庆节
        for (int day = 1; day <= 7; day++) {
            addHoliday(LocalDate.of(year, 10, day));
        }
    }
    
    /**
     * 获取今日交易时间段
     * 
     * @return 今日交易时间段
     */
    public List<TradingSession> getTodayTradingSessions() {
        return getTradingSessions(LocalDate.now(timezone));
    }
    
    /**
     * 获取下一个交易时间段的开始时间
     * 
     * @param currentDateTime 当前时间
     * @return 下一个交易时间段开始时间
     */
    public LocalDateTime getNextTradingSessionStart(LocalDateTime currentDateTime) {
        LocalDate date = currentDateTime.toLocalDate();
        LocalTime time = currentDateTime.toLocalTime();
        
        // 检查当前日期的交易时段
        List<TradingSession> sessions = getTradingSessions(date);
        for (TradingSession session : sessions) {
            if (time.isBefore(session.getStart())) {
                return LocalDateTime.of(date, session.getStart());
            }
        }
        
        // 如果当天没有更多交易时段，找下一个交易日的第一个时段
        LocalDate nextTradingDay = getNextTradingDay(date);
        List<TradingSession> nextSessions = getTradingSessions(nextTradingDay);
        if (!nextSessions.isEmpty()) {
            return LocalDateTime.of(nextTradingDay, nextSessions.get(0).getStart());
        }
        
        return null;
    }
    
    /**
     * 格式化交易日历信息
     * 
     * @return 格式化字符串
     */
    public String formatCalendarInfo() {
        StringBuilder sb = new StringBuilder();
        sb.append(String.format("交易日历[%s]\n", market));
        sb.append(String.format("时区: %s\n", timezone));
        sb.append(String.format("上午交易: %s - %s\n", morningStart, morningEnd));
        sb.append(String.format("下午交易: %s - %s\n", afternoonStart, afternoonEnd));
        sb.append(String.format("集合竞价: %s - %s\n", auctionStart, auctionEnd));
        sb.append(String.format("节假日数量: %d\n", holidays.size()));
        sb.append(String.format("调休工作日数量: %d", workingWeekends.size()));
        
        return sb.toString();
    }
    
    @Override
    public String toString() {
        return String.format("Calendar[%s %s 节假日:%d天]", 
                market, timezone, holidays.size());
    }
}