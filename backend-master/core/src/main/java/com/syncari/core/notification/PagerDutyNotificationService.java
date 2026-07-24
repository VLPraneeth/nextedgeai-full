package com.syncari.core.notification;

import java.util.List;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Component;

import com.syncari.core.config.AppConfig;
import com.syncari.core.model.Event;
import com.syncari.core.service.EmailService;

import lombok.extern.slf4j.Slf4j;

@Slf4j
@Component
public class PagerDutyNotificationService implements NotificationService {
	@Autowired
	@Qualifier("defaultEmailService")
	EmailService emailService;
	@Autowired
	AppConfig config;

	@Override
	public void notify(Event event) {
		emailService.sendText(List.of(config.getPdEmailAddress()), event.getSubType(),
				event.getDetails().get("message").toString());
	}
}
