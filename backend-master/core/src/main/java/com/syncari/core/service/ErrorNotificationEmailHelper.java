package com.syncari.core.service;

import com.syncari.core.GlobalConstants;
import com.syncari.core.SyncariContext;
import com.syncari.core.config.AppConfig;
import com.syncari.core.model.EmailConfigStatus;
import com.syncari.core.model.ErrorNotification;
import com.syncari.core.model.ErrorNotificationEmailConfig;
import com.syncari.core.model.User;
import com.syncari.core.model.misc.ErrorNotificationConfigStatus;
import com.syncari.core.model.misc.NotificationType;
import com.syncari.core.repositories.customer.ErrorNotificationConfigRepo;
import com.syncari.core.repositories.syncari.UserRepo;
import com.syncari.core.template.TemplateRenderer;
import com.syncari.utils.I18n;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.collections.CollectionUtils;
import org.apache.commons.lang.StringUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Component;

import java.net.ConnectException;
import java.net.SocketException;
import java.net.SocketTimeoutException;
import java.net.UnknownHostException;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

@Slf4j
@Component
public class ErrorNotificationEmailHelper {
	private static final String SINGLE_ERROR_TEMPLATE_PATH = "templates/error.notification.single.template";
	private static final String DIGEST_ERROR_TEMPLATE_PATH = "templates/error.notification.digest.template";
	private static final String OPTIN_TEMPLATE_PATH = "templates/error.notification.opt-in.template";
	private static final String VIEW_ALL_NOTIFICATION_URL = "%s/notifications";
	private static final String NOTIFICATION_SETTINGS_URL = "%s/profile/notifications";
	private static final String OPTIN_INVITE_URL = "%s/error-notifications/validate-email/%s/%s/%s";
	private static final int MAX_CONSECUTIVE_FAILURES = 3;

	// Network-related exception types that indicate transient errors
	private static final Set<Class<? extends Throwable>> TRANSIENT_EXCEPTION_TYPES = Set.of(
		SocketTimeoutException.class,
		ConnectException.class,
		UnknownHostException.class,
		SocketException.class
	);

	// Error message keywords that indicate transient errors
	private static final Set<String> TRANSIENT_ERROR_MESSAGES = Set.of(
		"connection timeout",
		"connection refused",
		"connection reset",
		"network is unreachable",
		"temporary failure",
		"timed out"
	);

	// SMTP transient error codes (4xx codes with word boundaries to avoid false positives)
	private static final Set<Pattern> SMTP_TRANSIENT_ERROR_PATTERNS = Set.of(
		Pattern.compile(".*\\b421\\b.*"),  // Service not available
		Pattern.compile(".*\\b450\\b.*"),  // Mailbox unavailable
		Pattern.compile(".*\\b451\\b.*"),  // Local error in processing
		Pattern.compile(".*\\b452\\b.*")   // Insufficient system storage
	);

	@Autowired
    @Qualifier("defaultEmailService")
    private EmailService emailService;
	@Autowired
    private TemplateRenderer renderer;
	@Autowired
	private AppConfig appConfig;
	@Autowired
	private UserRepo userRepo;
	@Autowired
	private ErrorNotificationConfigRepo configRepo;
	@Autowired
	private NotificationService notificationService;

	/**
	 * Checks if an exception is a transient error (network/SMTP server issues)
	 * that should not count towards disabling the configuration.
	 * Walks the entire exception chain to handle deeply nested causes.
	 */
	private boolean isTransientError(Exception ex) {
		if (ex == null) {
			return false;
		}

		// Walk through the entire exception chain with circular reference protection
		Set<Throwable> visited = new HashSet<>();
		Throwable current = ex;

		while (current != null && !visited.contains(current)) {
			visited.add(current);

			// Check if exception type matches any known transient exception types using instanceof
			for (Class<? extends Throwable> exceptionType : TRANSIENT_EXCEPTION_TYPES) {
				if (exceptionType.isInstance(current)) {
					return true;
				}
			}

			// Check error message for transient error indicators
			String message = current.getMessage() != null ? current.getMessage().toLowerCase() : "";

			// Check for transient error message keywords
			for (String errorMessage : TRANSIENT_ERROR_MESSAGES) {
				if (message.contains(errorMessage)) {
					return true;
				}
			}

			// Check for SMTP transient error codes (4xx codes)
			for (Pattern pattern : SMTP_TRANSIENT_ERROR_PATTERNS) {
				if (pattern.matcher(message).matches()) {
					return true;
				}
			}

			// Move to the next cause in the chain
			current = current.getCause();
		}

		return false;
	}

