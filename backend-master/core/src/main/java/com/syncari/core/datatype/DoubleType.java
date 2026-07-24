package com.syncari.core.datatype;

import org.apache.commons.lang3.StringUtils;

import lombok.EqualsAndHashCode;
import lombok.extern.slf4j.Slf4j;

import java.text.DecimalFormat;
import java.text.DecimalFormatSymbols;
import java.text.NumberFormat;
import java.text.ParseException;
import java.text.ParsePosition;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.function.Function;

import static java.text.NumberFormat.getCurrencyInstance;

@EqualsAndHashCode
@Slf4j
public class DoubleType extends AbstractDataType<Double> {
    public static final DoubleType VALUE = new DoubleType();
    public static final String NAME = "double";

    public static final Map<Class<?>, Function<Object, Double>> CONVERTERS = Map.of(
            String.class, value -> convert(value.toString()),

            Integer.class, value -> ((Integer) value).doubleValue(),

            Long.class, value -> ((Long) value).doubleValue(),

            Float.class, value -> ((Float) value).doubleValue()
    );

    @Override
    public String getName() {
        return NAME;
    }

    @Override
    public Class<Double> getJavaType() {
        return Double.class;
    }

    private static Double convert(String value) {
        try {
            if(StringUtils.isBlank(value)){
                return null;
            }
            String cleanedValue = value.replaceAll("\\s+","");
            try {
                return Double.parseDouble(cleanedValue);
            }catch(NumberFormatException ex){
                //Try other formats
            }
            // Try percentage Format
            try {
                NumberFormat percentageFormat = NumberFormat.getPercentInstance();
                percentageFormat.setMinimumFractionDigits(10);
                return percentageFormat.parse(cleanedValue).doubleValue();
            } catch(ParseException ex){
                //Try other formats
            }
            ParsePosition parsePosition = new ParsePosition(0);
            NumberFormat formatter = NumberFormat.getInstance();
            Number parsed = formatter.parse(cleanedValue, parsePosition);
            if(parsePosition.getIndex() >= cleanedValue.length() ){
                return parsed.doubleValue();
            }else if(DecimalFormat.class.isAssignableFrom(formatter.getClass())){
                parsePosition = new ParsePosition(0);
                DecimalFormat decimalFormatter = (DecimalFormat) formatter;
                DecimalFormatSymbols decimalCommaSymbol = new DecimalFormatSymbols();
                decimalCommaSymbol.setDecimalSeparator(',');
                decimalFormatter.setDecimalFormatSymbols(decimalCommaSymbol);
                parsed = decimalFormatter.parse(cleanedValue, parsePosition);
                if(parsePosition.getIndex() >= cleanedValue.length()){
                    return parsed.doubleValue();
                }else {
                    decimalCommaSymbol.setGroupingSeparator('.');
                    decimalFormatter.setDecimalFormatSymbols(decimalCommaSymbol);
                    parsed = decimalFormatter.parse(cleanedValue, new ParsePosition(0));
                    if(parsed!=null){
                        return parsed.doubleValue();
                    }else{
                        return parseCurrency(cleanedValue);
                    }
                }

            }
        }catch (Exception e){
            log.error(e.getMessage(), e);
        }
        return null;
    }

    private static Double parseCurrency(String cleanedValue) {
        List<NumberFormat> currencies= List.of(getCurrencyInstance(Locale.US)
                ,getCurrencyInstance(Locale.UK)
                ,getCurrencyInstance(Locale.GERMAN)
                ,getCurrencyInstance(Locale.CANADA)
                ,getCurrencyInstance(Locale.JAPAN)
                ,getCurrencyInstance(Locale.KOREA)
                ,getCurrencyInstance(Locale.PRC)
                ,getCurrencyInstance(Locale.TAIWAN)
                ,getCurrencyInstance(Locale.FRANCE)
                ,getCurrencyInstance(Locale.ITALY)
        );
        return currencies.stream().map(c->{
            try{
                return c.parse(cleanedValue).doubleValue();
            }catch (Exception e){
                return null;
            }
        }).filter(v->v!=null).findFirst().orElse(null);
    }

    @Override
    public boolean canConvert(Datatype other) {
        return StringType.VALUE.equals(other) || IntegerType.VALUE.equals(other)  ||  ObjectType.VALUE.equals(other);
    }

    @Override
    protected Map<Class<?>, Function<Object, Double>> getConverters() {
        return CONVERTERS;
    }
    
    @Override
    public boolean isEmpty(Object value){
        return value==null || StringUtils.isEmpty(value.toString());
    }
}
