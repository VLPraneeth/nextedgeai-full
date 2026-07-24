package com.syncari.connector.intacct;

import com.thoughtworks.xstream.annotations.XStreamAlias;
import com.thoughtworks.xstream.annotations.XStreamAsAttribute;
import com.thoughtworks.xstream.annotations.XStreamImplicit;
import com.thoughtworks.xstream.annotations.XStreamOmitField;
import lombok.Data;
import lombok.experimental.Accessors;
import org.apache.commons.lang3.math.NumberUtils;

import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.time.Instant;
import java.time.ZoneOffset;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.TimeZone;
import java.util.UUID;
import java.util.stream.Collectors;

@Data
@Accessors(chain = true)
public class IntacctRequest {
    RequestControl control;
    Operation operation;
    static final String DATE_TIME_FORMAT = "MM/dd/yyyy HH:mm:ss";
    static final String DATE_TIME_FORMAT2 = "yyyy-MM-dd'T'HH:mm:ssz";
    static final DateTimeFormatter dateTimeFormatter = DateTimeFormatter.ofPattern(DATE_TIME_FORMAT);
    static final DateTimeFormatter dateTimeFormatter2 = DateTimeFormatter.ofPattern(DATE_TIME_FORMAT2);

    public static Instant toInstant(String value) {
        // First try to parse with the "MM/dd/yyyy HH:mm:ss" format
        SimpleDateFormat simpleDateFormat1 = new SimpleDateFormat(DATE_TIME_FORMAT);
        simpleDateFormat1.setTimeZone(TimeZone.getTimeZone("UTC"));
        Instant instant = toInstant(value, simpleDateFormat1);

        if (instant == null) {
            // Try parsing with the "yyyy-MM-dd'T'HH:mm:ssz" format
            try {
                return ZonedDateTime.from(dateTimeFormatter2.parse(value)).toInstant();
            } catch (DateTimeParseException e) {
                // If both formats fail, try parsing with "MM/dd/yyyy" format for date-only input
                SimpleDateFormat dateOnlyFormat = new SimpleDateFormat("MM/dd/yyyy");
                dateOnlyFormat.setTimeZone(TimeZone.getTimeZone("UTC"));
                try {
                    Date parsedDate = dateOnlyFormat.parse(value);
                    return parsedDate.toInstant();
                } catch (ParseException parseException) {
                    // Handle the error case when the format is still invalid
                    throw new DateTimeParseException("Unable to parse the date: " + value, value, 0);
                }
            }
        }
        return instant;
    }

    private static Instant toInstant(String value, SimpleDateFormat dateFormat) {
        try {
            Date parsed =  dateFormat.parse(value);
            return Instant.ofEpochMilli(parsed.getTime());
        } catch (ParseException e) {
            return null;
        }
    }

    public static String toString(Instant dateTime){
        return ZonedDateTime.ofInstant(dateTime, ZoneOffset.UTC).format(dateTimeFormatter);
    }

    public static IntacctRequest getAPISessionRequest(String senderId, String senderPwd, String userId, String userPwd, String companyId) {
        return new IntacctRequest().setControl(RequestControl.getRequestControl(senderId, senderPwd))
                .setOperation(Operation.getAPISession(userId, userPwd, companyId));
    }

    public static IntacctRequest inspectRequest(String senderId, String senderPwd, String sessionId) {
        return new IntacctRequest().setControl(RequestControl.getRequestControl(senderId, senderPwd))
                .setOperation(Operation.inspect(sessionId));

    }

    public static IntacctRequest lookupObjects(String senderId, String senderPwd, String sessionId, List<String> objects) {
        return new IntacctRequest().setControl(RequestControl.getRequestControl(senderId, senderPwd))
                .setOperation(Operation.lookupObject(sessionId, objects));
    }

