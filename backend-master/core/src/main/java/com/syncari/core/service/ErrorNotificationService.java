package com.syncari.core.service;

import com.syncari.core.GlobalConstants;
import com.syncari.core.SyncariContext;
import com.syncari.core.config.AppConfig;
import com.syncari.core.event.EventTypes;
import com.syncari.core.event.Publisher;
import com.syncari.core.model.*;
import com.syncari.core.model.errornotification.TestRequest;
import com.syncari.core.model.errornotification.WebhookRequest;
import com.syncari.core.model.errornotification.WebhookRequestBodyNotification;
import com.syncari.core.model.misc.*;
import com.syncari.core.notification.NotificationChannelSeed;
import com.syncari.core.repositories.customer.*;
import com.syncari.core.repositories.syncari.UserRepo;
import com.syncari.core.template.TemplateRenderer;
import com.syncari.core.utils.ValidationUtils;
import com.syncari.utils.Pair;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.collections.CollectionUtils;
import org.apache.commons.lang.StringUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;
import org.springframework.stereotype.Component;
import org.springframework.web.client.HttpClientErrorException;

import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.*;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

import static com.syncari.core.model.ErrorNotificationFrequency.*;
import static com.syncari.core.model.misc.ErrorNotificationChannelType.email;
import static com.syncari.core.model.misc.ErrorNotificationChannelType.webhook;
import static com.syncari.core.model.misc.ErrorNotificationConfigStatus.Active;
import static com.syncari.core.utils.ValidationUtils.validateCondition;
import static com.syncari.utils.I18n.i18n;

@Slf4j
@Component
public class ErrorNotificationService {
	
	private static final String SINGLE_ERROR_TEMPLATE_PATH = "templates/error.notification.single.template";
	private static final String DIGEST_ERROR_TEMPLATE_PATH = "templates/error.notification.digest.template";
	private static final String VIEW_ALL_NOTIFICATION_URL = "%s/notifications";
	private static final String NOTIFICATION_SETTINGS_URL = "%s/profile/notifications";
	private static final String SUPPORT_EMAIL_ID = "supportalerts+%s@syncari.com";
	private static final Integer PAGE_SIZE = 100;

	@Autowired
	private ErrorCatalogRepo catalogRepo;
	@Autowired
	private ErrorNotificationRepo notificationRepo;
	@Autowired
	private Publisher publisher;
	@Autowired
	private UserPreferenceRepo preferenceRepo;
	@Autowired
	private ErrorNotificationUserInfoRepo userInfoRepo;
	@Autowired
    TemplateRenderer renderer;
	@Autowired
    @Qualifier("defaultEmailService")
	private EmailService emailService;
	@Autowired
	private AppConfig appConfig;
	@Autowired
	private UserRepo userRepo;
	@Autowired
	private ErrorNotificationConfigRepo configRepo;
	@Autowired
	private ErrorNotificationWebhookHelper webhookHelper;
	@Autowired
	private ErrorNotificationEmailHelper emailHelper;
	@Autowired
	private ThreadPoolTaskExecutor exec;
	@Autowired
	private ErrorNotificationInvitationRepo invitationRepo;
	@Autowired
    private SubscriptionService subService;
	@Autowired
	private UserService userService;

	public List<ErrorCatalog> getErrorCatalogs() {
		return catalogRepo.findByActive(true);
	}

	@Deprecated
	public List<NotificationChannel> getChannels() {
		return NotificationChannelSeed.allChannels();
	}

	public boolean sendErrorNotification(ErrorCategory category, ErrorPriority priority, String componentId,
			String subject, String body) {
		return sendErrorNotification(category, priority, componentId, subject, body, Map.of(), null);
	}
	
	public boolean sendErrorNotification(ErrorCategory category, ErrorPriority priority, String componentId,
			String subject, String body, Throwable ex) {
		return sendErrorNotification(category, priority, componentId, subject, body, Map.of(), ex);
	}

