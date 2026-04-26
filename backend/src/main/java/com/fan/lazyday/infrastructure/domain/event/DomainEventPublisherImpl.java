package com.fan.lazyday.infrastructure.domain.event;


import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Component;

/**
 * des 领域时间发布
 *
 * @author bufanqi
 * @date 2021-09-18 10:01
 **/
@Component
@Slf4j
public class DomainEventPublisherImpl<T> implements DomainEventPublisher<T> {

    @Autowired
    private ApplicationEventPublisher applicationEventPublisher;

    @Override
    public void publishEvent(DomainEvent<T> event) {
        applicationEventPublisher.publishEvent(event);
    }
}