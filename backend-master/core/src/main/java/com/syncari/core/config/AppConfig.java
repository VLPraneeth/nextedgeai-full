package com.syncari.core.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Configuration;
import org.springframework.integration.annotation.IntegrationComponentScan;
import java.util.List;

import lombok.Data;

@Data
@Configuration
@IntegrationComponentScan(basePackages = "com.syncari")
public class AppConfig {
	@Value("${event.log.topic.name}")
	String eventLogTopicName;

	@Value("${event.log.subscription.name}")
	String eventLogSubscription;

	@Value("${generic.topic.name}")
	String genericTopicName;
	
	@Value("${generic.subscription.name}")
	String genericSubscription;

	@Value("${webhook.topic.name}")
	String webhookTopicName;

	@Value("${webhook.subscription.name}")
	String webhookSubscription;
	
	@Value("${viper.topic.name}")
	String viperTopicName;
	
	@Value("${viper.subscription.name}")
	String viperSubscription;

	@Value("${spring.cloud.gcp.project-id}")
	String gcpProjectId;

	@Value("${spring.cloud.gcp.cf.project-id}")
	String gcpCfProjectId;
	
	@Value("${spring.cloud.gcp.credentials.encoded-key}")
	String gcpCredentialsKey;

	@Value("${spring.cloud.gcp.cfdeployer.credentials.encoded-key}")
	String cfDeployerCredentialsKey;

	@Value("${spring.cloud.gcp.cfexecutor.credentials.encoded-key}")
	String cfExecutorCredentialsKey;

	@Value("${spring.cloud.gcp.cfexecutor.sa.email}")
	String cfExecutorSAEmail;

    @Value("${cloud.function.endpoint}")
    String cloudFunctionEndPoint;
	
	@Value("${spring.cloud.gcp.profile}")
	String gcpProfile;
	
	@Value("${appconfig.spectrum.server.host}")
	String spectrumServerHost;

	@Value("${notification.pd.email:}")
	String pdEmailAddress;
	
	@Value("${gcs.bucket.name:}")
	String gcsBucketName;

	@Value("${gcs.cf.bucket.name:}")
	String gcsCfBucketName;
	
	@Value("${google.cloud.cdn.host:http://assets.integration.syncari.net}")
	String cloudCdnHost;

	@Value("${clearbit.api.key:}")
	String clearbitApiKey;
	
	@Value("${syncari.slack.client.id:}")
	String slackClientId;
	
	@Value("${syncari.slack.client.secret:}")
	String slackClientSecret;

	@Value("${syncari.slack.synapse.signing.secret}")
	public String slackSynapseSigningSecret;
	
	@Value("${environment.name:dev}")
	String environmentName;

	@Value("#{'${support.email:dev@syncari.com}'.split(';')}")
	List<String> supportEmail;
	
	@Value("#{'${error.support.email:dev@syncari.com}'.split(';')}")
	List<String> errorSupportEmail;

    @Value("#{'${error.email:dev@syncari.com}'.split(';')}")
    List<String> errorEmail;

	@Value("${syncari.datastore.host:}")
	String datastoreHost;

	@Value("${syncari.datastore.public.host:}")
	String datastorePublicHost;
	
	@Value("${syncari.datastore.user:}")
	String datastoreUser;
	
	@Value("${syncari.datastore.password:}")
	String datastorePwd;
	
	@Value("${syncari.datastore.cert:}")
	String datastoreCert;
	
	String datastorePort = "5439";

	@Value("${zendesk.shared.secret}")
	String zendeskSharedSecret;
	
	@Value("${zendesk.sso.secret}")
	String zendeskSSOSecret;

	@Value("${zendesk.support.url}")
	String zendeskSupportUrl;

	@Value("${zendesk.support.url.alias}")
	String zendeskSupportUrlAlias;
	
	@Value("${syncari.salesloft.client.id}")
	public String salesloftClientId;

	@Value("${syncari.salesloft.client.secret}")
	public String salesloftClientSecret;

	@Value("${workramp.internal.privateKey}")
	public String workrampPrivateKey;

	@Value("${workramp.internal.certificate}")
	public String workrampCertificate;

	@Value("${workramp.academies.privateKey}")
	public String academiesPrivateKey;

	@Value("${workramp.academies.certificate}")
	public String academiesCertificate;

	@Value("${workramp.sp.saml.internal.metadata}")
	public String workRampSamlInternalMetadata;

	@Value("${workramp.sp.saml.academies.metadata}")
	public String workRampSamlAcademiesMetadata;
    
    @Value("${syncari.hubspot.client.id}")
	public String hubspotClientId;

	@Value("${syncari.hubspot.client.secret}")
	public String hubspotClientSecret;

    @Value("${syncari.gsuite.client.id}")
	public String gsuiteClientId;

	@Value("${syncari.gsuite.client.secret}")
	public String gsuiteClientSecret;
	
	@Value("${error.notification.topic.name}")
	public String errorNotificationTopicName;

	@Value("${dfi.result.notification.topic.name}")
	public String dfiResultNotificationTopicName;

	@Value("${error.notification.subscription.name}")
	public String errorNotificationSubscription;

	@Value("${dfi.result.notification.subscription.name}")
	public String dfiResultNotificationSubscription;

	@Value("${spring.redis.host}")
	public String redisHost;

	@Value("${spring.redis.port}")
	public int redisPort;

	@Value("${spring.redis.user}")
	public String redisUser;

	@Value("${spring.redis.password}")
	public String redisPassword;

	@Value("${spring.newredis.host}")
	public String redisNewHost;

	@Value("${spring.newredis.port}")
	public int redisNewPort;

	@Value("${spring.newredis.user}")
	public String redisNewUser;

	@Value("${spring.newredis.password}")
	public String redisNewPassword;


	@Value("${entitycache.redis.host}")
	public String entityCacheHost;

	@Value("${entitycache.redis.port}")
	public int entityCachePort;

	@Value("${entitycache.redis.user}")
	public String entityCacheUser;

	@Value("${entitycache.redis.password}")
	public String entityCachePassword;
	
	@Value("${proxy.enabled}")
	private boolean proxyEnabled;
	
	@Value("${proxy.host}")
	private String proxyHost;
	
	@Value("${proxy.port}")
	private int proxyPort;

	@Value("${syncari.msteams.client.id}")
	String msTeamsClientId;

	@Value("${syncari.msteams.client.secret}")
	String msTeamsClientSecret;

	@Value("${NEXTEDGE_JWT_SECURITY_SECRET:${syncari.jwt.security.secret:}}")
	String jwtSecret;
	
	@Value("${webhook.server.host}")
	String webhookServerHost;

	@Value("${webhook.karibu.server.host}")
	String webhookKaribuServerHost;

	@Value("${syncari.env.name:non-prod}")
	String envName;

	@Value("${cf.vpc.connector:us-west2-connector}")
	String cfVpcConnector;
	
	

	@Value("${gcp.ts.bgsa.credentials.key}")
	String tsBqSaKey;

}
