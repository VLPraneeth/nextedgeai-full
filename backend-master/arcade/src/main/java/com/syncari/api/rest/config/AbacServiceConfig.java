package com.syncari.api.rest.config;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import com.syncari.core.abac.AbacService;
import com.syncari.core.abac.AbacServiceImpl;
import com.syncari.core.abac.SyncariNativeAbacServiceImpl;
import com.syncari.core.config.AppConfig;

@Configuration
public class AbacServiceConfig {
  @Autowired
  AppConfig appConfig;


  @Bean
  public AbacService abacService() {
    return new AbacServiceImpl();
  }

  @Bean
  public SyncariNativeAbacServiceImpl syncariNativeAbacService() {
    return new SyncariNativeAbacServiceImpl();
  }

}
