package io.tapdata.mongodb.decoder.impl;

import io.tapdata.entity.error.CoreException;
import io.tapdata.mongodb.decoder.CustomSQLObject;

import java.text.SimpleDateFormat;
import java.time.ZoneId;
import java.util.Calendar;
import java.util.Date;
import java.util.Map;
import java.util.Optional;
import java.util.TimeZone;

public class DynamicDateFilterTime implements CustomSQLObject<Object, Map<String, Object>> {
    public static final String DYNAMIC_DATE = "$dynamicDate";
    public static final String CUSTOM_FORMAT = "customFormat";
    public static final String TO_STRING_FORMAT = "toStringFormat";
    public static final String SUBTRACT = "subtract";
    public static final String TIMEZONE = "timezone";
    public static final String DEFAULT_DATE_FORMAT = "yyyy-MM-dd HH:mm:ss.SSS";

    @Override
    public Object execute(Object functionObj, Map<String, Object> curMap) {
        Object format = null;
        Object toStringFormat = null;
        Object timezone = null;
        int subtract = 0;
        if (functionObj instanceof Map) {
            format = ((Map<String, Object>) functionObj).get(CUSTOM_FORMAT);
            timezone = ((Map<String, Object>) functionObj).get(TIMEZONE);
            toStringFormat = ((Map<String, Object>) functionObj).get(TO_STRING_FORMAT);
            Object sub = Optional.ofNullable(((Map<String, Object>) functionObj).get(SUBTRACT)).orElse(0);
            if (sub instanceof Number) {
                subtract = ((Number)sub).intValue();
            } else {
                try {
                    subtract = Integer.parseInt(String.valueOf(sub));
                } catch (Exception ignore) {
                    throw new IllegalArgumentException("a subtract value is illegal value, \"" + SUBTRACT + "\" must be a number");
                }
            }
        }
        if (null != format && !(format instanceof String)) {
            throw new IllegalArgumentException("a customFormat value is illegal value, \"" + CUSTOM_FORMAT + "\" must be a date time format string, such as: \"yyyy-MM-dd HH:mm:ss.SSS\"");
        }
        TimeZone timeZone = null;
        if (null != timezone) {
            try {
                timeZone = TimeZone.getTimeZone(ZoneId.of(String.valueOf(timezone)));
            } catch (Exception e) {
                throw new IllegalArgumentException("a timezone value is illegal value, \"" + TIMEZONE + "\" must be a Number, such as: 8 or -8");
            }
        }
        if (null != toStringFormat && !(toStringFormat instanceof String)) {
            throw new IllegalArgumentException("a toStringFormat value is illegal value, \"" + TO_STRING_FORMAT + "\" must be a date time format string, such as: \"yyyy-MM-dd HH:mm:ss\"");
        }
        return covertTime((String) format, subtract, (String) toStringFormat, timeZone);
    }

    @Override
    public String getFunctionName() {
        return DYNAMIC_DATE;
    }

    protected Object covertTime(String dateTime, int subtract, String toStringFormat, TimeZone timeZone) {
        Calendar calendar = Calendar.getInstance();
        calendar.add(Calendar.MILLISECOND, -subtract);
        Date formatDate = calendar.getTime();
        if (null != dateTime) {
            try {
                SimpleDateFormat sdf = new SimpleDateFormat(dateTime);
                if (null != timeZone) {
                    sdf.setTimeZone(timeZone);
                }
                String dateFormatStr = sdf.format(formatDate);
                sdf.applyPattern(DEFAULT_DATE_FORMAT);
                formatDate = sdf.parse(dateFormatStr);
            } catch (Exception e) {
                throw new CoreException("Illegal argument in function: {}, wrong value: {}, the correct key value pairs should be as follows: \"{}\": \"yyyy-dd-MM hh:mm:ss.SSS\", error message: {}",
                        getFunctionName(), dateTime, CUSTOM_FORMAT, e.getMessage());
            }
        }

        if (null != toStringFormat) {
            try {
                SimpleDateFormat sdf = new SimpleDateFormat(toStringFormat);
                if (null != timeZone) {
                    sdf.setTimeZone(timeZone);
                }
                return sdf.format(formatDate);
            } catch (Exception e) {
                throw new CoreException("Illegal argument in function: {}, {}'s wrong value: {}, error message: {}",
                        getFunctionName(), toStringFormat, TO_STRING_FORMAT, e.getMessage());
            }
        }
        return formatDate;
    }

}