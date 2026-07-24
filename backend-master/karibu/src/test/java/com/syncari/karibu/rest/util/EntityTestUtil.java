package com.syncari.karibu.rest.util;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.ObjectWriter;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.syncari.connector.data.CreateFieldRequest;
import com.syncari.core.model.AttributeDefinition;
import com.syncari.core.model.Tag;
import com.syncari.core.model.misc.Taggable;
import com.syncari.karibu.rest.request.CreateSyncariEntityRequest;
import com.syncari.karibu.rest.request.FieldRequest;
import org.bson.types.ObjectId;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

@Component
public class EntityTestUtil {

    public String getNewField (String name, String dataType, Integer length,
                               boolean includeAPIName, boolean includeLength, boolean includePicklist) throws Exception {
        FieldRequest fieldRequest = new FieldRequest();
        if (includeAPIName)
            fieldRequest.setApiName(name);
        fieldRequest.setDisplayName(name);
        fieldRequest.setDatastoreName(name+"Datastore");
        fieldRequest.setDescription(name + " description");
        fieldRequest.setDataType(dataType);
        if (includeLength)
            fieldRequest.setLength(length);
        fieldRequest.setRequired(true);
        if(includePicklist){
            List<String> picklist = new ArrayList<>();
            picklist.add("Pick1");
            picklist.add("Pick2");
            fieldRequest.setPicklistValues(picklist);
        }
        Set<String> tags = new HashSet<>();
        tags.add("Tag1");
        tags.add("Tag2");
        fieldRequest.setTags(tags);

        ObjectMapper mapper = new ObjectMapper();
        mapper.configure(SerializationFeature.WRAP_ROOT_VALUE, false);
        ObjectWriter ow = mapper.writer().withDefaultPrettyPrinter();
        return ow.writeValueAsString(fieldRequest);
    }

    // for test
    public FieldRequest convertAttributeDefinition(AttributeDefinition request) {
        FieldRequest attr = new FieldRequest();
        String attributeId = ObjectId.get().toString();
        attr.setApiName(request.getApiName());
        attr.setDisplayName(request.getDisplayName());
        attr.setDatastoreName(request.getDataStoreName());
        attr.setDataType(request.getDataType().getName());
        if (request.getDescription() != null)
            attr.setDescription(request.getDescription());
        if (request.getLength() > 0 )
            attr.setLength(request.getLength());
        if (request.isMultiValueField())
            attr.setMultiValueField(request.isMultiValueField());
        if (!request.isNillable())
            attr.setRequired(!request.isUnique());
        if (request.isUnique())
            attr.setUnique(request.isUnique());
        if (request.getPicklistValues() != null)
            attr.setPicklistValues(request.getPicklistValues());
        if (request.getTags() != null) {
            var tags = request.getTags().stream()
                    .map(t -> new Tag(t.getName(), true, Taggable.attribute, attributeId))
                    .collect(Collectors.toList());
            attr.setTags(tags.stream().map(t -> t.getName()).collect(Collectors.toSet()));
        }
        return attr;
    }

    public String getNewEntity(CreateSyncariEntityRequest entityRequest)throws Exception{
        ObjectMapper mapper = new ObjectMapper();
        mapper.configure(SerializationFeature.WRAP_ROOT_VALUE, false);
        ObjectWriter ow = mapper.writer().withDefaultPrettyPrinter();
        return ow.writeValueAsString(entityRequest);
    }
}
