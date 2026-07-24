package com.syncari.dbm.customscripts.customer;

import com.github.mongobee.changeset.ChangeSet;
import com.syncari.core.MigrationContext;
import com.syncari.core.SyncariContext;
import com.syncari.core.model.insights.Datacard;
import com.syncari.core.model.insights.VizConfig;
import com.syncari.core.model.insights.dataset.Dataset;
import com.syncari.core.model.insights.dataset.Variable;
import com.syncari.core.model.insights.dataset.VariableValue;
import com.syncari.core.repositories.customer.DatacardRepo;
import com.syncari.core.repositories.customer.DatasetRepo;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.collections4.CollectionUtils;
import org.apache.commons.lang3.StringUtils;
import org.springframework.data.mongodb.core.MongoTemplate;

import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.time.temporal.TemporalAdjusters;
import java.util.Calendar;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Slf4j
public class SYN_11014_RemoveRelativeTime {

    @ChangeSet(order = "001", id = "removeRelativeTime", author = "rohit", runAlways = true)
    public void removeRelativeTime(MongoTemplate mongoTemplate) {
        boolean dryRun = Boolean.parseBoolean(System.getProperty("dryRun"));
        String instanceId = SyncariContext.getSyncariId();
        DatasetRepo datasetRepo = MigrationContext.getDatasetRepo();
        List<Dataset> datasets = datasetRepo.findAllActiveDatasetsWithVariables();
        List<Dataset> filteredDatsets = datasets.stream().filter(d -> {
           Map<String, Variable> variableMap = d.getVariablesMap();
           Collection<Variable> values = variableMap.values();
           List<Variable> relativetime = values.stream().filter(v -> v.getDatatype().equalsIgnoreCase("relativetime")).collect(Collectors.toList());
           return CollectionUtils.isNotEmpty(relativetime);
        }).collect(Collectors.toList());
        if (CollectionUtils.isNotEmpty(filteredDatsets)){
            log.info("SyncariId {} number of relativetime datasets are {}", instanceId, filteredDatsets.size());
            // fix relative time datasets
            for (Dataset fD : filteredDatsets){
                Map<String, Variable> varMap = fD.getVariablesMap();
                varMap.forEach((k,v) -> {
                    String dataType = v.getDatatype();
                    VariableValue variableValue = v.getVariableValue();
                    Object defaultVal = variableValue.getDefaultValue();
                    Map<String, Object> additionalParamForDefaultVal = variableValue.getAdditionalParamForDefaultVal();

                    if (StringUtils.isNotEmpty(dataType) && (dataType.equalsIgnoreCase("relativetime"))){
                        Object val = additionalParamForDefaultVal.getOrDefault("param",0);
                        Integer integerVal = val instanceof String ? Integer.valueOf((String)val) : val instanceof Integer ? (int)val : Integer.valueOf(val.toString());
                        log.info("Default Val {} and param {} before conversion", defaultVal, integerVal);
                        String toBeDefaultVal = convertVariableValue((String)defaultVal, (integerVal));
                        if (!dryRun){
                            v.setDatatype("datetime");
                            variableValue.setDefaultValue(toBeDefaultVal);
                            variableValue.setDatatype("LITERAL");
                            variableValue.setAdditionalParamForDefaultVal(null);
                            datasetRepo.save(fD);
                            log.info("Updating variable for syncariId {} as running in dry run mode but existing variable value is {}",instanceId, variableValue);
                            log.info("For dataset {} Updated default value could be {} and datatype would be datetime",fD.getName(),toBeDefaultVal);
                        }else{
                            log.info("Not updating variable for syncariId {} as running in dry run mode but variable value is {}",instanceId, variableValue);
                            log.info("For dataset {} Updated default value could be {} and datatype would be datetime",fD.getName(),toBeDefaultVal);
                        }
                    }
                });
            }
        }
    }

