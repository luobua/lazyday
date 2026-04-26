package com.fan.lazyday.application.service.impl;

import com.fan.lazyday.application.service.DemoService;
import com.fan.lazyday.application.service.mapstruct.DemoMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class DemoServiceImpl implements DemoService {
    private final DemoMapper demoMapper;
}
