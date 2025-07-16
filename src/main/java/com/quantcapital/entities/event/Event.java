package com.quantcapital.entities.event;

import com.fasterxml.jackson.annotation.JsonFormat;
import com.fasterxml.jackson.annotation.JsonSubTypes;
import com.fasterxml.jackson.annotation.JsonTypeInfo;
import com.quantcapital.entities.constant.EventType;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.Map;
import java.util.UUID;

/**
 * 事件基类
 * <p>
 * 所有事件的基础类，包含事件的通用属性。
 * 支持序列化和反序列化，便于事件的持久化和网络传输。
 *
 * @author QuantCapital Team
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@JsonTypeInfo(use = JsonTypeInfo.Id.NAME, include = JsonTypeInfo.As.PROPERTY, property = "eventType")
@JsonSubTypes({@JsonSubTypes.Type(value = MarketEvent.class, name = "MARKET"),
        @JsonSubTypes.Type(value = SignalEvent.class, name = "SIGNAL"),
        @JsonSubTypes.Type(value = OrderEvent.class, name = "ORDER"),
        @JsonSubTypes.Type(value = FillEvent.class, name = "FILL"),
        @JsonSubTypes.Type(value = TimerEvent.class, name = "TIMER")})
public abstract class Event {

    /**
     * 事件ID，全局唯一
     */
    private String eventId;

    /**
     * 事件类型
     */
    private EventType type;

    /**
     * 事件时间戳
     */
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private LocalDateTime timestamp;

    /**
     * 事件关联的标的代码（可选）
     */
    private String symbol;

    /**
     * 事件优先级（数值越小优先级越高）
     */
    private int priority = 0;

    /**
     * 事件扩展数据
     */
    private Map<String, Object> data;

    /**
     * 构造函数
     *
     * @param type      事件类型
     * @param timestamp 事件时间
     */
    protected Event(EventType type, LocalDateTime timestamp) {
        this.eventId = UUID.randomUUID().toString();
        this.type = type;
        this.timestamp = timestamp;
    }

    /**
     * 构造函数（带标的代码）
     *
     * @param type      事件类型
     * @param timestamp 事件时间
     * @param symbol    标的代码
     */
    protected Event(EventType type, LocalDateTime timestamp, String symbol) {
        this(type, timestamp);
        this.symbol = symbol;
    }

    /**
     * 获取事件描述
     *
     * @return 事件描述字符串
     */
    public abstract String getDescription();

    /**
     * 判断事件是否过期
     *
     * @param currentTime    当前时间
     * @param timeoutSeconds 超时秒数
     * @return 是否过期
     */
    public boolean isExpired(LocalDateTime currentTime, long timeoutSeconds) {
        return timestamp.plusSeconds(timeoutSeconds).isBefore(currentTime);
    }

    @Override
    public String toString() {
        return String.format("%s[id=%s, type=%s, timestamp=%s, symbol=%s]", getClass().getSimpleName(), eventId, type,
                timestamp, symbol);
    }
}