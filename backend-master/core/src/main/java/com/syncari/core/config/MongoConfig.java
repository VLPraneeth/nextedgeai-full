package com.syncari.core.config;

import com.google.common.base.Preconditions;
import com.mongodb.*;
import com.mongodb.client.MongoDatabase;
import com.syncari.core.SyncariContext;
import com.syncari.core.model.Cluster;
import com.syncari.core.model.Resource;
import com.syncari.core.model.ResourceType;
import com.syncari.core.repositories.customer.BigDecimalCustomCodec;
import com.syncari.core.repositories.customer.DatatypeConverter;
import com.syncari.core.repositories.customer.ExternalIdReadConverter;
import com.syncari.core.repositories.customer.ExternalIdWriteConverter;
import com.syncari.core.service.ClusterService;
import com.syncari.core.service.EncryptionService;
import com.syncari.core.utils.CustomDBRefResolver;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.bson.codecs.configuration.CodecRegistries;
import org.bson.codecs.configuration.CodecRegistry;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.cache.CacheManager;
import org.springframework.context.annotation.*;
import org.springframework.dao.DataAccessException;
import org.springframework.data.mongodb.MongoDbFactory;
import org.springframework.data.mongodb.MongoTransactionManager;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.SimpleMongoDbFactory;
import org.springframework.data.mongodb.core.convert.DbRefResolver;
import org.springframework.data.mongodb.core.convert.MappingMongoConverter;
import org.springframework.data.mongodb.core.convert.MongoCustomConversions;
import org.springframework.data.mongodb.core.mapping.MongoMappingContext;
import org.springframework.integration.transaction.PseudoTransactionManager;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

@Slf4j
@Configuration
public class MongoConfig {
	private static final String CLUSTER_ID = "clusterId";

	@Value("${spring.data.mongodb.port}")
	private String port;

	@Value("${spring.data.mongodb.host}")
	private String host;
	
	@Value("${spring.data.mongodb.uri}")
	private String uri;

	@Value("${spring.data.mongodb.readOnlyUri}")
	private String readOnlyUri;
	
	@Autowired
	DatatypeConverter dtConverter;

	@Autowired
	ZonedDateTimeReadConverter zonedDateTimeReadConverter;

	@Autowired
	ZonedDateTimeWriteConverter zonedDateTimeWriteConverter;

    @Autowired
    SqlDateReadConverter sqlDateReadConverter;

    @Autowired
    SqlDateWriteConverter sqlDateWriteConverter;

	@Autowired
	ExternalIdReadConverter externalIdReadConverter;

	@Autowired
	ExternalIdWriteConverter externalIdWriteConverter;

	@Autowired
	LocalDateReadConverter localDateReadConverter;

	@Autowired
	LocalDateWriteConverter localDateWriteConverter;

	@Autowired
	ExternalValueReadConverter externalValueReadConverter;

	MongoClient roMongoClient;
	MongoClient rwMongoClient;
	Map<String, MongoClient> mongoClientMap = new ConcurrentHashMap<>();
	
	@Autowired
	@Lazy
	ClusterService clusterService;
	
	@Autowired
	EncryptionService encryptionService;

	@Profile("!development & !ci")
	@Bean(name="customerTransactionManager")
	@Primary
	public MongoTransactionManager customerTransactionManager(@Qualifier("customerDBFactory") MongoDbFactory dbFactory) {
		return new MongoTransactionManager(dbFactory);
	}

	@Profile("development | ci")
	@Bean(name="customerTransactionManager")
	public PseudoTransactionManager noopTransactionManager(@Qualifier("customerDBFactory") MongoDbFactory dbFactory) {
		return new PseudoTransactionManager();
	}

	@Bean
	public MongoTransactionManager syncariTransactionManager(@Qualifier("syncariDBFactory") MongoDbFactory dbFactory) {
		return new MongoTransactionManager(dbFactory);
	}

	@Bean(name="syncariDBFactory")
	public MongoDbFactory syncariDBFactory() throws Exception {
		return new SimpleMongoDbFactory(retrieveSyncariMongoClient(false), "syncaridb");
	}

	@Bean(name = "syncariMongoTemplate")
    public MongoTemplate primaryMongoTemplate() throws Exception {
        return createTemplateWithConverters(syncariDBFactory());
    }

    @Bean(name = "customerMongoTemplate")
    public MongoTemplate customerMongoTemplate() throws Exception {
        return createTemplateWithConverters(customerDBFactory());
    }

	@Bean(name = "secondaryReaderCustomerMongoTemplate")
	public MongoTemplate secondaryReaderCustomerMongoTemplate() throws Exception {
		final MongoTemplate mongoTemplate = createTemplateWithConverters(customerDBFactory());
		final MongoTemplate secondaryReader = new MongoTemplate(mongoTemplate.getMongoDbFactory(), mongoTemplate.getConverter());
		secondaryReader.setReadPreference(ReadPreference.secondary());
		return secondaryReader;
	}


