package com.syncari.core.service;

import java.util.List;
import java.util.Optional;

public interface EmailService {
	public void sendText(List<String> to, String subject, String body);
	public void sendHtml(List<String> to, String subject, String body);
	public void sendHtml(List<String> to, List<String> cc, String subject, String body);
	public void sendSupportEmail(String subject, String body);
	public void sendErrorEmail(List<String> to, List<String> bcc, String subject, String body);
	public void sendText(List<String> to, String subject, String body, Optional<String> fromEmail);
}