	public boolean sendErrorNotification(ErrorCategory category, ErrorPriority priority, String componentId,
			String subject, String body, Map<String, String> details, Throwable ex) {
		if (category == null || priority == null || componentId == null) {
			return false;
		}
		Event event = new Event().setType(EventTypes.ERROR_NOTIFICATION)
				.setDetails(Map.of("category", category, "componentId", componentId, "priority", priority, "key",
						category.name() + "_" + priority.name() + "_" + componentId, "subject", subject, "body", body,
						"details", details != null ? details : Map.of()));
		publisher.publishToErrorNotificationQueue(event);
		return true;
	}

	public boolean processErrorNotification(ErrorNotification notification) {
		if (notification == null) {
			return false;
		}
		ErrorNotification notif = notificationRepo.save(notification);
		var catalogList = catalogRepo.findById(notification.getCatalogId());
		if (catalogList.isEmpty()) {
			return false;
		}
		ErrorCatalog catalog = catalogList.get();
		List<ErrorNotificationConfig> configList = configRepo.findByStatusAndCadence(Active, IMMEDIATE).stream()
				.filter(config -> CollectionUtils.isNotEmpty(config.getNotificationTypes())
						&& config.getNotificationTypes().contains(notification.getCatalogId()))
				.collect(Collectors.toList());
		processWebhook(configList.stream().filter(config -> config.getType() == webhook).collect(Collectors.toList()),
				List.of(notification));
		processEmail(configList.stream().filter(config -> config.getType() == email).collect(Collectors.toList()),
				List.of(notification));
		//For backward compatibility
		//return processEmailFromPreference(notif, catalog);
		return true;
	}

	public void processDelayedErrorNotification() {
		log.info("Processing of ErrorNotification started for {}", SyncariContext.getSyncariId());
		List<ErrorNotificationConfig> configList = configRepo.findByStatusAndCadenceIn(Active,
				List.of(HOURLY, DAILY, WEEKLY, MONTHLY));
		configList.forEach(config -> {
			if (shouldSendNotification(config.getCadence(), config.getLastNotificationTimestamp())) {
				if (CollectionUtils.isNotEmpty(config.getNotificationTypes())) {
					int page = 0;
					while (true) {
						PageRequest pageRequest = PageRequest.of(page, PAGE_SIZE, Sort.by("_id"));
						List<ErrorNotification> notifications;

						if (config.getLastNotificationTimestamp() == null) {
							notifications = notificationRepo.findByCatalogIdIn(config.getNotificationTypes(), pageRequest);
						} else {
							notifications = notificationRepo.findByCatalogIdInAndCreatedAtGreaterThanEqual(
									config.getNotificationTypes(), config.getLastNotificationTimestamp(), pageRequest);
						}
						if (CollectionUtils.isEmpty(notifications)) {
							break; // exit loop when no more data
						}
						processErrorNotifications(notifications, config);
						page++;
					}
				}
			}
		});
		log.info("Processing of ErrorNotification completed for {}", SyncariContext.getSyncariId() );
	}

	private void processErrorNotifications(List<ErrorNotification> errorNotifications, ErrorNotificationConfig config){
		if (CollectionUtils.isNotEmpty(errorNotifications)) {
			if (config.getType() == webhook) {
				processWebhook(List.of(config), errorNotifications);
			} else if (config.getType() == email) {
				processEmail(List.of(config), errorNotifications);
			}
		}
	}

	private void processEmail(List<ErrorNotificationConfig> configList, List<ErrorNotification> notification) {
		configList.forEach(config -> {
			var configFromDb = configRepo.findById(config.getId()); // Reloaded to check processing flag
			if(configFromDb.isEmpty() || configFromDb.get().isProcessing()) {
				log.info("Skipping execution for email config {}", config.getName());
			} else {
				log.debug("Trigerring execution for email config {}", config.getName());
				ErrorNotificationConfig conf = configRepo.save(configFromDb.get().setProcessing(true));
				var org = SyncariContext.getOrganziation();
				var ins = SyncariContext.getInstance();
				var user = SyncariContext.getUser();
				exec.execute(() -> {
					SyncariContext.runWithContext(org, ins, user, () -> {
						emailHelper.sendEmail((ErrorNotificationEmailConfig) conf, notification);
					});
				});
				
			}
		});
		
	}

