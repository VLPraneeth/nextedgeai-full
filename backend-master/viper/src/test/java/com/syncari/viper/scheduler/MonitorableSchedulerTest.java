package com.syncari.viper.scheduler;

import static org.junit.Assert.assertTrue;

import java.lang.reflect.Method;
import java.util.List;

import com.syncari.TestConfig;
import com.syncari.core.service.MonitorableService;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.junit4.SpringRunner;
import org.springframework.test.context.support.AnnotationConfigContextLoader;

@RunWith(SpringRunner.class)
@SpringBootTest
@TestPropertySource(locations = "classpath:test_application.properties")
@ContextConfiguration(classes = TestConfig.class, loader = AnnotationConfigContextLoader.class)
public class MonitorableSchedulerTest {

    @Autowired
    private MonitorableScheduler monitorableScheduler;
    
    @Test
    public void monitorableSchedulerTest() {
        List<String> monitorableServices = monitorableScheduler.getMonitorableServices();
        assertTrue(monitorableServices.contains("pipelineTestService"));
        assertTrue(monitorableServices.contains("mockMonitorableService"));
    }

    @Test
    public void getMonitorableMethods() throws ClassNotFoundException {
        assertTrue(List.of(MonitorableService.class.getMethods()).stream().anyMatch(x -> "buryTheDead".equals(x.getName())));
    }
}