	public void sendEmail(ErrorNotificationEmailConfig emailConfig, List<ErrorNotification> notifList) {
		log.info("ErrorNotificationEmailHelper webhook request for {}",  emailConfig.getName());
		if(CollectionUtils.isNotEmpty(emailConfig.getEmails())) {
			var emails = emailConfig.getEmails()
				.stream()
				.filter(e -> e.getStatus() == EmailConfigStatus.Active)
				.map(e -> e.getEmail())
				.collect(Collectors.toSet());
			Map<String, String> errors = new LinkedHashMap<>();
			var notSendingEmail = emailConfig.getEmails()
					.stream()
					.filter(e -> e.getStatus() != EmailConfigStatus.Active)
					.map(e -> e.getEmail())
					.collect(Collectors.toSet());
			if(!notSendingEmail.isEmpty()) {
				log.info("Not sending emails to {} because these emails are not active for {} in instance {}", notSendingEmail, emailConfig.getName(), SyncariContext.getSyncariId());
			}
			if(CollectionUtils.isNotEmpty(emails)) {
				Map<String, Exception> exceptions = new LinkedHashMap<>();
				emails.forEach(e -> {
					try {
						sendEmail(userRepo.findByEmail(e).orElse(new User(e, "")), notifList,
								emailConfig.getLastNotificationTimestamp() == null ? new Date()
										: emailConfig.getLastNotificationTimestamp());
					} catch (Exception ex) {
						log.error("Message sending failed for {}", e, ex);
						errors.put(e, ex.getMessage());
						exceptions.put(e, ex);
					}
				});

				boolean hasAnyPermanentError = exceptions.values().stream()
						.anyMatch(ex -> !isTransientError(ex));

				if(!errors.isEmpty()) {
					emailConfig.setLastErrorTimestamp(new Date());
					emailConfig.setLastError(translateError(errors));

					if (hasAnyPermanentError) {
						// Increment retry counter for permanent errors only
						Integer retries = emailConfig.getRetries() != null ? emailConfig.getRetries() : 0;
						retries++;
						emailConfig.setRetries(retries);

						if (retries >= MAX_CONSECUTIVE_FAILURES) {
							// Disable only after MAX_CONSECUTIVE_FAILURES consecutive permanent failures
							emailConfig.setStatus(ErrorNotificationConfigStatus.Disabled);
							sendDisabledNotification(emailConfig, errors);
							log.warn("Email config '{}' disabled after {} consecutive permanent failures",
								emailConfig.getName(), retries);
						} else {
							log.warn("Email config '{}' has {} permanent failure(s), will disable after {} failures",
								emailConfig.getName(), retries, MAX_CONSECUTIVE_FAILURES);
						}
					} else {
						// All errors are transient - reset counter as these don't count
						emailConfig.setRetries(0);
						log.info("Email config '{}' encountered only transient errors (network/SMTP), retry counter reset",
							emailConfig.getName());
					}
				} else {
					// Reset retry counter on successful send
					emailConfig.setRetries(0);
				}
			}
			log.info("ErrorNotificationEmailHelper webhook request for {} completed with error {} ",  emailConfig.getName(), !errors.isEmpty());
		}
		emailConfig.setLastNotificationTimestamp(new Date());
		emailConfig.setProcessing(false);
		configRepo.save(emailConfig);
	}
	
	private String translateError(Map<String, String> errors) {
		return I18n.i18n("error_notification_email_failure", String.join(", ", errors.keySet()), String.join(", ", Set.copyOf(errors.values())));
	}