	private void processWebhook(List<ErrorNotificationConfig> configList, List<ErrorNotification> notification) {
		List<WebhookRequestBodyNotification> notifs = notification.stream()
			.limit(15)
			.map(n -> {
				return WebhookRequestBodyNotification.builder()
				.timestamp(n.getCreatedAt())
				.summary(n.getSubject())
				.message(n.getBody())
				.build();
			}).collect(Collectors.toList());
		configList.forEach(config -> {
			var configFromDb = configRepo.findById(config.getId()); // Reloaded to check processing flag
			if(configFromDb.isEmpty() || configFromDb.get().isProcessing()) {
				log.info("Skipping execution for webhook config {}", config.getName());
			} else {
				ErrorNotificationConfig conf = configRepo.save(configFromDb.get().setProcessing(true));
				var org = SyncariContext.getOrganziation();
				var ins = SyncariContext.getInstance();
				var user = SyncariContext.getUser();
				exec.execute(() -> {
					SyncariContext.runWithContext(org, ins, user, () -> {
						webhookHelper.execute((ErrorNotificationWebhookConfig) conf, notifs, notification.size(), new Date());
					});
				});
			}
		});
		
	}

	@Deprecated
	private boolean processEmailFromPreference(ErrorNotification notif, ErrorCatalog catalog) {
		List<UserPreference> preferences = preferenceRepo.findByErrorNotificationNotNull();
		if (preferences.isEmpty()) {
			return false;
		}
		preferences.forEach(pref -> {
			sendNotification(pref.getErrorNotification(), notif, catalog, pref.getUserId());
		});
		return true;
	}

	@Deprecated
	private void sendNotification(ErrorNotificationPreference pref, ErrorNotification notif, ErrorCatalog catalog,
			String userId) {
		List<ErrorSubscription> subs = pref.getSubscriptions().stream()
				.filter(sub -> sub.isActive() && sub.getCatalogId().equals(catalog.getId()))
				.collect(Collectors.toList());
		subs.forEach(sub -> {
			if (shouldSendNotification(notif, sub.getFrequency(), userId)) {
				sub.getChannels().forEach(channelType -> {
					getChannelConfiguration(pref, channelType).ifPresent(channel -> {
						switch (channelType) {
						case "email":
							sendEmail(sub, channel, notif, userId);
							userInfoRepo.save(
									ErrorNotificationUserInfo.builder().key(notif.getKey()).userId(userId).build());
							break;
						default:
							log.error("Unsupported channelType {}", channelType);
							break;
						}
					});
				});

			} else {
				log.error("Skipping notification {} due to frequency {}", notif.getDetails(), sub.getFrequency());
			}
		});

	}

	@Deprecated
	private Optional<ErrorChannelConfiguration> getChannelConfiguration(ErrorNotificationPreference pref,
			String channelType) {
		return pref.getChannelConfigurations().stream().filter(channel -> channelType.equals(channel.getType()))
				.findFirst();
	}

