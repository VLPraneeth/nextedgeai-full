package com.syncari.connector;

import com.syncari.utils.TextUtil;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.Getter;
import lombok.ToString;
import lombok.experimental.Accessors;
import lombok.experimental.Wither;
import org.apache.commons.lang3.StringUtils;

import java.io.Serializable;
import java.util.*;
import java.util.Map.Entry;

@ToString
@Data
@Accessors(chain = true)
@AllArgsConstructor
@Wither
public class EntityData implements Serializable {
    public static final Set<String> SYSTEM_FIELDS = Set.of("syncariId", "_isNew", "_source");
    public static final Set<String> SYNCARI_DEFINED_FIELDS = Set.of("createdAt", "lastModified", "isDeleted", "lastTransactionLogId",
        "syncariScore", "reparented", "syncariTimestamp", "Id", "LastModifiedDate", "CreatedDate");
    public static final String SYNCARI_FILE_LINK_FIELD_NAME = "syncariFileLink";
    public static final String SYNCARI_FILE_REFERENCE_FIELD_NAME = "syncariFileReferences";
    private String name;
    private String connectorId;
    //epoch millis - lastModified timestamp from the latest synapse that touched this record
    private long lastModified;
    private long createdAt;
    private String lastTransactionLogId;
    private long lastTransactionTimestamp;
    //epoch millis, last time when Syncari saw this document
    private long syncariTimestamp;
    //epoch millis, time when this record was created in Syncari
    private long syncariCreatedAt;
    private String id;
    private boolean isChild = false;
    private String parentId;
    private String syncariEntityId;
    private String syncariParentEntityId;
    @Wither private Map<String, Object> values = new HashMap<>();
    private Map<String, String> caseInsensitiveKeys = new HashMap<>();
    private boolean isDeleted = false;
    private boolean isNew = false;
    private Set<String> ignoreFieldChanges = new HashSet<>();
    private EntityScore syncariScore;
    private String originatingConnectorId;
    //This flag tells whethere the current record was recently reparented
    //due to a merge of a parent record
    private boolean reparented;
    private Map<String, Object> compositeKeyData = new HashMap<>();
    //key is normalized synapsename
    //value is a map of entityAPIName -> external id
    private Map<String, Map<String, String>> externalIds=new HashMap<>();
    private String dedupeHash;
    private Map<String, AttachRecordData> attachRecordData = new HashMap<>();
    private boolean outlierTimestamp;

    public EntityData withValues(Map<String, Object> incoming) {
        if (this.values == incoming) {
            return this;
        }
        Map<String, String> lowerCasekeys = new HashMap<>();
        incoming.forEach((k, v) -> lowerCasekeys.put(k.toLowerCase(), k));
        return new EntityData(name, connectorId, lastModified, createdAt, lastTransactionLogId, lastTransactionTimestamp,
                syncariTimestamp, syncariCreatedAt, id, isChild, parentId, syncariEntityId, syncariParentEntityId, incoming,
                lowerCasekeys, isDeleted, isNew, ignoreFieldChanges, syncariScore, originatingConnectorId, reparented
                , compositeKeyData, externalIds, dedupeHash, attachRecordData, outlierTimestamp
        );
    }

    public EntityData setValues(Map<String, Object> incoming) {
        this.values = incoming;
        this.caseInsensitiveKeys = new HashMap<>();
        incoming.forEach((k, v) -> {
            registerLowerCaseKey(k);
        });
        return this;
    }

    public List<EntityData> getChildrenRecords(String apiName){
        Object value = getValue(apiName);
        if(value==null) return List.of();
        if(List.class.isAssignableFrom(value.getClass())){
            return (List<EntityData>) value;
        }else{
            return List.of((EntityData)value);
        }
    }

    /**
     * Iterates over all child records (list of entitydata or entitydata) and finds a matching ones
     * @param syncariId
     * @return
     */
    public Optional<EntityData> getChildRecord(String syncariId){
        for(Entry<String,Object> entry : values.entrySet()){
            if(entry.getValue()==null) return Optional.empty();
            if(List.class.isAssignableFrom(entry.getValue().getClass())){
                List children = List.class.cast(entry.getValue());
                if(!children.isEmpty() && EntityData.class.isAssignableFrom(children.get(0).getClass())){
                    return children.stream().filter(r->EntityData.class.cast(r).getSyncariEntityId().equals(syncariId)).findFirst();
                }
            }else if (EntityData.class.isAssignableFrom(entry.getValue().getClass()) && EntityData.class.cast(entry.getValue()).getSyncariEntityId().equals(syncariId)){
                return Optional.ofNullable(EntityData.class.cast(entry.getValue()));
            }
        }
        return  Optional.empty();
    }


    public boolean isIgnoredField(String fieldName){
        return ignoreFieldChanges!=null && ignoreFieldChanges.contains(fieldName);
    }

    public EntityData() {
    }

    public EntityData(String name) {
        this.name = name;
    }

    public EntityData addValue(String key, Object value) {
        this.values.put(key, value);
        registerLowerCaseKey(key);
        return this;
    }

    private void registerLowerCaseKey(String key) {
        final String lowerCase = key.toLowerCase();
        if (!lowerCase.equals(key)) {
            caseInsensitiveKeys.put(lowerCase, key);
        }
    }

