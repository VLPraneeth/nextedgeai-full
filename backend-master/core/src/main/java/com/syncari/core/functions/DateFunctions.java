package com.syncari.core.functions;

import com.syncari.core.datatype.DatatypeFactory;
import com.syncari.core.datatype.DateType;
import com.syncari.core.datatype.DatetimeType;
import com.syncari.core.model.FunctionCall;
import com.syncari.core.pipeline.GraphContext;
import com.syncari.core.token.TokenHelper;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.text.SimpleDateFormat;
import java.time.*;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.time.temporal.ChronoUnit;
import java.util.*;

import static java.time.temporal.ChronoUnit.*;

@Slf4j
@Component
public class DateFunctions extends FunctionsBase{

	@Autowired
	TokenHelper tokenHelper;
	public static final String TIME = "exactMatch";
	public static final String DATETIME = "DATETIME";
	public static final String DATE = "DATE";
	public static final String SECONDS_OPTION = "SECONDS";
	public static final String MILLIS_OPTION = "MILLIS";

	private Map<String, java.util.function.Function<String, ZonedDateTime>> datetimeParsers = Map.of(
			"Epoch Timestamp in Seconds", value ->  ZonedDateTime.ofInstant(Instant.ofEpochMilli(Long.parseLong(value) * 1000),ZoneOffset.UTC),
			"Epoch Timestamp in Milliseconds", value ->  ZonedDateTime.ofInstant(Instant.ofEpochMilli(Long.parseLong(value)),ZoneOffset.UTC)
			);

	@Function
	public ZonedDateTime now(Object input, FunctionCall functionCall, GraphContext context) {
		return ZonedDateTime.now();
	}

	@Function
	public Object nowOnEntity(List<Object> inputs, FunctionCall functionCall, GraphContext context) {
		ZonedDateTime time = ZonedDateTime.now();
		String returnValue = getConfig("returnValue", functionCall, context);
		Object result = ZonedDateTime.now();
		switch (returnValue) {
			case DATE:
				result = time.toLocalDate();
				break;
			case SECONDS_OPTION:
				result = time.toInstant().toEpochMilli() / 1000;
				break;
			case MILLIS_OPTION:
				result = time.toInstant().toEpochMilli();
				break;
		}
		context.addResult(result);
		return getInput(inputs);
	}

	@Function
	public Date today(Object input, FunctionCall functionCall, GraphContext context) {
		return new Date();
	}

	@Function
	public int year(Object input, FunctionCall functionCall, GraphContext context) {
		return LocalDate.now().getYear();
	}

	@Function
	public int month(Object input, FunctionCall functionCall, GraphContext context) {
		return LocalDate.now().getMonthValue();
	}

	@Function
	public Integer dayOfMonth(ZonedDateTime dateTime,FunctionCall functionCall, GraphContext context) {
		if (dateTime == null)
			return null;
		return dateTime.getDayOfMonth();
	}

	@Function
	public Integer dayOfWeek(ZonedDateTime dateTime, FunctionCall functionCall, GraphContext context) {
		if (dateTime == null)
			return null;
		return dateTime.getDayOfWeek().getValue();
	}

	@Function
	public Integer dayOfYear(ZonedDateTime dateTime,FunctionCall functionCall, GraphContext context) {
		if (dateTime == null)
			return null;
		return dateTime.getDayOfYear();
	}

	@Function
	public String dateFormat(Object date, FunctionCall functionCall, GraphContext context) {
	    if (date == null)
	        return null;
		String dateFormat = getConfig("pattern", functionCall);
		Object dateToFormat = date;
		if(date instanceof ZonedDateTime) {
		    // if date is an instance of ZonedDateTime use DateTimeFormatter
			return ((ZonedDateTime)date).format(DateTimeFormatter.ofPattern(dateFormat));
		}
		SimpleDateFormat formatter = new SimpleDateFormat(dateFormat);
		try {
		    return formatter.format(dateToFormat);
        } catch (Exception e) {
            log.error(e.getMessage());
            if(dateToFormat != null) {
            	return dateToFormat.toString();
            }
            return null;
        }
	}