	@Deprecated
	private void sendEmail(ErrorSubscription sub, ErrorChannelConfiguration channel, ErrorNotification notif, String userId) {
		String body = "";
		String subject = "Your Syncari instance '%s'(%s) in subscription '%s' has %s notification(s)";
		String cc = String.format(SUPPORT_EMAIL_ID, SyncariContext.getSyncariId());
		Map<String, Object> context = new HashMap<>();
		context.put("syncariLogoUrl", String.format(GlobalConstants.SYNCARI_LOGO, appConfig.getCloudCdnHost()));
		userRepo.findById(userId).ifPresent(user -> {
			if(StringUtils.isNotBlank(user.getFirstName()) && StringUtils.isNotBlank(user.getLastName())) {
				context.put("name", user.getFirstName() + " " + user.getLastName());
			}
			if(StringUtils.isNotBlank(user.getTimeZone())) {
				context.put("tz", user.getTimeZone());
			}
		});
		context.put("allNotificationsUrl", String.format(VIEW_ALL_NOTIFICATION_URL, appConfig.getSpectrumServerHost()));
		context.put("manageNotificationsUrl", String.format(NOTIFICATION_SETTINGS_URL, appConfig.getSpectrumServerHost()));
		Optional<ErrorNotificationUserInfo> userInfo = userInfoRepo.findLatestNotifForUserByKey(notif.getKey(), userId);
		//if(sub.getFrequency() == ErrorNotificationFrequency.IMMEDIATE || userInfo.isEmpty()) {
		if(userInfo.isEmpty()) {
			context.put("notification", notif);
			body = renderer.render(SINGLE_ERROR_TEMPLATE_PATH, context);
			subject = String.format(subject, SyncariContext.getInstance().getName(), SyncariContext.getInstance().getSyncariId(), SyncariContext.getOrganziation().getName(), "1");
		} else {
			List<ErrorNotification> notifList = notificationRepo.findByKeyAndCreatedAtGreaterThanEqual(notif.getKey(), userInfo.get().getCreatedAt());
			log.info("ErrorNotification count {}", notifList.size());
			log.info("ErrorNotification {}", notifList);
			context.put("lastDigestDate", userInfo.get().getCreatedAt());
			context.put("notificationsCount", notifList.size());
			if(notifList.size() > 15) {
				context.put("notification", notifList.subList(0, 15));
				context.put("additionalNotificationsCount", notifList.size() - 15);
				context.put("truncated", true);
			} else {
				context.put("notification", notifList);
				context.put("truncated", false);
			}
			body = renderer.render(DIGEST_ERROR_TEMPLATE_PATH, context);
			subject = String.format(subject, SyncariContext.getInstance().getName(), notifList.size());
		}
		Set<String> emails = (Set<String>) channel.getConfiguration().get("emails");
		log.debug("Sending email notification to {} cc {} ", emails, cc);
		log.debug("Subject {}", subject);
		log.debug("Message {}", body);
		if(emails != null && !emails.isEmpty()) {
			emailService.sendHtml(List.copyOf(emails), List.of(cc), subject, body);
		}

	}

	@Deprecated
	private boolean shouldSendNotification(ErrorNotification notification, ErrorNotificationFrequency frequency,
			String userId) {
		Optional<ErrorNotificationUserInfo> lastNotification = userInfoRepo.findLatestNotifForUserByKey(notification.getKey(),
				userId);
		if (lastNotification.isEmpty())
			return true;
		long timeGapMillis = Instant.now().toEpochMilli() - lastNotification.get().getCreatedAt().getTime();
		
		switch (frequency){
		case IMMEDIATE:
			//return true;
			return TimeUnit.MILLISECONDS.toDays(timeGapMillis) >= 1; //Temp changed to make it behave
		case HOURLY:
			return TimeUnit.MILLISECONDS.toHours(timeGapMillis) >= 1;
		case DAILY:
			return TimeUnit.MILLISECONDS.toDays(timeGapMillis) >= 1;
		case WEEKLY:
			return TimeUnit.MILLISECONDS.toDays(timeGapMillis) >= 7;
		case MONTHLY:
			return TimeUnit.MILLISECONDS.toDays(timeGapMillis) >= 30;

		default:
			log.warn("Unhandled NotificationFrequency {}", frequency);
			return false;
	}
	}
	
	private boolean shouldSendNotification(ErrorNotificationFrequency frequency, Date lastNotificationTimestamp) {
		if (lastNotificationTimestamp == null) {
			return true;
		}
		long timeGapMillis = Instant.now().toEpochMilli() - lastNotificationTimestamp.getTime();

		switch (frequency) {
		case HOURLY:
			return TimeUnit.MILLISECONDS.toHours(timeGapMillis) >= 1;
		case DAILY:
			return TimeUnit.MILLISECONDS.toDays(timeGapMillis) >= 1;
		case WEEKLY:
			return TimeUnit.MILLISECONDS.toDays(timeGapMillis) >= 7;
		case MONTHLY:
			return TimeUnit.MILLISECONDS.toDays(timeGapMillis) >= 30;
		default:
			log.warn("Unhandled NotificationFrequency {}", frequency);
			return false;
		}
	}
	