    public static IntacctRequest readByQuery(String senderId,
                                             String senderPwd,
                                             String sessionId,
                                             String object,
                                             String watermarkField,
                                             Instant startWatermark,
                                             Instant endWatermark,
                                             int pageSize,
                                             long offset,
                                             List<String> fields) {
        return new IntacctRequest().setControl(RequestControl.getRequestControl(senderId, senderPwd))
                .setOperation(Operation.queryByDate(sessionId, fields, object, watermarkField, startWatermark, endWatermark, offset, pageSize));

    }
    public static IntacctRequest readByIds(String senderId, String senderPwd, String sessionId, String object, List<String> ids) {
        return new IntacctRequest().setControl(RequestControl.getRequestControl(senderId, senderPwd))
                .setOperation(Operation.read(sessionId, object, ids));

    }

    public static IntacctRequest readByNameIds(String senderId, String senderPwd, String sessionId, String object, List<String> ids) {
        return new IntacctRequest().setControl(RequestControl.getRequestControl(senderId, senderPwd))
                .setOperation(Operation.readByName(sessionId, object, ids));

    }

    public static IntacctRequest queryByIds(final String senderId,
                                            final String senderPwd,
                                            final String sessionId,
                                            final List<String> fields,
                                            final String object,
                                            final String entityIdName,
                                            final List<String> ids,
                                            int pageSize) {
        return new IntacctRequest().setControl(RequestControl.getRequestControl(senderId, senderPwd))
                .setOperation(Operation.queryByIds(sessionId, fields, object, entityIdName, ids, pageSize));
    }

    public static IntacctRequest create(String senderId, String senderPwd, String sessionId, String object, Map<String, Object> values) {
        return new IntacctRequest().setControl(RequestControl.getRequestControl(senderId, senderPwd))
                .setOperation(Operation.create(sessionId, object, values));

    }

    public static IntacctRequest update(String senderId, String senderPwd, String sessionId, String object, Map<String, Object> values) {
        return new IntacctRequest().setControl(RequestControl.getRequestControl(senderId, senderPwd))
                .setOperation(Operation.update(sessionId, object, values));

    }

    public static IntacctRequest delete(String senderId, String senderPwd, String sessionId, String object, List<String> ids) {
        return new IntacctRequest().setControl(RequestControl.getRequestControl(senderId, senderPwd))
                .setOperation(Operation.delete(sessionId, object, ids));

    }

    public static IntacctRequest queryWithOffset(final String senderId,
                                                 final String senderPwd,
                                                 final String sessionId,
                                                 final List<String> fields,
                                                 final String object,
                                                 long offset,
                                                 int pageSize) {
        return new IntacctRequest().setControl(RequestControl.getRequestControl(senderId, senderPwd))
                .setOperation(Operation.queryWithOffset(sessionId, fields, object, offset, pageSize));
    }

}

@Data
@Accessors(chain = true)
class Authentication {
    Login login;
    String sessionid;
}

@Data
@Accessors(chain = true)
class Login {
    String userid;
    String companyid;
    String password;
}

@Data
@Accessors(chain = true)
class Content {
    @XStreamImplicit(itemFieldName = "function", keyFieldName = "function")
    List<Function> functions;
}

@Data
@Accessors(chain = true)
class Function {
    private String controlid = UUID.randomUUID().toString();
    private FunctionBody functionBody;
}

interface FunctionBody {
}

//authenticate/refresh token
@Data
@Accessors(chain = true)
class getAPISession implements FunctionBody {

}

//getByWatermark all fields
@Data
@Accessors(chain = true)
@XStreamAlias("readByQuery")
class ReadByQuery implements FunctionBody {
    String object;
    String fields = "*";

    static String defaultQuery = "%s > '%s'";
    String query;
    int pagesize = 1000;

    public ReadByQuery withWatermark(String watermarkField, Instant watermark,int pageSize) {
        query = String.format(defaultQuery, watermarkField, IntacctRequest.toString(watermark));
        this.pagesize = pageSize;
        return this;
    }
}

@Data
@Accessors(chain = true)
class CUDOperation {
    @XStreamOmitField
    String objectName;
    Map<String, Object> objectMap;

    public void CUDOperationObject(String objectName, Map<String, Object> values ){
        this.objectName = objectName;
        objectMap = new HashMap<>();
        values.entrySet().stream().forEach(e-> objectMap.put(e.getKey(), e.getValue()));
    }
}

