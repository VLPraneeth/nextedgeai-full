package com.syncari.core.utils;

import java.util.Date;

import org.springframework.scheduling.support.CronSequenceGenerator;
import org.springframework.util.StringUtils;

public class ScheduleUtils {
	public static boolean isValidCronExpression(String expression) {
		return CronSequenceGenerator.isValidExpression(appendSecondsIfRequired(expression));
	}
	
	public static Date next(String expression, Date date) {
		expression = appendSecondsIfRequired(expression);
		CronSequenceGenerator cronSequenceGenerator = new CronSequenceGenerator(expression);
		return cronSequenceGenerator.next(date);
	}

	private static String appendSecondsIfRequired(String expression) {
		if (expression != null) {
			String[] fields = StringUtils.tokenizeToStringArray(expression, " ");
			if (fields.length == 5) {
				expression = "0 " + expression;
			}
		}
		return expression;
	}
}
