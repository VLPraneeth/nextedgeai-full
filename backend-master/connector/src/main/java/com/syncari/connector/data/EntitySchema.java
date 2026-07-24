package com.syncari.connector.data;

import com.fasterxml.jackson.annotation.JsonIgnore;
import lombok.AccessLevel;
import lombok.Data;
import lombok.Getter;
import lombok.Setter;
import lombok.experimental.Accessors;
import org.apache.commons.lang3.ArrayUtils;
import org.apache.commons.lang3.StringUtils;

import java.util.*;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

@Data
@Accessors(chain = true)
public class EntitySchema {
    String id;
	String apiName;
	String displayName;
	String pluralName;
	String description;
	boolean custom;
	boolean readOnly;
	int version;
	boolean child;
	List<AttributeSchema> attributes = new ArrayList<>();
	Set<Partition> partitions = new HashSet<>();

	@Getter(value=AccessLevel.NONE)
	@Setter(value= AccessLevel.NONE)
	private Map<String,AttributeSchema> nameToAttributeMap = new HashMap<>();

	//Any custom properties about the entity. Generally used within synapses. and not expected to be stored in core framework
	private Map<String,Object> additionalProperties = new HashMap<>();
	private List<AttributeSchema> sourceParams = new ArrayList<>();
	private List<AttributeSchema> destParams = new ArrayList<>();
	public static final String COMPOSITE_KEY_DELIMETER = "|";

	public EntitySchema() {
	}
	
	public EntitySchema(String apiName, String displayName) {
		this.apiName = apiName;
		this.displayName = displayName;
	}

	public EntitySchema(String apiName) {
		this.apiName = apiName;
	}

	public String getPluralName() {
		return StringUtils.isNotEmpty(pluralName) ? pluralName : apiName + "s";
	}

	public boolean hasField(String apiName) {
		return attributes.stream().filter(f -> apiName.equalsIgnoreCase(f.apiName)).findFirst().isPresent();
	}

	public Optional<AttributeSchema> getField(String apiName) {
		return attributes.stream().filter(f -> apiName.equalsIgnoreCase(f.apiName)).findFirst();
	}

	public Optional<AttributeSchema> getCachedField(String apiName) {
		return Optional.ofNullable(nameToAttributeMap.get(apiName.toLowerCase()));
	}

	private void populateMap() {
		nameToAttributeMap.clear();
		attributes.forEach(this::cacheField);
	}

	private void cacheField(AttributeSchema f) {
		nameToAttributeMap.put(f.getApiName().toLowerCase(), f);
	}

	public Optional<AttributeSchema> getFieldByDisplayName(String displayName) {
	    return attributes.stream().filter(f -> displayName.equalsIgnoreCase(f.displayName)).findFirst();
	}

	public List<AttributeSchema> getReferenceFields() {
		return attributes.stream().filter(f -> f.isReference()).collect(Collectors.toList());
	}

	@JsonIgnore
	public AttributeSchema getWatermarkField() {
		return attributes.stream().filter(f -> f.isWatermarkField()).findFirst().get();
	}
	
	@JsonIgnore
	public Optional<AttributeSchema> getWatermarkAttr() {
		return attributes.stream().filter(f -> f.isWatermarkField()).findFirst();
	}

	public boolean hasWatermarkField() {
		return attributes.stream().filter(f -> f.isWatermarkField()).findFirst().isPresent();
	}
	
	public boolean isWatermarkField(String name) {
		return hasWatermarkField() && getWatermarkField().getApiName().equalsIgnoreCase(name);
	}

	@JsonIgnore
	public AttributeSchema getIdField() {
		Optional<AttributeSchema> first = attributes.stream().filter(f -> f.isIdField()).findFirst();
		return first.orElseThrow(() -> new RuntimeException("Id field not defined for entity "+ this.apiName));
	}

	@JsonIgnore
	@Deprecated(forRemoval = true)
	//"Use getCompositeKeyAttributes instead"
	public List<AttributeSchema> getCompositeKeyFields() {
		AttributeSchema idField = this.getIdField();
		String compositeKeyFields = idField.getCompositeKey();
		List<AttributeSchema> results = new ArrayList<>();
		if (StringUtils.isNotEmpty(compositeKeyFields)){
			String [] compositeKeyFieldsApiNames = compositeKeyFields.split(Pattern.quote(COMPOSITE_KEY_DELIMETER));
			if (ArrayUtils.isNotEmpty(compositeKeyFieldsApiNames)){
				for (String apiName: compositeKeyFieldsApiNames){
						results.add(attributes.stream().filter(x -> x.getApiName().equals(apiName)).findFirst().get());
				}
			}
        }
        return results;
    }