	public List<ErrorNotificationConfig> getErrorNotificationConfig() {
		return configRepo.findAll();
	}
	
	public ErrorNotificationConfig getErrorNotificationConfig(String id) {
		ValidationUtils.validateCondition(StringUtils.isEmpty(id), "Id cannot be empty");
		var config = configRepo.findById(id);
		ValidationUtils.validateCondition(config.isEmpty(), "Id does not exist");
		return config.get();
		
	}
	
	public void deleteErrorNotificationConfig(String id) {
		ValidationUtils.validateCondition(StringUtils.isEmpty(id), "Id cannot be empty");
		configRepo.deleteById(id);
	}

	public ErrorNotificationConfig saveErrorNotificationConfig(ErrorNotificationConfig config) {
		validateCondition(StringUtils.isEmpty(config.getName()), i18n("error_notification_invalid_name", config.getName()));
		config.validate();
		if(StringUtils.isNotEmpty(config.getId())) {
			var byName = configRepo.findByName(config.getName());
			validateCondition(byName.isPresent() && !byName.get().getId().equals(config.getId()), i18n("error_notification_name_exist", config.getName()));
			var fromDb = configRepo.findById(config.getId());
			if(fromDb.isPresent()) {
				var configFromDb = fromDb.get();

				// Check if status is being changed from Disabled to Active
				// If so, reset retry counter to give the user a fresh start
				boolean wasDisabled = configFromDb.getStatus() == ErrorNotificationConfigStatus.Disabled;
				boolean isNowActive = config.getStatus() == ErrorNotificationConfigStatus.Active;

				configFromDb.copyFrom(config);

				// Set retries AFTER copyFrom() so it's not overwritten
				if (wasDisabled && isNowActive) {
					log.info("Resetting retry counter for config '{}' as it's being re-enabled from Disabled to Active", config.getName());
					configFromDb.setRetries(0);
				}

				config = configRepo.save(configFromDb);
			}
		} else {
			validateCondition(configRepo.findByName(config.getName()).isPresent(), i18n("error_notification_name_exist", config.getName()));
			config.setLastNotificationTimestamp(new Date());
			config = configRepo.save(config);
		}
		if(config instanceof ErrorNotificationEmailConfig) {
			var emailConfig = (ErrorNotificationEmailConfig) config;
			emailConfig.getEmails().forEach(emailEntry -> {
				if(emailEntry.getStatus() == null || emailEntry.getStatus() == EmailConfigStatus.Inactive) { // new email
					if(emailConfig.getStatus() == ErrorNotificationConfigStatus.Inactive) {
						emailEntry.setStatus(EmailConfigStatus.Inactive);
					} else {
						var invitation = ErrorNotificationInvitation
								.builder()
								.email(emailEntry.getEmail())
								.configId(emailConfig.getId())
								.invitationId(UUID.randomUUID().toString())
								.build();
						invitation = invitationRepo.save(invitation);
						emailHelper.sendOptInEmail(
								userRepo.findByEmail(emailEntry.getEmail()).orElse(new User(emailEntry.getEmail(), "")),
								invitation.getInvitationId(), emailConfig, SyncariContext.getSyncariId());
						emailEntry.setStatus(EmailConfigStatus.Pending);
					}
				}
			});
			config = configRepo.save(config);
		}
		return config;
	}
	
	public ErrorNotificationConfig sendOptin(String id,  String email) {
		var config = configRepo.findById(id);
		ValidationUtils.validateCondition(config.isEmpty(), "Id does not exist");
		ValidationUtils.validateCondition(!(config.get() instanceof ErrorNotificationEmailConfig), "Not a valid configuration");
		var emailConfig = (ErrorNotificationEmailConfig) config.get();
		var emailEntry = emailConfig.getEmails().stream().filter(e -> e.getEmail().equals(email)).findFirst();
		ValidationUtils.validateCondition(emailEntry.isEmpty(), "Not a valid configuration");
		invitationRepo.deleteByConfigIdAndEmail(id, email);
		var invitation = ErrorNotificationInvitation
				.builder()
				.email(emailEntry.get().getEmail())
				.configId(emailConfig.getId())
				.invitationId(UUID.randomUUID().toString())
				.build();
		invitation = invitationRepo.save(invitation);
		emailHelper.sendOptInEmail(
				userRepo.findByEmail(email).orElse(new User(email, "")),
				invitation.getInvitationId(), emailConfig, SyncariContext.getSyncariId());
		emailEntry.get().setStatus(EmailConfigStatus.Pending);
		emailConfig = configRepo.save(emailConfig);
		return emailConfig;
	}

