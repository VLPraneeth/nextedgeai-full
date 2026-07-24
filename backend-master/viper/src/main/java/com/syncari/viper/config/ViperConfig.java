package com.syncari.viper.config;

import akka.actor.ActorSystem;
import akka.stream.ActorMaterializer;
import akka.stream.ActorMaterializerSettings;
import akka.stream.Materializer;
import com.google.common.util.concurrent.ThreadFactoryBuilder;
import com.syncari.core.abac.AbacService;
import com.syncari.core.abac.AbacServiceMockImpl;
import com.syncari.core.config.AppConfig;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.task.TaskExecutionAutoConfiguration;
import org.springframework.boot.task.TaskExecutorBuilder;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Lazy;
import org.springframework.scheduling.annotation.AsyncAnnotationBeanPostProcessor;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.ThreadPoolExecutor;

@Configuration
public class ViperConfig {
    public static final int MAX_NODE_AUDIT_WORKERS = 3;
    
    @Autowired
    AppConfig appConfig;

    @Bean
    public Materializer materializer() {
        ActorSystem viper = ActorSystem.create("viper");
        //DO NOT Change buffer size here. We don't want pipelining inside our streams. That'll mess up everything
        //TODO: enforce this in EntityStream by using a per-stream permit
        return ActorMaterializer.create(ActorMaterializerSettings.apply(viper)
                .withDispatcher("viper-dispatcher")
                .withInputBuffer(1, 1), viper);
    }

    @Bean(name = "nodeAuditWriterPool")
    public ExecutorService nodeAuditWriterPool() {
        final ThreadPoolExecutor service = (ThreadPoolExecutor) Executors.newFixedThreadPool(MAX_NODE_AUDIT_WORKERS);
        ThreadFactory namedThreadFactory =
                new ThreadFactoryBuilder().setNameFormat("viper-nodeauditwriter-%d").build();
        service.setThreadFactory(namedThreadFactory);
        return service;
    }

    /**
     * The default spring task executor doesnt work if a custom executor is registered as a bean!
     * So we explicitly set up  spring task executor here
     *
     * @param builder
     * @return
     */
    @Lazy
    @Bean(name = {TaskExecutionAutoConfiguration.APPLICATION_TASK_EXECUTOR_BEAN_NAME,
            AsyncAnnotationBeanPostProcessor.DEFAULT_TASK_EXECUTOR_BEAN_NAME})
    public ThreadPoolTaskExecutor applicationTaskExecutor(TaskExecutorBuilder builder) {
        return builder.build();
    }
    
    @Bean
    public AbacService abacService() {
      return new AbacServiceMockImpl();
    }
}