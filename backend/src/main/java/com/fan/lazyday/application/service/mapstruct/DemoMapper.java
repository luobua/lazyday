package com.fan.lazyday.application.service.mapstruct;

import com.fan.lazyday.application.service.bo.DemoBo;
import com.fan.lazyday.interfaces.request.DemoRequest;
import org.mapstruct.Mapper;

import static org.mapstruct.MappingConstants.ComponentModel.SPRING;

/**
 * @author chenbin
 */
@Mapper(componentModel = SPRING)
public interface DemoMapper {
    DemoBo toBo(DemoRequest value);
}
