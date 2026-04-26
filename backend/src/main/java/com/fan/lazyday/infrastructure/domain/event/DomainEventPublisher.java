package com.fan.lazyday.infrastructure.domain.event;


/**
 * des something
 *
 * @author bufanqi
 * @date 2021-09-18 09:59
 **/
public interface DomainEventPublisher<T> {
    /**
     * 发布事件
     *
     * @param event 领域事件
     */
    void publishEvent(DomainEvent<T> event);
}