	@Bean(name="customerDBFactory")
	public MongoDbFactory customerDBFactory() throws Exception {
		return new SimpleMongoDbFactory(retrieveMongoClient(false), "defaultdb") {
			@Override
			public MongoDatabase getDb() throws DataAccessException {
				Optional<Resource> resource = SyncariContext.getInstance().getResource(ResourceType.DATABASE);
				Preconditions.checkArgument(resource.isPresent(),"Missing Database Resource for "+SyncariContext.getInstance());
				return super.getDb(resource.get().getConfiguration().get("database"));
			}

			@Override
			protected MongoClient getMongoClient() {
				return retrieveMongoClient(SyncariContext.isReadOnlyOp());
			}
		};
	}

	@Bean
	public MongoClient mongoClient() throws Exception {
		return retrieveMongoClient(false);
	}
	
	public MongoClient retrieveMongoClient(boolean readOnly){
		String hostUrl = uri;
		String readHostUrl = readOnlyUri;
		if(SyncariContext.getInstance() != null) {
			Optional<Resource> resource = SyncariContext.getInstance().getResource(ResourceType.DATABASE);
			if(resource.isPresent()) {
				String clusterId = resource.get().getConfiguration().getOrDefault(CLUSTER_ID, "");
				if(!StringUtils.isBlank(clusterId)) {
					Optional<Cluster> cluster = clusterService.findById(clusterId);
					if(cluster.isPresent()) {
						Cluster c = cluster.get();
						Optional<Integer> optPort = c.getPort() == null ? Optional.empty() : Optional.of(c.getPort());
						hostUrl = constructUri(c.getHost(), c.getUser(), c.getPassword(), optPort);
						readHostUrl = constructUri(c.getHost(), c.getReadOnlyUser(), c.getReadOnlyPassword(), optPort);
						if(StringUtils.isBlank(hostUrl)) {
							throw new RuntimeException("Invalid database cluster setup");
						}
					}
				}
			}
		}
		if(readOnly){
			final String readUri = readHostUrl;
			mongoClientMap.computeIfAbsent(readUri, v -> createMongoClient(readUri));
			return mongoClientMap.get(readUri);
		}
		final String hostUri = hostUrl;
		mongoClientMap.computeIfAbsent(hostUri, v -> createMongoClient(hostUri));
		return mongoClientMap.get(hostUri);
	}
	
	private MongoClient retrieveSyncariMongoClient(boolean readOnly) {
		// Syncaridb is in the main cluster
		if (readOnly) {
			roMongoClient = roMongoClient == null ? createMongoClient(readOnlyUri) : roMongoClient;
			return roMongoClient;
		}
		rwMongoClient = rwMongoClient == null ? createMongoClient(uri) : rwMongoClient;
		return rwMongoClient;
	}

	private MongoClient createMongoClient(String mongoUri){
		CodecRegistry registry = CodecRegistries.fromRegistries(
				CodecRegistries.fromCodecs(new BigDecimalCustomCodec()),
				MongoClient.getDefaultCodecRegistry()
		);
		MongoClientOptions.Builder options = MongoClientOptions.builder().codecRegistry(registry);
		if(!StringUtils.isBlank(mongoUri)) {
			return new MongoClient(new MongoClientURI(mongoUri, MongoClientOptions.builder().codecRegistry(registry)));
		}
		return new MongoClient(new ServerAddress(host,Integer.parseInt(port)), options.build());
	}

	private MongoTemplate createTemplateWithConverters(MongoDbFactory mongoDbFactory) throws Exception {
		DbRefResolver dbRefResolver = new CustomDBRefResolver(mongoDbFactory);
		MongoCustomConversions conversions = new MongoCustomConversions(List.of(dtConverter, zonedDateTimeReadConverter, zonedDateTimeWriteConverter,
				sqlDateReadConverter, sqlDateWriteConverter, externalIdReadConverter,externalIdWriteConverter, externalValueReadConverter));

		MongoMappingContext mappingContext = new MongoMappingContext();
		mappingContext.setSimpleTypeHolder(conversions.getSimpleTypeHolder());
		mappingContext.afterPropertiesSet();

		MappingMongoConverter converter = new MappingMongoConverter(dbRefResolver, mappingContext);
		converter.setCustomConversions(conversions);
		converter.afterPropertiesSet();
		converter.setMapKeyDotReplacement("__dot__");

		return new MongoTemplate(mongoDbFactory, converter);
	}
	
	private String constructUri(String host, String user, String password, Optional<Integer> port) {
		String portStr = port.isPresent() ? ":"+port.get() : "";
		if(StringUtils.isBlank(user) || StringUtils.isBlank(password)) {
			return String.format("mongodb://%s%s", host, portStr);
		}
		return String.format("mongodb+srv://%s:%s@%s%s/test?retryWrites=true", user, encryptionService.decrypt(password), host, portStr);
	}
	
}
