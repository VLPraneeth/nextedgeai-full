package com.syncari.karibu.rest.controllers;

import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.data.redis.RedisProperties;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.util.SocketUtils;
import redis.embedded.RedisServer;

import javax.annotation.PostConstruct;
import javax.annotation.PreDestroy;

@TestConfiguration
//@Component
@Slf4j
public class TestRedisConfiguration {

    private RedisServer redisServer;

    public TestRedisConfiguration(RedisProperties redisProperties) {
        this.redisServer = new RedisServer(redisProperties.getPort());
    }

    @PostConstruct
    public void start() {
        int port = redisServer.ports().get(0);
        try {
            // hack to check if port is already used
            log.info("Starting redis server " + SocketUtils.findAvailableTcpPort(port, port));
            //SocketUtils.findAvailableTcpPort(port, port);
            redisServer.start();
        } catch (Exception e) {
            log.info("Server already running");
        }
    }

    @PreDestroy
    public void stop() {
        log.info("Stopping redis server ");
        if (redisServer.isActive()) {
            redisServer.stop();
        }
    }

}
