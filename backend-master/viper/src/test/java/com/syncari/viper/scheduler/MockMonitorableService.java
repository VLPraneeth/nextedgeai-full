package com.syncari.viper.scheduler;

import com.syncari.core.service.Monitorable;
import com.syncari.core.service.MonitorableService;

import org.springframework.stereotype.Component;

import lombok.extern.slf4j.Slf4j;

@Slf4j
// Should be a component/bean for Spring discovery.
@Component
// Monitorable annotation to be discovered by the HeartBeatScheduler. name is irrelevant, can be anything.
@Monitorable(name = "MockMonitorableService")
// Class should implement MonitorableService to match the exact method name.
public class MockMonitorableService implements MonitorableService {

    @Override
    public void buryTheDead() {
        log.info("***** buryTheDead method invoked for MockMonitorableService *****");
    }
    
}