	@JsonIgnore
	public List<AttributeSchema> getCompositeKeyAttributes() {
		AttributeSchema idField = this.getIdField();
		List<AttributeSchema> results = new ArrayList<>();
		if (StringUtils.isNotEmpty(idField.composeKeys())){
			String [] compositeKeyFieldsApiNames = idField.composeKeys().split(Pattern.quote(COMPOSITE_KEY_DELIMETER));
			if (ArrayUtils.isNotEmpty(compositeKeyFieldsApiNames)){
				for (String apiName: compositeKeyFieldsApiNames){
					results.add(attributes.stream().filter(x -> x.getApiName().equals(apiName)).findFirst().get());
				}
			}
		}
		return results;
	}

	public List<String> getCompositeKeyFieldNames() {
		return getCompositeKeyFields().stream().map(x -> x.getApiName()).collect(Collectors.toList());
	}

	@JsonIgnore
    public Optional<AttributeSchema> getCreatedAtField() {
        return attributes.stream().filter(f -> f.isCreatedAtField()).findFirst();
    }

    @JsonIgnore
    public Optional<AttributeSchema> getUpdatedAtField() {
        return attributes.stream().filter(f -> f.isUpdatedAtField()).findFirst();
    }

    @JsonIgnore
    public List<AttributeSchema> getCustomFields() {
        return attributes.stream().filter(f -> f.isCustom()).collect(Collectors.toList());
    }

    public List<AttributeSchema> getFileLinkAttributes() {
        return attributes == null ? new ArrayList<>() : attributes.stream().filter(a -> a.isFileLink())
                .collect(Collectors.toList());
    }

    public boolean hasIdField() {
        return attributes.stream().filter(f -> f.isIdField()).findAny().isPresent();
    }

    public boolean hasCompositeKeyFields() {
        String compositeKeyFields = this.getIdField().getCompositeKey();
        return StringUtils.isNotEmpty(compositeKeyFields);
    }

	public void addField(AttributeSchema field) {
		attributes.add(field);
		cacheField(field);
	}
	
	public void removeField(String apiName) {
		Iterator<AttributeSchema> iterator = attributes.iterator();
		while(iterator.hasNext()){
			AttributeSchema attribute = iterator.next();
			if(attribute.getApiName().equalsIgnoreCase(apiName)){
				iterator.remove();
				removeCacheField(attribute);
			}
		}
	}

	private void removeCacheField(AttributeSchema attribute) {
		nameToAttributeMap.remove(attribute.getApiName().toLowerCase());
	}

	public void addFields(Collection<AttributeSchema> fields) {
		attributes.addAll(fields);
		populateMap();
    }

    public void addPartition(Partition partition) {
        partitions.add(partition);
    }

    public EntitySchema addProperty(String name, Object value) {
        additionalProperties.put(name, value);
        return this;
    }

    public Optional<AttributeSchema> getSourceParam(String name) {
        return sourceParams.stream().filter(s -> s.getApiName().equals(name)).findFirst();
    }

    public Optional<AttributeSchema> getDestinationParam(String name) {
        return destParams.stream().filter(s -> s.getApiName().equals(name)).findFirst();
    }

    /**
     * Adds a new source param, if one with the same apiName doesnt exist.
     * If it exists, the existing param is replaced with sourceParam
     *
     * @param sourceParam
     * @return
     */
    public EntitySchema addSourceParam(AttributeSchema sourceParam) {
        final Optional<AttributeSchema> existing = getSourceParam(sourceParam.apiName);
        existing.ifPresent(existingParam -> {
            sourceParams.remove(existingParam);
        });
        sourceParams.add(sourceParam);
        return this;
    }

    /**
     * Adds a new destination param, if one with the same apiName doesnt exist.
     * If it exists, the existing param is replaced with destinationParam
     *
     * @param destinationParam
     * @return
     */
    public EntitySchema addDestinationParam(AttributeSchema destinationParam) {
        final Optional<AttributeSchema> existing = getDestinationParam(destinationParam.apiName);
        existing.ifPresent(existingParam -> {
            destParams.remove(existingParam);
        });
        destParams.add(destinationParam);
        return this;
    }

    public Optional<Object> getProperty(String name) {
        return Optional.ofNullable(additionalProperties.get(name));
    }

    public <T> Optional<T> getTypedProperty(String name) {
        return Optional.ofNullable((T) additionalProperties.get(name));
    }

    public void setAttributes(List<AttributeSchema> attributes) {
        this.attributes = attributes;
        populateMap();
    }

	public <T> T getAdditionalProperty(String name) {
		return (T) additionalProperties.get(name);
	}
}