    public EntityData addCompositeKey(String key, Object value) {
        this.compositeKeyData.put(key, value);
        return this;
    }

    public EntityData addExternalRecordId(String connectorName,String entityName, String externalId) {
        this.externalIds.put(TextUtil.toTokenName(connectorName), Map.of(entityName,externalId));
        return this;
    }
    public Map<String, Map<String,String>> getExternalIds(){
        return externalIds;
    }
    public boolean has(String key) {
        return this.values.containsKey(key);
    }

    public Object getValue(String key) {
        if(StringUtils.isBlank(key)) return null;
        if (this.values.containsKey(key)) return this.values.get(key);
        final String lowerCase = key.toLowerCase();
        if (this.values.containsKey(lowerCase)) return this.values.get(lowerCase);
        return this.values.get(this.caseInsensitiveKeys.get(lowerCase));
    }

    public boolean hasValue(String key) {
        if(StringUtils.isBlank(key)) return false;
        return this.values.containsKey(key) ||
                this.values.containsKey(this.caseInsensitiveKeys.get(key));

    }

    public Comparable getComparable(String key) {
        return (Comparable) this.values.get(key);
    }

    public <T> T getTypedValue(String key) {
        return (T) this.values.get(key);
    }

    public Value getValueObject(String key) {
        return new Value(this.has(key), getValueOptional(key));
    }

    public EntityData remove(String key) {
        this.values.remove(key);
        this.caseInsensitiveKeys.remove(key.toLowerCase());
        return this;
    }

    public Optional<? extends Object> getValueOptional(String key) {
        return Optional.ofNullable(this.values.get(key));
    }

    public String getValueAsString(String key) {
        return this.values.get(key) == null ? null : this.getValue(key).toString();
    }

    public EntityData removeSystemFields() {
        SYSTEM_FIELDS.forEach(f -> remove(f));
        return this;
    }

    /**
     * @param fieldName
     * @param newValue  - NULL is assumed to be a valid value and if field is NOT set
     *                  this method returns true even for a null value
     * @return
     */
    public boolean hasChanges(String fieldName, Object newValue) {

        Value current = getValueObject(fieldName);
        return !isIgnoredField(fieldName) && current.hasChange(newValue);

    }

    public boolean hasChanges(String fieldName, Object newValue, boolean rejectEmptyString) {
        Value current = getValueObject(fieldName);
        if(hasEmptyStringChange(newValue, current, rejectEmptyString)) return false;
        return !isIgnoredField(fieldName) && current.hasChange(newValue);
    }

    private boolean hasEmptyStringChange(Object newValue, Value current, boolean rejectEmptyString) {
        return current.getValue().isPresent() && current.getValue().get() instanceof String &&
                StringUtils.isBlank((String) current.getValue().get()) && newValue == null && rejectEmptyString;
    }

    public boolean hasId() {
        return !StringUtils.isEmpty(id);
    }

    public EntityData setReparented(boolean reparented) {
        this.reparented = reparented;
        return this;
    }

    public boolean isReparented() {
        return reparented;
    }

    public long getLastTransactionTimestamp() {
        return lastTransactionTimestamp > 0 ? lastTransactionTimestamp : createdAt;
    }

    public void setOutlierTimestamp(boolean hasOutlierTimestamp) {
        this.outlierTimestamp = hasOutlierTimestamp;
    }
}

@AllArgsConstructor
@Getter
class Value {
    //this flag allows us to handle nulls as actual values
    private boolean isPresent;
    private Optional<? extends Object> value;

    public boolean hasChange(Object newValue) {
        //Value is not present, so any new value, including a NULL, represents a change
        //if (!isPresent) return true;
        //Existing value is EMPTY, so any nonempty newvalue is a change
        if (value.isEmpty()) return newValue != null;
        ///straight comparision doesnt work specifically for Document and Maps (and lists of documents and maps)
        // the equals implementation in org.bson.Document does not match java Maps, even though its a subclass of Map

        Object v = value.get();
        return !objectsEqual(newValue, v);
    }

    private boolean objectsEqual(Object newValue, Object v) {
        if (v != null && Map.class.isAssignableFrom(v.getClass())) {
            return mapsEqual(newValue, v);
        }
        if (v != null && List.class.isAssignableFrom(v.getClass()) && newValue != null && List.class.isAssignableFrom(newValue.getClass())) {
            return listsEqual(newValue, v);
        }
        return Objects.equals(v, newValue);
    }

    private boolean listsEqual(Object newValue, Object v) {
        List list1 = List.class.cast(newValue);
        List list2 = List.class.cast(v);
        if (list1.size() != list2.size()) {
            return false;
        }
        final Iterator<?> it1 = list1.iterator();
        final Iterator<?> it2 = list2.iterator();
        Object obj1 = null;
        Object obj2 = null;

        while (it1.hasNext() && it2.hasNext()) {
            obj1 = it1.next();
            obj2 = it2.next();

            if (!(obj1 == null ? obj2 == null : objectsEqual(obj1, obj2))) {
                return false;
            }
        }
        return !(it1.hasNext() || it2.hasNext());
    }

    private static boolean mapsEqual(Object newValue, Object v) {
        Map asMap = new LinkedHashMap(Map.class.cast(v));
        return asMap.equals(newValue);
    }

}