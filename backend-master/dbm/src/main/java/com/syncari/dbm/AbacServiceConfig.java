package com.syncari.dbm;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import com.syncari.core.abac.AbacService;
import com.syncari.core.abac.AbacServiceMockImpl;

@Configuration
public class AbacServiceConfig {
    
    @Bean
    public AbacService abacService() {
      return new AbacServiceMockImpl();
    }

}
