package com.syncari.core.service;

import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Optional;

@Slf4j
@Component("defaultEmailService")
@Profile("development")
public class DevEmailService implements EmailService {

    @Override
    public void sendText(List<String> to, String subject, String body,Optional<String> fromEmail) {
        log.info("Skipping send text email for development environment");
    }

    @Override
    public void sendText(List<String> to, String subject, String body) {
        log.info("Skipping sendText email for development environment");
    }

    @Override
    public void sendHtml(List<String> to, String subject, String body) {
        log.info("Skipping send html email for development environment To:{}, Subject:{}, Body:{}", to, subject, body);
    }

    @Override
    public void sendSupportEmail(String subject, String body){
        log.info("Skipping send support email for development environment, Subject:{}, Body:{}", subject, body);
    }

    @Override
    public void sendErrorEmail(List<String> to, List<String> bcc, String subject, String body){
        log.info("Skipping send error email for development environment");
    }

	@Override
	public void sendHtml(List<String> to, List<String> cc, String subject, String body) {
		log.info("Skipping send html email for development environment");
		
	}
}
