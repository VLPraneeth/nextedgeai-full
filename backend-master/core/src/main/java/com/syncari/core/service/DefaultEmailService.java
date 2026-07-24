package com.syncari.core.service;

import org.apache.commons.collections4.CollectionUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.context.annotation.Primary;
import org.springframework.context.annotation.Profile;
import org.springframework.retry.annotation.Backoff;
import org.springframework.retry.annotation.Retryable;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Optional;
import javax.mail.MessagingException;
import javax.mail.internet.MimeMessage;

import com.syncari.core.config.MailConfig;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.MimeMessageHelper;

import lombok.extern.slf4j.Slf4j;

@Slf4j
@Component("defaultEmailService")
@Profile("!development")
public class DefaultEmailService implements EmailService {
    public static final String SUPPORT = "support@syncari.com";

    @Autowired
	@Qualifier("javaMailService")
	private JavaMailSender javaMailSender;

	@Autowired
	MailConfig mailConfig;

	@Retryable(value = RuntimeException.class, maxAttempts = 3, backoff = @Backoff(delay = 100))
	public void sendText(List<String> to, String subject, String body, Optional<String> fromEmail) {
		sendText(to, List.of(), subject, body, fromEmail);
	}
	@Retryable(value = RuntimeException.class, maxAttempts = 3, backoff = @Backoff(delay = 100))
	public void sendText(List<String> to, List<String> bcc, String subject, String body, Optional<String> fromEmail) {
	    if(to == null && bcc == null) return;
		SimpleMailMessage msg = new SimpleMailMessage();
		if(CollectionUtils.isNotEmpty(to)) {
			msg.setTo(to.toArray(new String[0]));
		}
		if(CollectionUtils.isNotEmpty(bcc)) {
			msg.setBcc(bcc.toArray(new String[0]));
		}
		msg.setSubject(subject);
		fromEmail.ifPresentOrElse(fromE -> msg.setFrom(fromE), () -> msg.setFrom(mailConfig.getFromEmail()));
		msg.setText(body);
		javaMailSender.send(msg);
		log.info("Successfully sent text email");
	}

	@Override
	@Retryable(value = RuntimeException.class, maxAttempts = 3, backoff = @Backoff(delay = 100))
	public void sendText(List<String> to, String subject, String body) {

		if(to == null || to.isEmpty()) return;
		SimpleMailMessage msg = new SimpleMailMessage();
		msg.setTo(to.toArray(new String[0]));
		msg.setSubject(subject);
		msg.setFrom(mailConfig.getFromEmail());
		msg.setText(body);
		javaMailSender.send(msg);
		log.info("Successfully sent text email");
	}

	@Retryable(value = RuntimeException.class, maxAttempts = 3, backoff = @Backoff(delay = 100))
	public void sendHtml(List<String> to, String subject, String body) {
	    if(to == null || to.isEmpty()) return;
		MimeMessage mimeMessage = javaMailSender.createMimeMessage();
		MimeMessageHelper helper = new MimeMessageHelper(mimeMessage, "utf-8");
		try {
			helper.setText(body, true);
			helper.setTo(to.toArray(new String[0]));
			helper.setSubject(subject);
			helper.setFrom(mailConfig.getFromEmail());
			javaMailSender.send(mimeMessage);
		} catch (MessagingException e) {
			throw new RuntimeException(e.getMessage());
		}
		log.info("Successfully sent html email");
	}

	@Retryable(value = RuntimeException.class, maxAttempts = 3, backoff = @Backoff(delay = 100))
	public void sendHtml(List<String> to, String subject,String from, String body) {
		if(to == null || to.isEmpty()) return;
		MimeMessage mimeMessage = javaMailSender.createMimeMessage();
		MimeMessageHelper helper = new MimeMessageHelper(mimeMessage, "utf-8");
		try {
			helper.setText(body, true);
			helper.setTo(to.toArray(new String[0]));
			helper.setSubject(subject);
			helper.setFrom(mailConfig.getFromEmail());
			javaMailSender.send(mimeMessage);
		} catch (MessagingException e) {
			throw new RuntimeException(e.getMessage());
		}
		log.info("Successfully sent html email");
	}

	public void sendSupportEmail(String subject, String body){
		sendHtml(List.of(SUPPORT), subject, body);
	}

	@Override
	public void sendErrorEmail(List<String> to, List<String> bcc, String subject, String body) {
		sendText(to, bcc, subject, body, Optional.of(mailConfig.getFromForErrorEmail()));
	}

	@Override
	@Retryable(value = RuntimeException.class, maxAttempts = 3, backoff = @Backoff(delay = 100))
	public void sendHtml(List<String> to, List<String> cc, String subject, String body) {
		if(to == null || to.isEmpty()) return;
		MimeMessage mimeMessage = javaMailSender.createMimeMessage();
		MimeMessageHelper helper = new MimeMessageHelper(mimeMessage, "utf-8");
		try {
			helper.setText(body, true);
			helper.setTo(to.toArray(new String[0]));
			if(cc != null && !cc.isEmpty()) {
				helper.setCc(cc.toArray(new String[0]));
			}
			helper.setSubject(subject);
			helper.setFrom(mailConfig.getFromEmail());
			javaMailSender.send(mimeMessage);
		} catch (MessagingException e) {
			throw new RuntimeException(e.getMessage());
		}
		log.info("Successfully sent html email");
		
	}
}