	public Pair<? extends Object, ? extends Object> test(TestRequest request) {
		if (request.getType() == ErrorNotificationChannelType.webhook) {
			try {
				ErrorNotificationWebhookConfig errorNotificationWebhookConfig = new ErrorNotificationWebhookConfig();
				errorNotificationWebhookConfig.setHttpMethod((String) request.getConfiguration().get("httpMethod")).setUrl((String) request.getConfiguration().get("url"));
				errorNotificationWebhookConfig.validate();
				WebhookRequest webhookReq = WebhookRequest.builder()
						.body((String) request.getConfiguration().get("body"))
						.headers((Map) request.getConfiguration().get("headers"))
						.method(HttpMethod.valueOf((String) request.getConfiguration().get("httpMethod")))
						.url((String) request.getConfiguration().get("url")).build();
				return webhookHelper.execute(webhookReq);
			} catch (HttpClientErrorException e) {
				log.error("Test execution error ", e);
				if(e.getStatusCode() == HttpStatus.PROXY_AUTHENTICATION_REQUIRED) {
					return Pair.of(Map.of(), ResponseEntity.status(HttpStatus.FORBIDDEN).body(String.format(i18n("forbidden_http_request"))));
				} else {
					return Pair.of(Map.of(), ResponseEntity.status(e.getStatusCode()).headers(e.getResponseHeaders()).body(e.getResponseBodyAsString()));
				}
			} catch (Exception e) {
				throw new RuntimeException(e);
			}
		} else if (request.getType() == ErrorNotificationChannelType.email) {
			var notification = ErrorNotification.builder()
					.subject("Test email for error notification")
					.body("This is a test message for error notification")
					.build();
			notification.setCreatedAt(new Date());
			emailHelper.sendEmail(SyncariContext.getUser(),
					List.of(notification),
					new Date());
			return Pair.of(notification, Map.of("success", "true"));
		} else {
			throw new RuntimeException("Unsupported type " + request.getType());
		}
	}

	public void processInvitationAcceptance(String encInstanceId, String invitationId, EmailConfigStatus status) {
		ValidationUtils.validateCondition(encInstanceId == null, "Invalid request");
		ValidationUtils.validateCondition(invitationId == null, "Invalid request");
		String instanceId = new String(Base64.getDecoder().decode(encInstanceId), StandardCharsets.UTF_8);
		var org = subService.getOrgBySyncariId(instanceId);
		var user = userService.getSystemUser();
		var instance = org.getInstance(instanceId);
		ValidationUtils.validateCondition(instance.isEmpty(), "Invalid request");
		SyncariContext.runWithContext(org, instance.get(), user, () -> {
			var invitation = invitationRepo.findByInvitationId(invitationId);
			ValidationUtils.validateCondition(invitation.isEmpty(), "Unknown or expired invitation");
			ValidationUtils.validateCondition(invitation.get().hasExpired(), "Unknown or expired invitation");
			var configOpt = configRepo.findById(invitation.get().getConfigId());
			ValidationUtils.validateCondition(configOpt.isEmpty(), "Unknown or expired invitation");
			var config = (ErrorNotificationEmailConfig) configOpt.get();
			if (CollectionUtils.isNotEmpty(config.getEmails())) {
				var emailConfig = config.getEmails().stream().filter(e -> e.getEmail().equals(invitation.get().getEmail())).findFirst();
				ValidationUtils.validateCondition(emailConfig.isEmpty(), "Unknown or expired invitation");
				emailConfig.ifPresent(e -> {
					e.setStatus(status);
				});
			}
			configRepo.save(config);
			invitationRepo.deleteById(invitation.get().getId());
		});
	}
}
