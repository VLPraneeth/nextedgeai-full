package com.syncari.karibu.rest.controllers;

import de.flapdoodle.embed.mongo.MongodExecutable;
import de.flapdoodle.embed.mongo.MongodProcess;
import de.flapdoodle.embed.mongo.config.IMongodConfig;
import de.flapdoodle.embed.process.config.IRuntimeConfig;
import de.flapdoodle.embed.process.distribution.BitSize;
import de.flapdoodle.embed.process.distribution.Distribution;
import de.flapdoodle.embed.process.distribution.Platform;
import de.flapdoodle.embed.process.extract.IExtractedFileSet;
import de.flapdoodle.embed.process.runtime.Starter;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.mongo.MongoProperties;
import org.springframework.boot.autoconfigure.mongo.embedded.EmbeddedMongoAutoConfiguration;
import org.springframework.boot.autoconfigure.mongo.embedded.EmbeddedMongoProperties;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.ApplicationContext;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;
import org.springframework.core.env.MapPropertySource;
import org.springframework.core.env.MutablePropertySources;
import org.springframework.core.env.PropertySource;

import java.io.IOException;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;

@Profile(value = "development")
@Configuration
@EnableConfigurationProperties({MongoProperties.class, EmbeddedMongoProperties.class})
public class SingleEmbeddedMongoConfiguration extends EmbeddedMongoAutoConfiguration {

    private static IMongodConfig iMongodConfig;
    private static MongodExecutable mongodExecutable;
    private final MongoProperties properties;
    private final IRuntimeConfig runtimeConfig;
    private final ApplicationContext context;

    public SingleEmbeddedMongoConfiguration(MongoProperties properties,
                                            EmbeddedMongoProperties embeddedProperties,
                                            ApplicationContext context,
                                            IRuntimeConfig runtimeConfig) {
        super(properties, embeddedProperties, context, runtimeConfig);

        this.properties = properties;
        this.context = context;
        this.runtimeConfig = runtimeConfig;
    }

    @Bean(initMethod = "start", destroyMethod = "stop")
    @ConditionalOnMissingBean
    @Override
    public MongodExecutable embeddedMongoServer(IMongodConfig mongodConfig) throws IOException {
        Integer configuredPort = this.properties.getPort();
        if (configuredPort == null || configuredPort == 0) {
            setEmbeddedPort(mongodConfig.net().getPort());
        }
        if (mongodExecutable != null) {
            return mongodExecutable;
        }
        SingleInstanceMongodStarter singleInstanceMongodStarter = new SingleInstanceMongodStarter(this.runtimeConfig);
        mongodExecutable = singleInstanceMongodStarter.prepare(mongodConfig);
        return mongodExecutable;
    }

    @Bean
    @ConditionalOnMissingBean
    @Override
    public IMongodConfig embeddedMongoConfiguration() throws IOException {
        if (iMongodConfig != null) {
            return iMongodConfig;
        }
        iMongodConfig = super.embeddedMongoConfiguration();
        return iMongodConfig;
    }

    private void setEmbeddedPort(int port) {
        setPortProperty(this.context, port);
    }

    private void setPortProperty(ApplicationContext currentContext, int port) {
        if (currentContext instanceof ConfigurableApplicationContext) {
            MutablePropertySources sources = ((ConfigurableApplicationContext) currentContext)
                    .getEnvironment().getPropertySources();
            getMongoPorts(sources).put("local.mongo.port", port);
        }
        if (currentContext.getParent() != null) {
            setPortProperty(currentContext.getParent(), port);
        }
    }

    private Map<String, Object> getMongoPorts(MutablePropertySources sources) {
        PropertySource<?> propertySource = sources.get("mongo.ports");
        if (propertySource == null) {
            propertySource = new MapPropertySource("mongo.ports",
                    new HashMap<>());
            sources.addFirst(propertySource);
        }
        return (Map<String, Object>) propertySource.getSource();
    }

    private class SingleInstanceMongodStarter extends Starter<IMongodConfig,MongodExecutable,MongodProcess> {

        public SingleInstanceMongodStarter(IRuntimeConfig config) {
            super(config);
        }

        @Override
        public MongodExecutable prepare(IMongodConfig config) {
            Distribution distribution = new Distribution(config.version(), Platform.detect(), BitSize.B64);
            return prepare(config, distribution);
        }

        @Override
        protected MongodExecutable newExecutable(IMongodConfig config, Distribution distribution, IRuntimeConfig runtime, IExtractedFileSet exe) {
            try {
                return new SingleInstanceMongodExecutable(config, runtime);
            } catch (IOException e) {
                throw new RuntimeException(e);
            }
        }
    }

    private class SingleInstanceMongodExecutable extends MongodExecutable {

        private MongodProcess process;
        private final AtomicInteger counter = new AtomicInteger(0);

        public SingleInstanceMongodExecutable(IMongodConfig mongodConfig, IRuntimeConfig runtimeConfig) throws IOException {
            super(
                    new Distribution(mongodConfig.version(), Platform.detect(), BitSize.B64),
                    mongodConfig,
                    runtimeConfig,
                    runtimeConfig.getArtifactStore().extractFileSet(new Distribution(mongodConfig.version(), Platform.detect(), BitSize.B64))
            );
        }

        @Override
        protected MongodProcess start(Distribution distribution, IMongodConfig config, IRuntimeConfig runtime) throws IOException {
            synchronized (counter) {
                if (counter.get() > 0) {
                    counter.incrementAndGet();
                    return process;
                }
                process = super.start(distribution, config, runtime);
                counter.incrementAndGet();
                return process;
            }
        }

        @Override
        public synchronized void stop() {
            synchronized (counter) {
                if (counter.decrementAndGet() == 0) {
                    super.stop();
                }
            }
        }
    }
}