	@Function
	public ZonedDateTime parse(String date, FunctionCall functionCall, GraphContext context) {
		if (date == null)
			return null;

		String format = getConfig("format", functionCall);
		try {
			return Optional.ofNullable(datetimeParsers.get(format))
					.orElse(value -> parseFormat(value, DateTimeFormatter.ofPattern(format)))
					.apply(date);
		} catch (Exception e) {
			log.error(e.getMessage());
			return null;
		}
	}

	private ZonedDateTime parseFormat(String value, DateTimeFormatter formatter) {
		try {
			return ZonedDateTime.parse(value, formatter);
		} catch (DateTimeParseException ex) {
			log.debug("Tried date formatter {} on string {} and failed", formatter, value);
		}
		try {
			LocalDateTime localDate = LocalDateTime.parse(value, formatter);
			return ZonedDateTime.of(localDate, ZoneOffset.UTC);
		} catch (DateTimeParseException ex) {
			log.debug("Tried local date time formatter {} on string {} and failed", formatter, value);
		}
		try {
			LocalDate localDate = LocalDate.parse(value, formatter);
			return ZonedDateTime.of(localDate, LocalTime.MIDNIGHT, ZoneOffset.UTC);
		} catch (DateTimeParseException ex) {
			log.debug("Tried local date formatter {} on string {} and failed", formatter, value);
		}

		try {
			LocalDate parsed = formatter.parse(value, LocalDate::from);
			if(parsed!=null){
				return parsed.atStartOfDay(ZoneOffset.UTC);
			}
		} catch (DateTimeParseException ex) {
			log.debug("Tried date formatter {} on string {} and failed", formatter, value);
		}
		return null;
	}

	@Function
	public ZonedDateTime plus(ZonedDateTime date, FunctionCall functionCall, GraphContext context) {
	    if (date == null)
	        return date;
		String deltaValue = getConfig("delta",functionCall).toString();
		if(deltaValue == null) return date;
		String delta = tokenHelper.resolveTokens(context, deltaValue);
		if (StringUtils.isBlank(delta)) {
			return date;
		}
		long amountToAdd = Long.parseLong((String) delta);
		String unitString = getConfig("unit",functionCall);
		if (!checkChronoType(unitString)) {
			return date;
		}
		var unit = ChronoUnit.valueOf(unitString);
		return date.plus(amountToAdd, unit);
	}

	@Function
	public ZonedDateTime minus(ZonedDateTime date, FunctionCall functionCall, GraphContext context) {
	    if (date == null)
	        return date;
		String deltaVaue = getConfig("delta",functionCall).toString();
		if(deltaVaue == null) return date;
		String delta = tokenHelper.resolveTokens(context,deltaVaue);
		if (StringUtils.isBlank(delta)) {
			return date;
		}
		long amountToSubtract = Long.parseLong(delta);
		String unitString = getConfig("unit",functionCall);
		if (!checkChronoType(unitString)) {
			return date;
		}
		var unit = ChronoUnit.valueOf(unitString);
		return date.minus(amountToSubtract, unit);
	}

	private <T> T getConfig(String configName, FunctionCall functionCall) {
		return (T) functionCall.getConfig().get(configName);
	}

	private boolean checkChronoType(String unitString) {
		boolean result = false;
		try {
			ChronoUnit.valueOf(unitString);
			result = true;
		} catch (Exception ignored) {
		}
		return result;
	}

	@Function
	public Object dateDiffOnEntity(Object input, FunctionCall functionCall, GraphContext context){
		Object result = dateDiff(input, functionCall, context);
		context.addResult(result);
		return input;
	}

