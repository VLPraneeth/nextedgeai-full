package com.syncari.viper.scheduler;

import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.List;

import javax.annotation.PostConstruct;

import com.syncari.core.config.AppConfig;
import com.syncari.core.service.EmailService;
import com.syncari.core.service.Monitorable;
import com.syncari.core.service.MonitorableService;
import com.syncari.viper.InstanceUtil;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.context.ApplicationContext;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.util.Assert;

import lombok.extern.slf4j.Slf4j;

@Component
@Slf4j
public class MonitorableScheduler {

    @Autowired
    private ApplicationContext context;
    @Autowired
    @Qualifier("defaultEmailService")
    EmailService emailService;
    @Autowired
    AppConfig appConfig;
    @Autowired
    InstanceUtil instanceUtil;

    public static final String BASE_PACKAGE = "com.syncari";

    private List<String> monitorableBeans = new ArrayList<>();
    private List<Method> monitorableMethods = new ArrayList<>();

    @PostConstruct
    public void init() {
        monitorableBeans = List.of(context.getBeanNamesForAnnotation(Monitorable.class));
        Class monitorableIface = MonitorableService.class;
        monitorableMethods = List.of(monitorableIface.getMethods());
    }

    public List<String> getMonitorableServices() {
        return monitorableBeans;
    }

    @Scheduled(fixedRate = 120000)
    public void scheduleMonitorables() {
        monitorableBeans.forEach(name -> {
            monitorableMethods.forEach(monitorableMethod -> {
                log.info("Invoking monitorable method {} for service {}", monitorableMethod.getName(), name);
                monitor(name, monitorableMethod);
            });
        });
    }

    private void monitor(String monitorableComponent, Method monitorableMethod) {
        Object monitorableBean = context.getBean(monitorableComponent);
        instanceUtil.forEachInstance(context -> {
            try {
                Assert.isTrue(monitorableMethod.getParameterTypes().length == 0, 
                    "Invoking monitorable method with parameters is not supported");
                monitorableBean.getClass().getMethod(monitorableMethod.getName()).invoke(monitorableBean);
            } catch (Exception e) {
                String msg = String.format("Failed to invoke monitorable method %s for service %s", 
                    monitorableMethod.getName(), monitorableComponent);
                log.error(msg, e);
                emailService.sendErrorEmail(List.of(), appConfig.getErrorEmail(), msg, e.getMessage());
            }
        });
    }
}