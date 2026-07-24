package com.syncari.core.service;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Optional;

@Slf4j
@Component("plgEmailService")
@Profile("development")
public class PLGDevEmailService implements EmailService{
    @Override
    public void sendText(List<String> to, String subject, String body, Optional<String> fromEmail) {
        log.info("Skipping send text email for development environment");
    }

    @Override
    public void sendText(List<String> to, String subject, String body) {
        log.info("Skipping sendText email for development environment");
    }

    @Override
    public void sendHtml(List<String> to, String subject, String body) {
        log.info("Skipping send html email for development environment");
    }

    @Override
    public void sendSupportEmail(String subject, String body){
        log.info("Skipping send support email for development environment");
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