    @ChangeSet(order = "002", id = "removeRelativeTimeFromDatacard", author = "rohit", runAlways = true)
    public void removeRelativeTimeFromDatacard(MongoTemplate mongoTemplate) {
        boolean dryRun = Boolean.parseBoolean(System.getProperty("dryRun"));
        String instanceId = SyncariContext.getSyncariId();
        DatacardRepo datacardRepo = MigrationContext.getDatacardRepo();
        List<Datacard> datacards = datacardRepo.findAllActiveDatacardsWithVariables();

        List<Datacard> filteredDatacards = datacards.stream().filter(d -> {
            Map<String, Variable> variableMap = d.getContents().get(0).getConfig().getVariablesMap();
            Collection<Variable> values = variableMap.values();
            List<Variable> relativetime = values.stream().filter(v -> v.getDatatype().equalsIgnoreCase("relativetime")).collect(Collectors.toList());
            return CollectionUtils.isNotEmpty(relativetime);
        }).collect(Collectors.toList());
        if (CollectionUtils.isNotEmpty(filteredDatacards)){
            log.info("SyncariId {} number of relativetime datacards are {}", instanceId, filteredDatacards.size());
            // fix relative time datasets
            for (Datacard fD : filteredDatacards){
                VizConfig config = fD.getContents().get(0).getConfig();
                Map<String, Variable> varMap = config.getVariablesMap();
                varMap.forEach((k,v) -> {
                    String dataType = v.getDatatype();
                    VariableValue variableValue = v.getVariableValue();
                    Object defaultVal = variableValue.getDefaultValue();
                    Map<String, Object> additionalParamForDefaultVal = variableValue.getAdditionalParamForDefaultVal();

                    if (StringUtils.isNotEmpty(dataType) && (dataType.equalsIgnoreCase("relativetime"))){
                        Object val = additionalParamForDefaultVal.getOrDefault("param",0);
                        Integer integerVal = val instanceof String ? Integer.valueOf((String)val) : val instanceof Integer ? (int)val : Integer.valueOf(val.toString());
                        log.info("Default Val {} and param {} before conversion for datacards", defaultVal, integerVal);
                        String toBeDefaultVal = convertVariableValue((String)defaultVal, (integerVal));
                        if (!dryRun){
                            v.setDatatype("datetime");
                            variableValue.setDefaultValue(toBeDefaultVal);
                            variableValue.setDatatype("LITERAL");
                            variableValue.setAdditionalParamForDefaultVal(null);
                            datacardRepo.save(fD);
                            log.info("Datacards: Updating variable for syncariId {} as running in dry run mode but existing variable value is {}",instanceId, variableValue);
                            log.info("For datacard {} Updated default value could be {} and datatype would be datetime",fD.getName(),toBeDefaultVal);
                        }else{
                            log.info("Datacards: Not updating variable for syncariId {} as running in dry run mode but variable value is {}",instanceId, variableValue);
                            log.info("For datacard {} Updated default value could be {} and datatype would be datetime",fD.getName(),toBeDefaultVal);
                        }
                    }
                });
            }
        }
    }

    private String convertVariableValue(String defaultVal, Integer param){
        if (StringUtils.isNotEmpty(defaultVal)){
            if (defaultVal.equalsIgnoreCase("Last_N_Seconds")){
                return String.format("last %s seconds", param);
            }else if (defaultVal.equalsIgnoreCase("Last_N_Minutes")){
                return String.format("last %s minutes", param);
            }else if (defaultVal.equalsIgnoreCase("Last_N_Hours")){
                return String.format("last %s hours", param);
            }else if (defaultVal.equalsIgnoreCase("Last_N_Days")){
                return String.format("last %s days", param);
            }else if (defaultVal.equalsIgnoreCase("Last_N_Weeks")){
                return String.format("last %s weeks", param);
            }else if (defaultVal.equalsIgnoreCase("Last_N_Months")){
                return String.format("last %s months", param);
            }
            else if (defaultVal.equalsIgnoreCase("Last_N_Quarters")){
                return String.format("last %s days", getNumberOFDaysOfNQuarters(param));
            }else if (defaultVal.equalsIgnoreCase("Last_N_Years")){
                return String.format("last %s years", param);
            }else if (defaultVal.equalsIgnoreCase("Today")){
                return "today";
            }else if (defaultVal.equalsIgnoreCase("This_Week")){
                return "this week";
            }else if (defaultVal.equalsIgnoreCase("This_Month")){
                return "this month";
            }else if (defaultVal.equalsIgnoreCase("This_Quarter")){
                return "this quarter";
            }else if (defaultVal.equalsIgnoreCase("This_Year")){
                return "this year";
            }
        }
        return null;
    }

    /*public static void main(String args[]){
        System.out.println(new DatetimeType().convert(String.format("last %s days", new SYN_11014_RemoveRelativeTime().getCurrentQuarterFirsDateMinusCurrentDate())) );
        System.out.println(new DatetimeType().convert(String.format("last %s days", new SYN_11014_RemoveRelativeTime().getNumberOFDaysOfNQuarters(2))) );
    }*/

    public int getThisQuarterMonthsNumber(){
        return (Calendar.getInstance().get(Calendar.MONTH)%3);
    }

    private static long getCurrentQuarterFirsDateMinusCurrentDate(){
        LocalDateTime localDate = LocalDateTime.now();
        LocalDateTime t  = localDate.with(localDate.getMonth().firstMonthOfQuarter())
                .with(TemporalAdjusters.firstDayOfMonth());
        return ChronoUnit.DAYS.between(t,localDate);
    }

    private static long getNumberOFDaysOfNQuarters(int n){
        LocalDateTime localDate = LocalDateTime.now();
        LocalDateTime lastNthQuarterMonth = localDate.minusMonths((n-1)*3);
        LocalDateTime t  = lastNthQuarterMonth.with(lastNthQuarterMonth.getMonth().firstMonthOfQuarter())
                .with(TemporalAdjusters.firstDayOfMonth());
        return ChronoUnit.DAYS.between(t,localDate);
    }
}
