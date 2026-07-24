package com.syncari.core.config;

import com.fasterxml.jackson.annotation.JsonAutoDetect;
import com.fasterxml.jackson.annotation.JsonTypeInfo;
import com.fasterxml.jackson.databind.MapperFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.json.JsonMapper;
import com.fasterxml.jackson.databind.jsontype.BasicPolymorphicTypeValidator;
import com.fasterxml.jackson.databind.jsontype.PolymorphicTypeValidator;
import com.fasterxml.jackson.databind.jsontype.impl.LaissezFaireSubTypeValidator;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.apache.commons.pool2.impl.GenericObjectPoolConfig;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.cache.CacheManager;
import org.springframework.cache.annotation.EnableCaching;
import org.springframework.cache.support.NoOpCacheManager;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;
import org.springframework.context.annotation.Profile;
import org.springframework.data.redis.cache.RedisCacheConfiguration;
import org.springframework.data.redis.cache.RedisCacheWriter;
import org.springframework.data.redis.connection.RedisStandaloneConfiguration;
import org.springframework.data.redis.connection.lettuce.LettuceConnectionFactory;
import org.springframework.data.redis.serializer.GenericJackson2JsonRedisSerializer;
import org.springframework.data.redis.serializer.RedisSerializationContext;
import org.springframework.stereotype.Component;
import redis.clients.jedis.*;
import redis.clients.jedis.providers.PooledConnectionProvider;

import java.time.Duration;

@EnableCaching
@Component
@Slf4j
public class RedisConfig {

    @Autowired
    private AppConfig appConfig;

    @Autowired
    RedisCacheConfiguration cacheConfiguration;

    private int TTL_IN_MINUTES = 60;

    @Bean("cacheConfiguration")
    public RedisCacheConfiguration cacheConfiguration() {

        PolymorphicTypeValidator ptv =
                BasicPolymorphicTypeValidator.builder()
                        .allowIfSubType("com.syncari.core")
                        .build();
        ObjectMapper objectMapper = JsonMapper.builder()
                .polymorphicTypeValidator(ptv)
                .activateDefaultTyping(LaissezFaireSubTypeValidator.instance, ObjectMapper.DefaultTyping.NON_FINAL, JsonTypeInfo.As.WRAPPER_ARRAY)
                .configure(MapperFeature.PROPAGATE_TRANSIENT_MARKER, true)
                .build();


        objectMapper.setVisibility(objectMapper.getSerializationConfig().getDefaultVisibilityChecker()
                .withFieldVisibility(JsonAutoDetect.Visibility.ANY)
                .withGetterVisibility(JsonAutoDetect.Visibility.NONE)
                .withSetterVisibility(JsonAutoDetect.Visibility.NONE)
                .withIsGetterVisibility(JsonAutoDetect.Visibility.NONE)
                .withCreatorVisibility(JsonAutoDetect.Visibility.NONE)).registerModule(new JavaTimeModule());

        return RedisCacheConfiguration.defaultCacheConfig()
                .entryTtl(Duration.ofMinutes(TTL_IN_MINUTES))
                .disableCachingNullValues()
                .serializeValuesWith(RedisSerializationContext.SerializationPair.fromSerializer(new GenericJackson2JsonRedisSerializer(objectMapper)));
    }


    @Bean(name="oldRedisConnectionFactory")
    @Primary
    public LettuceConnectionFactory oldRedisConnectionFactory() {
        var redisConfig = new RedisStandaloneConfiguration(appConfig.redisHost, appConfig.redisPort);
        redisConfig.setPassword(appConfig.redisPassword);
        return new LettuceConnectionFactory(redisConfig);
    }

    @Bean(name="newRedisConnectionFactory")
    public LettuceConnectionFactory newRedisConnectionFactory() {
        var redisConfig = new RedisStandaloneConfiguration(appConfig.redisNewHost, appConfig.redisNewPort);
        redisConfig.setPassword(appConfig.redisNewPassword);
        return new LettuceConnectionFactory(redisConfig);
    }

    @Profile("!development && !ci && !dbm")
    @Bean
    @Primary
    public CacheManager redisCacheManager(@Qualifier("oldRedisConnectionFactory") LettuceConnectionFactory oldRedisConnectionFactory, @Qualifier("newRedisConnectionFactory") LettuceConnectionFactory newRedisConnectionFactory) {
        return new CustomRedisCacheManager(RedisCacheWriter.lockingRedisCacheWriter(oldRedisConnectionFactory),
                RedisCacheWriter.lockingRedisCacheWriter(newRedisConnectionFactory), cacheConfiguration);
    }

    @Profile("development || ci || dbm")
    @Primary
    @Bean
    public CacheManager noopCacheManager() {
        return new NoOpCacheManager();
    }

    @Bean("redisClient")
    public JedisPooled redisClient() {

        log.info("Initializing redis client");
        JedisClientConfig config = DefaultJedisClientConfig.builder().user("default").password(appConfig.entityCachePassword).blockingSocketTimeoutMillis(60000).build();

        //TODO: Pool config externalize
        GenericObjectPoolConfig<Connection> poolConfig = new GenericObjectPoolConfig<>();
        poolConfig.setMaxTotal(100);
        poolConfig.setMaxIdle(50);
        poolConfig.setMaxWaitMillis(5000);
        poolConfig.setBlockWhenExhausted(true);
        poolConfig.setMinEvictableIdleTimeMillis(1000);
        poolConfig.setSoftMinEvictableIdleTimeMillis(1000);
        poolConfig.setTimeBetweenEvictionRunsMillis(2000);

        // remove this
        log.info("Host {} Port {} User {} Password {}", appConfig.entityCacheHost, appConfig.entityCachePort, appConfig.entityCacheUser);
        if(StringUtils.isEmpty(appConfig.entityCacheUser) && StringUtils.isEmpty(appConfig.entityCachePassword)){
            return new JedisPooled(new PooledConnectionProvider(new HostAndPort(appConfig.entityCacheHost, 6379), config, poolConfig));
        }else {
            log.info("Using Redis Host {} Port {}", appConfig.entityCacheHost, appConfig.entityCachePort);
            return new JedisPooled(new PooledConnectionProvider(new HostAndPort(appConfig.entityCacheHost, appConfig.entityCachePort), config, poolConfig));
        }
    }
}
