package com.syncari.core.model.insights;

import com.syncari.core.exceptions.SyncariValidationException;
import com.syncari.utils.I18n;
import org.apache.commons.collections4.CollectionUtils;

import java.util.LinkedList;
import java.util.List;
import java.util.Optional;

import static com.syncari.utils.I18n.i18n;

public enum AggFunctions {
    COUNT{
        @Override
        public QueryFunction createQueryFunction() {
            func = new CountQueryFunction();
            return func;
        }
        @Override
        public boolean isAggregate(){
            return true;
        }

        @Override
        public String getDataType(){
            return "integer";
        }
    },SUM{
        @Override
        public QueryFunction createQueryFunction() {
            func = new SumQueryFunction();
            return func;
        }
        @Override
        public boolean isAggregate(){
            return true;
        }

        @Override
        public String getDataType(){
            return "double";
        }
    },CONCAT{
        @Override
        public QueryFunction createQueryFunction() {
            func = new ConcatQueryFunction();
            return func;
        }
    }, DATE_TRUNC{
        @Override
        public QueryFunction createQueryFunction() {
            func = new DateTruncQueryFunction();
            return func;
        }
        @Override
        public void addMoreParamsFromColumns(List<QField> fields){
            Optional<QField> truncatedField = Optional.empty();
            if (CollectionUtils.isNotEmpty(fields)){
                // Todo : validate fields api name values as well.
                truncatedField = fields.stream().filter(f -> f.isLiteral()).findFirst();
                truncatedField.ifPresent(tf -> ((DateTruncQueryFunction)func).setTruncatedField(tf.getName()));
            }
            if (truncatedField.isEmpty()){
                throw new SyncariValidationException(i18n("datetrunc_wrong_param"));
            }
        };

        @Override
        public List<String> getInputDataTypes(){
            return List.of("date", "datetime","timestamp");
        }
    },DISTINCT{
        @Override
        public QueryFunction createQueryFunction() {
            func = new DistinctQueryFunction();
            return func;
        }
    },TO_CHAR{
        @Override
        public QueryFunction createQueryFunction() {
            func = new ToCharQueryFunction();
            return func;
        }
        @Override
        public void addMoreParamsFromColumns(List<QField> fields){
            Optional<QField> toCharFieldParam = Optional.empty();
            if (CollectionUtils.isNotEmpty(fields)){
                // Todo : validate fields api name values as well.
                toCharFieldParam = fields.stream().filter(f -> f.isLiteral()).findFirst();
                toCharFieldParam.ifPresent(charf -> ((ToCharQueryFunction)func).setToCharField(charf.getName()));
            }
            if (toCharFieldParam.isEmpty()){
                throw new SyncariValidationException(i18n("tochar_wrong_param"));
            }
        };
    }, DIVIDE {
        @Override
        public QueryFunction createQueryFunction() {
            func = new DivideQueryFunction();
            return func;
        }

        @Override
        public String getDataType() {
            return "double";
        }

    },
    //Adds all its params (a+b+c). Unlike sum, which sums all values for a single field
    ADD {
        @Override
        public QueryFunction createQueryFunction() {
            return new AddQueryFunction();
        }

        @Override
        public String getDataType() {
            return "double";
        }
    },
    SUBTRACT {
        @Override
        public QueryFunction createQueryFunction() {
            return new SubtractQueryFunction();
        }

        @Override
        public String getDataType() {
            return "double";
        }
    },
    GROWTH {
        @Override
        public QueryFunction createQueryFunction() {
            return new GrowthQueryFunction();
        }

        @Override
        public String getDataType() {
            return "double";
        }
    },
    FORMULA {
        @Override
        public QueryFunction createQueryFunction() {
            return new FormulaQueryFunction();
        }

        @Override
        public String getDataType() {
            return "object";
        }
    },
    MULTIPLY {
        @Override
        public QueryFunction createQueryFunction() {
            return new MultiplyQueryFunction();
        }

        @Override
        public String getDataType() {
            return "double";
        }
    },
    REMAINDER {
        @Override
        public QueryFunction createQueryFunction() {
            return new RemainderQueryFunction();
        }

        @Override
        public String getDataType() {
            return "double";
        }
    },
    POWER {
        @Override
        public QueryFunction createQueryFunction() {
            return new PowerQueryFunction();
        }

        @Override
        public String getDataType() {
            return "double";
        }
    },
    NONE {
        @Override
        public QueryFunction createQueryFunction() {
            func = new NoQueryFunction();
            return func;
        }
    }, DATE_PART {
        @Override
        public QueryFunction createQueryFunction() {
            func = new DatePartQueryFunction();
            return func;
        }

        @Override
        public void addMoreParamsFromColumns(List<QField> fields){
            Optional<QField> datePartField = Optional.empty();
            if (CollectionUtils.isNotEmpty(fields)){
                datePartField = fields.stream().filter(f -> f.isLiteral()).findFirst();
                datePartField.ifPresent(tf -> ((DatePartQueryFunction)func).setDatePartField(tf.getName()));
            }
            if (datePartField.isEmpty()){
                throw new SyncariValidationException(i18n("datetpart_wrong_param"));
            }
        };
        @Override
        public String getDataType(){
            return "integer";
        }

        @Override
        public List<String> getInputDataTypes(){
            return List.of("date", "datetime","timestamp");
        }
    }, DATE {
        @Override
        public QueryFunction createQueryFunction() {
            func = new DateQueryFunction();
            return func;
        }

        @Override
        public void addMoreParamsFromColumns(List<QField> fields){
            Optional<QField> datePartField = Optional.of(new QField().setName("yyyy-mm-dd").setType(QField.Type.LITERAL));
            datePartField.ifPresent(tf -> ((ToCharQueryFunction)func).setToCharField(tf.getName()));
        };
        @Override
        public String getDataType(){
            return "string";
        }

        @Override
        public List<String> getInputDataTypes(){
            return List.of("date", "datetime","timestamp");
        }
    },
    MONTH{
        @Override
        public QueryFunction createQueryFunction() {
            func = new DateMonthQueryFunction();
            return func;
        }

        @Override
        public void addMoreParamsFromColumns(List<QField> fields){
            Optional<QField> datePartField = Optional.of(new QField().setName("Mon yyyy").setType(QField.Type.LITERAL));
            datePartField.ifPresent(tf -> ((ToCharQueryFunction)func).setToCharField(tf.getName()));
        };
        @Override
        public String getDataType(){
            return "string";
        }
        @Override
        public List<String> getInputDataTypes(){
            return List.of("date", "datetime","timestamp");
        }
    },YEAR{
        @Override
        public QueryFunction createQueryFunction() {
            func = new DateYearQueryFunction();
            return func;
        }

        @Override
        public void addMoreParamsFromColumns(List<QField> fields){
            Optional<QField> datePartField = Optional.of(new QField().setName("year").setType(QField.Type.LITERAL));
            datePartField.ifPresent(tf -> ((DatePartQueryFunction)func).setDatePartField(tf.getName()));
        };
        @Override
        public String getDataType(){
            return "integer";
        }
        @Override
        public List<String> getInputDataTypes(){
            return List.of("date", "datetime","timestamp");
        }
    },QUARTER{
        @Override
        public QueryFunction createQueryFunction() {
            func = new DateQuarterQueryFunction().setConcatField(false);
            List<QField> fieldsToAdd = new LinkedList<>();
            Optional<QField> qLiteralField = Optional.of(new QField().setName("Q").setType(QField.Type.LITERAL));
            Optional<QField> quarterFunctionField = Optional.of(new QField().setName("quarterField").setType(QField.Type.FUNCTION)
                    .setQueryFunction(new DatePartQueryFunction().setDatePartField("quarter")));
            Optional<QField> dashLiteralField = Optional.of(new QField().setName("-").setType(QField.Type.LITERAL));
            Optional<QField> toCharFunctionField = Optional.of(new QField().setName("yearField").setType(QField.Type.FUNCTION)
                    .setQueryFunction(new ToCharQueryFunction().setToCharField("yyyy")));
            qLiteralField.ifPresent(tf -> fieldsToAdd.add(tf));
            quarterFunctionField.ifPresent(qf -> fieldsToAdd.add(qf));
            dashLiteralField.ifPresent(tcf -> fieldsToAdd.add(tcf));
            toCharFunctionField.ifPresent(tcf -> fieldsToAdd.add(tcf));
            ((DateQuarterQueryFunction)func).setFieldsToConcat(fieldsToAdd);
            return func;
        }

        @Override
        public String getDataType(){
            return "string";
        }

        @Override
        public List<String> getInputDataTypes(){
            return List.of("date", "datetime","timestamp");
        }
    },WEEKNUM{
        @Override
        public QueryFunction createQueryFunction() {
            func = new DateWeekNumQueryFunction();
            return func;
        }

        @Override
        public void addMoreParamsFromColumns(List<QField> fields){
            Optional<QField> datePartField = Optional.of(new QField().setName("week").setType(QField.Type.LITERAL));
            datePartField.ifPresent(tf -> ((DatePartQueryFunction)func).setDatePartField(tf.getName()));
        };
        @Override
        public String getDataType(){
            return "integer";
        }

        @Override
        public List<String> getInputDataTypes(){
            return List.of("date", "datetime","timestamp");
        }
    },DAYOFWEEK{
        @Override
        public QueryFunction createQueryFunction() {
            func = new DateDayOfWeekQueryFunction();
            return func;
        }

        @Override
        public void addMoreParamsFromColumns(List<QField> fields){
            Optional<QField> datePartField = Optional.of(new QField().setName("day").setType(QField.Type.LITERAL));
            datePartField.ifPresent(tf -> ((ToCharQueryFunction) func).setToCharField(tf.getName()));
        }

        ;

        @Override
        public String getDataType() {
            return "string";
        }

        @Override
        public List<String> getInputDataTypes() {
            return List.of("date", "datetime", "timestamp");
        }
    },
    AVG {
        @Override
        public QueryFunction createQueryFunction() {
            return new UnaryAggFunction(AVG, getDataType());
        }

        @Override
        public boolean isAggregate() {
            return true;
        }

        @Override
        public String getDataType() {
            return "double";
        }
    },
    MIN {
        @Override
        public QueryFunction createQueryFunction() {
            return new UnaryAggFunction(MIN, getDataType());
        }

        @Override
        public boolean isAggregate() {
            return true;
        }

        @Override
        public String getDataType() {
            return "double";
        }
    },
    MAX {
        @Override
        public QueryFunction createQueryFunction() {
            return new UnaryAggFunction(MAX, getDataType());
        }

        @Override
        public boolean isAggregate() {
            return true;
        }

        @Override
        public String getDataType() {
            return "double";
        }
    },
    MEDIAN {
        @Override
        public QueryFunction createQueryFunction() {
            return new MedianFunction(getDataType());
        }

        @Override
        public boolean isAggregate() {
            return true;
        }

        @Override
        public String getDataType() {
            return "double";
        }
    },
    PERCENTILE_75 {
        @Override
        public QueryFunction createQueryFunction() {
            return new Percentile75Function(getDataType());
        }

        @Override
        public boolean isAggregate() {
            return true;
        }

        @Override
        public String getDataType() {
            return "double";
        }
    },
    PERCENTILE_25 {
        @Override
        public QueryFunction createQueryFunction() {
            return new Percentile25Function(getDataType());
        }

        @Override
        public boolean isAggregate() {
            return true;
        }

        @Override
        public String getDataType() {
            return "double";
        }
    },
    MODE {
        @Override
        public QueryFunction createQueryFunction() {
            return new ModeFunction(getDataType());
        }

        @Override
        public boolean isAggregate() {
            return true;
        }

        @Override
        public String getDataType() {
            return "double";
        }
    },
    STDDEV_POP {
        @Override
        public QueryFunction createQueryFunction() {
            return new UnaryAggFunction(STDDEV_POP, getDataType());
        }

        @Override
        public boolean isAggregate() {
            return true;
        }

        @Override
        public String getDataType() {
            return "double";
        }
    },
    VAR_POP {
        @Override
        public QueryFunction createQueryFunction() {
            return new UnaryAggFunction(VAR_POP, getDataType());
        }

        @Override
        public boolean isAggregate() {
            return true;
        }

        @Override
        public String getDataType() {
            return "double";
        }
    }
    ;
    //AVG,FIRST,LAST,MAX,MIN,
    QueryFunction func;

    public QueryFunction getQueryFunction() {
        return func;
    }

    public abstract QueryFunction createQueryFunction();

    public void addMoreParamsFromColumns(List<QField> fields) {
    }

    ;

    public void addInnerQueryFields(List<QField> fields) {
    }

    ;

    public String getDataType() {
        return "string";
    }

    public String getDisplayName(){
        return I18n.i18n(String.format("%s_label", name()));
    }
    public boolean isAggregate(){
        return false;
    }

    public String getDescription(){
        return I18n.i18n(String.format("%s_description", name()));
    }

    public List<String> getInputDataTypes(){
        return List.of();
    }

}