@Data
@Accessors(chain = true)
@XStreamAlias("create")
class Create extends CUDOperation implements FunctionBody{
    public Create createObject(String objectName, Map<String, Object> values ){
        super.CUDOperationObject(objectName, values);
        return this;
    }
}

@Data
@Accessors(chain = true)
@XStreamAlias("update")
class Update extends CUDOperation implements FunctionBody{
    public Update updateObject(String objectName, Map<String, Object> values ){
        super.CUDOperationObject(objectName, values);
        return this;
    }
}

@Data
@Accessors(chain = true)
@XStreamAlias("delete")
class Delete implements FunctionBody {
    String object;
    String keys;

    public Delete withKeys(List<String> keys) {
        List<String> numbersOnly = keys.stream().filter(key -> NumberUtils.isCreatable(key)).collect(Collectors.toList());
        this.keys=String.join(",",numbersOnly);
        return this;
    }
}

@Data
@Accessors(chain = true)
@XStreamAlias("read")
class Read implements FunctionBody {
    String object;
    String fields = "*";
    String keys;

    public Read withKeys(List<String> keys) {
        List<String> numbersOnly = keys.stream().filter(key -> NumberUtils.isCreatable(key)).collect(Collectors.toList());
        this.keys=String.join(",",numbersOnly);
        return this;
    }
}

@Data
@Accessors(chain = true)
@XStreamAlias("readByName")
class ReadByName implements FunctionBody {
    String object;
    String fields = "*";
    String keys;

    public ReadByName withKeys(List<String> keys) {
        this.keys = String.join(",", keys);
        return this;
    }
}

@Data
@Accessors(chain = true)
@XStreamAlias("readMore")
class ReadMore implements FunctionBody {
    String resultId;
}

//Describe schema of a single object
@Data
@Accessors(chain = true)
@XStreamAlias("lookup")
class Lookup implements FunctionBody {
    String object;

    public static Lookup describe(String object) {
        return new Lookup().setObject(object);
    }
}

//List all objects
@Data
@Accessors(chain = true)
@XStreamAlias("inspect")
class Inspect implements FunctionBody {
    String object = "*";
    @XStreamAsAttribute
    String detail = "0";

    public static Inspect listAll() {
        return new Inspect();
    }

    public static Inspect describe(String object) {
        return new Inspect().setObject(object).setDetail("1");
    }

}

@Data
@Accessors(chain = true)
class Operation {
    Authentication authentication;
    Content content;

    public static Operation getAPISession(String userId, String password, String companyId) {
        return new Operation()
                .setAuthentication(new Authentication()
                        .setLogin(new Login().setUserid(userId).setPassword(password).setCompanyid(companyId)))
                .setContent(new Content().setFunctions(List.of(new Function().setFunctionBody(new getAPISession()))));
    }

    public static Operation inspect(String sessionId) {
        return createOperation(sessionId, new Inspect());
    }

    public static Operation lookupObject(String sessionId, List<String> objects) {
        return createOperation(sessionId,
                objects.stream().map(object -> new Function().setFunctionBody(Lookup.describe(object))).collect(Collectors.toList())
        );
    }

    public static Operation readByQuery(String sessionId, String object, String watermarkField, Instant watermark, int pageSize) {
        return createOperation(sessionId, new ReadByQuery().setObject(object).withWatermark(watermarkField, watermark,pageSize));
    }

    public static Operation queryByDate(final String sessionId,
                                        final List<String> fields,
                                        final String object,
                                        final String watermarkField,
                                        final Instant startDate,
                                        final Instant endDate,
                                        long offset,
                                        int pageSize) {
        Query query = new Query()
                .setObject(object)
                .setSelect(new Select()
                        .setField(fields))
                .setOptions(new Options().setShowprivate(true))
                .setFilter(getQueryFilter(watermarkField, startDate, endDate))
                .setOffset(offset)
                .setPagesize(pageSize)
                .setOrderby(new OrderBy()
                        .setOrder(new Order()
                                .setField(watermarkField)
                                .setAscending("")));
        return createOperation(sessionId, query);
    }