	@Function
	public Long dateDiff(Object input, FunctionCall functionCall, GraphContext context) {

		String toAdd = functionCall.getConfig().getOrDefault("toDate", "").toString();
		Object toDateTokenValue = tokenHelper.resolveTokensObject(context, toAdd);
		Object actualToDate = getConvertedValue(toDateTokenValue);


		String fromDate = functionCall.getConfig().getOrDefault("fromDate", "").toString();
		Object fromDateTokenValue = tokenHelper.resolveTokensObject(context, fromDate);
		Object actualFromDate = getConvertedValue(fromDateTokenValue);



		if(null == actualFromDate || null == actualToDate)
			return 0L;

		Object timeUnit = getConfig("unit", functionCall);

		if(actualFromDate instanceof ZonedDateTime && actualToDate instanceof ZonedDateTime){

			return getDiff((ZonedDateTime) actualFromDate,(ZonedDateTime) actualToDate,timeUnit.toString());

		}else if((actualFromDate instanceof ZonedDateTime && actualToDate instanceof Date)
				|| (actualFromDate instanceof Date && actualToDate instanceof ZonedDateTime)){
			//One of the operand is java.util.Date and the other is zonedDateTime, so we need to convert localDate to the zone to which
			// the other operand belongs and then perform the diff

			LocalDate localDate = actualFromDate instanceof Date ?getLocalDateFromDate((Date)actualFromDate) : getLocalDateFromDate((Date)actualToDate);
			ZonedDateTime zonedDate = actualFromDate instanceof ZonedDateTime ? (ZonedDateTime) actualFromDate : (ZonedDateTime) actualToDate;
			TimeZone targetTimeZoneToResolveTo = TimeZone.getTimeZone(zonedDate.getZone());
			ZonedDateTime updatedZonedDateTime = ZonedDateTime.of(localDate.atStartOfDay(), targetTimeZoneToResolveTo.toZoneId());

			return getDiff(zonedDate, updatedZonedDateTime, timeUnit.toString());
		}else{
			//We need to convert any other data-type to zonedDateTime
			//which was not covered before
			DatetimeType converter = (DatetimeType) DatatypeFactory.getDatatype("datetime");
			actualToDate = converter.convert(toDateTokenValue);
			actualFromDate = converter.convert(actualFromDate);
			if(actualToDate instanceof ZonedDateTime && actualFromDate instanceof ZonedDateTime){
				return getDiff((ZonedDateTime) actualFromDate,(ZonedDateTime) actualToDate,timeUnit.toString());
			}
			log.error("Unable to find diff for the given Date {} and Target Date {} ",actualFromDate, actualToDate);
			return null;

		}
	}

	private Object getConvertedValue(Object value) {
		if(value == null) return null;
		Object targetDate;
		if(Date.class.isAssignableFrom(value.getClass())){
			DateType converter = (DateType) DatatypeFactory.getDatatype("date");
			targetDate = converter.convert(value);
		}else{
			DatetimeType converter = (DatetimeType) DatatypeFactory.getDatatype("datetime");
			targetDate = converter.convert(value);
		}
		return targetDate;
	}

	private LocalDate getLocalDateFromDate(Date providedDate) {
		return providedDate.toInstant()
				.atZone(ZoneId.systemDefault())
				.toLocalDate();
	}

	private Long getDiff(ZonedDateTime isGivenDateTimeStamp, ZonedDateTime isTargetDatTimeStamp, String timeUnit) {
		switch (timeUnit) {
			case "YEARS":
				return YEARS.between(isGivenDateTimeStamp, isTargetDatTimeStamp);
			case "MONTHS":
				return MONTHS.between(isGivenDateTimeStamp, isTargetDatTimeStamp);
			case "WEEKS":
				return WEEKS.between(isGivenDateTimeStamp, isTargetDatTimeStamp);
			case "DAYS":
				return DAYS.between(isGivenDateTimeStamp, isTargetDatTimeStamp);
			case "HOURS":
				return HOURS.between(isGivenDateTimeStamp, isTargetDatTimeStamp);
			case "MINUTES":
				return MINUTES.between(isGivenDateTimeStamp, isTargetDatTimeStamp);
			case "SECONDS":
				return SECONDS.between(isGivenDateTimeStamp, isTargetDatTimeStamp);
			case "MILLISECONDS":
				return MILLIS.between(isGivenDateTimeStamp, isTargetDatTimeStamp);
			default:
				log.error("Unable to find diff for the given Date {} and Target Date {} ", isGivenDateTimeStamp, isTargetDatTimeStamp);
				return null;
		}
	}


}
