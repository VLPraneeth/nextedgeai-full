package com.syncari.core.config;

import java.util.Properties;

import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.JavaMailSenderImpl;

import lombok.extern.slf4j.Slf4j;

@Slf4j
@Configuration
public class MailConfig {
	@Value("${mail.sender.username}")
	private String uname;
	@Value("${mail.sender.password}")
	private String password;
	@Value("${mail.smtp.host}")
	private String host;
	@Value("${mail.smtp.port}")
	private String port;
	@Value("${mail.sender.from-email}")
	private String fromEmail;
	@Value("${mail.sender.from-for-error-email}")
	private String fromErrorEmail;

	@Value("${mail.sender.from-plg-email}")
	private String fromPlgEmail;

	@Bean
	@Qualifier(value = "javaMailService")
	public JavaMailSender javaMailService() {
		JavaMailSenderImpl javaMailSender = new JavaMailSenderImpl();
		javaMailSender.setUsername(uname);
		javaMailSender.setPassword(password);
		javaMailSender.setJavaMailProperties(getMailProperties());
		return javaMailSender;
	}

	@Bean
	@Qualifier(value = "javaPlgMailService")
	public JavaMailSender javaPlgMailService() {
		JavaMailSenderImpl javaMailSender = new JavaMailSenderImpl();
		javaMailSender.setUsername(uname);
		javaMailSender.setPassword(password);
		javaMailSender.setJavaMailProperties(getMailProperties());
		return javaMailSender;
	}

	private Properties getMailProperties() {
		Properties properties = new Properties();
		properties.put("mail.transport.protocol", "smtp");
		properties.put("mail.smtp.auth", "true");
		properties.put("mail.smtp.starttls.enable", "true");
		properties.put("mail.debug", "false");
		properties.put("mail.smtp.ssl.trust", host);
		properties.put("mail.smtp.ssl.protocols", "TLSv1.2");
		properties.put("mail.smtp.host", host);
		properties.put("mail.smtp.port", port);
		return properties;
	}

	public String getFromEmail() {
		return fromEmail;
	}

	public String getFromPlgEmail() {
		return fromPlgEmail;
	}

	public String getFromForErrorEmail() {
		return fromErrorEmail;
	}
}