    public static Operation queryWithOffset(final String sessionId,
                                           final List<String> fields,
                                           final String object,
                                           long offset,
                                           int pageSize) {
        Query query = new Query()
                .setObject(object)
                .setSelect(new Select()
                        .setField(fields))
                .setOptions(new Options().setShowprivate(true))
                .setOffset(offset)
                .setPagesize(pageSize)
                .setOrderby(new OrderBy()
                        .setOrder(new Order()
                                .setField("RECORDNO")
                                .setAscending("")));
        return createOperation(sessionId, query);
    }

    public static Operation queryByIds(final String sessionId,
                                       final List<String> fields,
                                       final String object,
                                       final String entityIdName,
                                       final List<String> ids,
                                       int pageSize) {
        Filter filter = new Filter();
        if (ids.size() > 1) {
            filter.setOr(new Or()
                    .setEqualto(ids.stream().map(id -> new EqualTo()
                            .setField(entityIdName)
                            .setValue(id)).collect(Collectors.toList())));
        } else if (ids.size() == 1) {
            filter.setEqualto(new EqualTo()
                    .setField(entityIdName)
                    .setValue(ids.get(0)));
        }
        Query query = new Query()
                .setPagesize(pageSize)
                .setObject(object)
                .setSelect(new Select()
                        .setField(fields))
                .setFilter(filter);
        return createOperation(sessionId, query);
    }

    private static Filter getQueryFilter(final String watermarkField,
                                         final Instant startDate,
                                         final Instant endDate) {
        Filter filter = new Filter();
        if (endDate != null) {
            filter.setBetween(new Between()
                    .setField(watermarkField)
                    .setValue(List.of(IntacctRequest.toString(startDate), IntacctRequest.toString(endDate))));
        } else {
            filter.setGreaterthan(new GreaterThan().setField(watermarkField).setValue(IntacctRequest.toString(startDate)));
        }
        return filter;
    }

    public static Operation read(String sessionId, String object, List<String> ids) {
        return createOperation(sessionId, new Read().setObject(object).withKeys(ids));
    }

    public static Operation readByName(String sessionId, String object, List<String> ids) {
        return createOperation(sessionId, new ReadByName().setObject(object).withKeys(ids));
    }

    public static Operation readMore(String sessionId, String resultId) {
        return createOperation(sessionId, new ReadMore().setResultId(resultId));
    }

    public static Operation create(String sessionId, String objectName, Map<String, Object> values) {
        return createOperation(sessionId, new Create().createObject(objectName, values));
    }

    public static Operation update(String sessionId, String objectName, Map<String, Object> values) {
        return createOperation(sessionId, new Update().updateObject(objectName, values));
    }

    public static Operation delete(String sessionId, String object, List<String> ids) {
        return createOperation(sessionId, new Delete().setObject(object).withKeys(ids));
    }

    private static Operation createOperation(String sessionId, FunctionBody functionBody) {
        return createOperation(sessionId, List.of(new Function().setFunctionBody(functionBody)));
    }

    private static Operation createOperation(String sessionId, List<Function> functions) {
        return new Operation()
                .setAuthentication(new Authentication().setSessionid(sessionId))
                .setContent(new Content().setFunctions(functions));
    }

}

// New Query
@Data
@Accessors(chain = true)
@XStreamAlias("query")
class Query implements FunctionBody{
    private String object;
    private Filter filter;
    private Select select;
    private Options options;
    private long offset;
    private int pagesize;
    private OrderBy orderby;
}

@Data
@Accessors(chain = true)
class Filter {
    private Between between;
    private GreaterThan greaterthan;
    private Or or;
    private EqualTo equalto;
}

@Data
@Accessors(chain = true)
class Between {
    private String field;
    private List<String> value;
}

@Data
@Accessors(chain = true)
class Select {
    private List<String> field;
}

@Data
@Accessors(chain = true)
class OrderBy {
    private Order order;
}

@Data
@Accessors(chain = true)
class Order {
    private String field;
    private String ascending;
}

@Data
@Accessors(chain = true)
class GreaterThan {
    private String field;
    private String value;
}

@Data
@Accessors(chain = true)
class Or {
    private List<EqualTo> equalto;
}

@Data
@Accessors(chain = true)
class EqualTo {
    private String field;
    private String value;
}

@Data
@Accessors(chain = true)
class Options {
    private Boolean showprivate;
}