	public void sendEmail(User user, List<ErrorNotification> notifList, Date lastMessageSentTime) {
		String body = "";
		String subject = "Your Syncari instance '%s'(%s) in subscription '%s' has %s notification(s)";
		Map<String, Object> context = new HashMap<>();
		context.put("syncariLogoUrl", String.format(GlobalConstants.SYNCARI_LOGO, appConfig.getCloudCdnHost()));
		context.put("name", getName(user));
		context.put("tz", getTimeZone(user));
		context.put("allNotificationsUrl", String.format(VIEW_ALL_NOTIFICATION_URL, appConfig.getSpectrumServerHost()));
		context.put("manageNotificationsUrl", String.format(NOTIFICATION_SETTINGS_URL, appConfig.getSpectrumServerHost()));
		//if(sub.getFrequency() == ErrorNotificationFrequency.IMMEDIATE || userInfo.isEmpty()) {
		if(notifList.size() == 1) {
			context.put("notification", notifList.get(0));
			body = renderer.render(SINGLE_ERROR_TEMPLATE_PATH, context);
			subject = String.format(subject, SyncariContext.getInstance().getName(), SyncariContext.getInstance().getSyncariId(), SyncariContext.getOrganziation().getName(), "1");
		} else {
			log.info("ErrorNotification count {}", notifList.size());
			log.info("ErrorNotification {}", notifList);
			context.put("lastDigestDate", lastMessageSentTime);
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
			subject = String.format(subject, SyncariContext.getInstance().getName(), SyncariContext.getInstance().getSyncariId(), SyncariContext.getOrganziation().getName(), notifList.size());
		}
		log.debug("Sending email notification to {} ", user.getEmail());
		log.debug("Subject {}", subject);
		log.debug("Message {}", body);
		emailService.sendHtml(List.of(user.getEmail()), subject, body);

	}
	
	public void sendOptInEmail(User user, String invitationId, ErrorNotificationEmailConfig config, String instanceId) {
		String encInstanceId =  Base64.getEncoder().encodeToString(instanceId.getBytes(StandardCharsets.UTF_8));
		String body = "";
		String subject = String.format("Please validate your email to receive a Syncari Notification");
		Map<String, Object> context = new HashMap<>();
		context.put("syncariLogoUrl", String.format(GlobalConstants.SYNCARI_LOGO, appConfig.getCloudCdnHost()));
		context.put("userLogoUrl", String.format("%s/user-add_2X.png", appConfig.getCloudCdnHost()));
		context.put("name", getName(user));
		context.put("fromUser", getName(SyncariContext.getUser()));
		context.put("configName", config.getName());
		context.put("cadence", config.getCadence().getLabel());
		context.put("optInUrl", String.format(OPTIN_INVITE_URL, appConfig.getSpectrumServerHost(), encInstanceId, invitationId, EmailConfigStatus.Active));
		context.put("optOutUrl", String.format(OPTIN_INVITE_URL, appConfig.getSpectrumServerHost(), encInstanceId, invitationId, EmailConfigStatus.OptOut));
		body = renderer.render(OPTIN_TEMPLATE_PATH, context);
		
		log.debug("Sending opt-in email to {} ", user.getEmail());
		log.debug("Subject {}", subject);
		log.debug("Message {}", body);
		emailService.sendHtml(List.of(user.getEmail()), subject, body);
	}
	
	private String getName(User user) {
		if(StringUtils.isNotBlank(user.getFirstName()) && StringUtils.isNotBlank(user.getLastName())) {
			return user.getFirstName() + " " + user.getLastName();
		} else {
			return user.getEmail();
		}
	}
	
	private String getTimeZone(User user) {
		if(StringUtils.isNotBlank(user.getTimeZone())) {
			return user.getTimeZone();
		} else {
			return TimeZone.getDefault().getID();
		}
	}

	private void sendDisabledNotification(ErrorNotificationEmailConfig emailConfig, Map<String, String> errors) {
		try {
			String subject = I18n.i18n("error_notification_email_disabled_subject", emailConfig.getName());
			String body = I18n.i18n("error_notification_email_disabled_body",
				emailConfig.getName(),
				String.join(", ", errors.keySet()),
				String.join(", ", Set.copyOf(errors.values())));

			notificationService.broadcast(subject, body, NotificationType.ERROR);

			log.info("Sent in-app notification for disabled email config: {}", emailConfig.getName());
		} catch (Exception ex) {
			log.error("Failed to send in-app notification for disabled email config: {}", emailConfig.getName(), ex);
		}
	}
}
