package com.syncari.core.service;

import com.syncari.core.config.MailConfig;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.exception.ExceptionUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.context.annotation.Profile;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.MimeMessageHelper;
import org.springframework.retry.annotation.Backoff;
import org.springframework.retry.annotation.Retryable;
import org.springframework.stereotype.Component;

import javax.mail.MessagingException;
import javax.mail.internet.MimeMessage;
import java.util.List;
import java.util.Optional;

@Slf4j
@Component("plgEmailService")
@Profile("!development")
public class PlgEmailService implements EmailService{
    public static final String SUPPORT = "support@syncari.com";

    @Autowired
    @Qualifier("javaPlgMailService")
    private JavaMailSender javaPlgMailSender;

    @Autowired
    MailConfig mailConfig;

    @Retryable(value = RuntimeException.class, maxAttempts = 3, backoff = @Backoff(delay = 100))
    public void sendText(List<String> to, String subject, String body, Optional<String> fromEmail) {
        if(to == null || to.isEmpty()) return;
        SimpleMailMessage msg = new SimpleMailMessage();
        msg.setTo(to.toArray(new String[0]));
        msg.setSubject(subject);
        fromEmail.ifPresentOrElse(fromE -> msg.setFrom(fromE), () -> msg.setFrom(mailConfig.getFromEmail()));
        msg.setText(body);
        javaPlgMailSender.send(msg);
        log.info("Successfully sent text email");
    }
    @Retryable(value = RuntimeException.class, maxAttempts = 3, backoff = @Backoff(delay = 100))
    public void sendText(List<String> to, String subject, String body) {

        if(to == null || to.isEmpty()) return;
        SimpleMailMessage msg = new SimpleMailMessage();
        msg.setTo(to.toArray(new String[0]));
        msg.setSubject(subject);
        msg.setFrom(mailConfig.getFromEmail());
        msg.setText(body);
        javaPlgMailSender.send(msg);
        log.info("Successfully sent text email");
    }

    @Retryable(value = RuntimeException.class, maxAttempts = 3, backoff = @Backoff(delay = 100))
    public void sendHtml(List<String> to, String subject, String body) {
        if(to == null || to.isEmpty()) return;
        MimeMessage mimeMessage = javaPlgMailSender.createMimeMessage();
        MimeMessageHelper helper = new MimeMessageHelper(mimeMessage, "utf-8");
        try {
            helper.setText(body, true);
            helper.setTo(to.toArray(new String[0]));
            helper.setSubject(subject);
            helper.setFrom(mailConfig.getFromPlgEmail());
            javaPlgMailSender.send(mimeMessage);
        } catch (MessagingException e) {
            log.error("MessagingException occurred with trace {}", ExceptionUtils.getStackTrace(e));
            throw new RuntimeException(e.getMessage());
        }
        log.info("Successfully sent html email");
    }

    @Retryable(value = RuntimeException.class, maxAttempts = 3, backoff = @Backoff(delay = 100))
    public void sendHtml(List<String> to, String subject,String from, String body) {
        if(to == null || to.isEmpty()) return;
        MimeMessage mimeMessage = javaPlgMailSender.createMimeMessage();
        MimeMessageHelper helper = new MimeMessageHelper(mimeMessage, "utf-8");
        try {
            helper.setText(body, true);
            helper.setTo(to.toArray(new String[0]));
            helper.setSubject(subject);
            helper.setFrom(mailConfig.getFromPlgEmail());
            javaPlgMailSender.send(mimeMessage);
        } catch (MessagingException e) {
            throw new RuntimeException(e.getMessage());
        }
        log.info("Successfully sent html email");
    }
    public void sendSupportEmail(String subject, String body){
        sendHtml(List.of(SUPPORT), subject, body);
    }
    @Override
    public void sendErrorEmail(List<String> to, List<String> bcc,String subject, String body) {
        sendText(to, subject, body, Optional.of(mailConfig.getFromForErrorEmail()));
    }
    
	@Override
    @Retryable(value = RuntimeException.class, maxAttempts = 3, backoff = @Backoff(delay = 100))
    public void sendHtml(List<String> to, List<String> cc, String subject, String body) {
		if(to == null || to.isEmpty()) return;
        MimeMessage mimeMessage = javaPlgMailSender.createMimeMessage();
        MimeMessageHelper helper = new MimeMessageHelper(mimeMessage, "utf-8");
        try {
            helper.setText(body, true);
            helper.setTo(to.toArray(new String[0]));
            if(cc != null && !cc.isEmpty()) {
				helper.setCc(cc.toArray(new String[0]));
			}
            helper.setSubject(subject);
            helper.setFrom(mailConfig.getFromPlgEmail());
            javaPlgMailSender.send(mimeMessage);
        } catch (MessagingException e) {
            throw new RuntimeException(e.getMessage());
        }
        log.info("Successfully sent html email");
	}
}
