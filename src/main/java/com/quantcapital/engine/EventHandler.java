package com.quantcapital.engine;

import com.quantcapital.entities.Event;

/**
 * 事件处理器接口
 * <p>
 * 所有事件处理器的基础接口，定义事件处理的标准方法。
 * 实现类需要提供具体的事件处理逻辑。
 *
 * @author QuantCapital Team
 */
public interface EventHandler {

    /**
     * 获取处理器名称
     *
     * @return 处理器名称
     */
    String getName();

    /**
     * 处理事件
     *
     * @param event 待处理的事件
     * @throws Exception 处理过程中可能发生的异常
     */
    void handleEvent(Event event) throws Exception;

    /**
     * 判断是否能处理指定类型的事件
     *
     * @param eventType 事件类型
     * @return 是否能处理
     */
    default boolean canHandle(String eventType) {
        return true; // 默认可以处理所有事件类型
    }

    /**
     * 获取处理器的优先级
     * 数值越小优先级越高
     *
     * @return 优先级
     */
    default int getPriority() {
        return 5; // 默认中等优先级
    }

    /**
     * 处理器初始化
     * 在注册到事件引擎时调用
     */
    default void initialize() {
        // 默认空实现
    }

    /**
     * 处理器销毁
     * 在从事件引擎注销时调用
     */
    default void destroy() {
        // 默认空实现
    }

    /**
     * 判断处理器是否启用
     *
     * @return 是否启用
     */
    default boolean isEnabled() {
        return true; // 默认启用
    }
}