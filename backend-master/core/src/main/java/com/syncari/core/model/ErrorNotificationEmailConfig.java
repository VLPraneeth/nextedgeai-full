package com.syncari.core.model;

import static com.syncari.utils.I18n.i18n;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

import org.apache.commons.collections.CollectionUtils;
import org.apache.commons.validator.routines.EmailValidator;

import com.syncari.core.model.misc.ErrorNotificationChannelType;
import com.syncari.core.utils.ValidationUtils;

import lombok.Data;
import lombok.experimental.Accessors;

@Data
@Accessors(chain = true)
public class ErrorNotificationEmailConfig extends ErrorNotificationConfig {

	private List<EmailConfig> emails;

	@Override
	public ErrorNotificationChannelType getType() {
		return ErrorNotificationChannelType.email;
	}

	@Override
	public void loadConfig(Map<String, Object> config) {
		ValidationUtils.validateCondition(config == null, "Email config is not present");
		ValidationUtils.validateCondition(config.get("emails") == null, "Email config is not present");
		List<Map<String, String>> emailConfigs = (List<Map<String, String>>) config.get("emails");
		List<String> existingEmails = new ArrayList<>();
		if(CollectionUtils.isNotEmpty(emails)) {
			existingEmails.addAll(emails.stream().map(e -> e.getEmail()).collect(Collectors.toList()));
		} else {
			emails = new ArrayList<>();
		}
		emailConfigs.forEach(emailConfig -> {
			if(!existingEmails.contains(emailConfig.get("email"))) {
				EmailConfig email = new EmailConfig(emailConfig.get("email"), null);
				emails.add(email);
			} else {
				existingEmails.remove(emailConfig.get("email"));
			}
		});
		if(CollectionUtils.isNotEmpty(existingEmails)) {
			//Delete entries
			Map<String, EmailConfig> emailMap = emails.stream().collect(Collectors.toMap(EmailConfig::getEmail, Function.identity()));
			existingEmails.forEach(e -> {
				if(emailMap.containsKey(e)) {
					emails.remove(emailMap.get(e));
				}
			});
		}

	}

	@Override
	public Map<String, Object> getConfig() {
		if(CollectionUtils.isEmpty(emails)) {
			return Map.of("emails", List.of());
		} else {
			return Map.of("emails", emails.stream().map(c -> {
				Map<String, String> map = new LinkedHashMap<String, String>();
				if(c.getEmail() != null) {
					map.put("email", c.getEmail());
				}
				if(c.getStatus() != null) {
					map.put("status", c.getStatus().toString());
				}
				return map;
			}).collect(Collectors.toList()));
		}
	}

	@Override
	public void validate() {
		if(CollectionUtils.isNotEmpty(emails)) {
			var emailValidator = EmailValidator.getInstance();
			var invalidEmails = emails.stream().map(c -> c.getEmail()).filter(email -> !emailValidator.isValid(email)).collect(Collectors.toList());
			ValidationUtils.validateCondition(!invalidEmails.isEmpty(), i18n("error_notification_invalid_email", String.join(", ", invalidEmails)));
		}
	}

}
