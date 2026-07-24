package com.syncari.core.changelogs.customer;

import com.github.mongobee.changeset.ChangeLog;
import com.github.mongobee.changeset.ChangeSet;
import com.mongodb.client.MongoCollection;
import com.syncari.core.datatype.Datatype;
import com.syncari.core.datatype.DatatypeFactory;
import com.syncari.core.model.EngineType;
import com.syncari.core.model.util.Scope;
import com.syncari.core.model.util.Type;
import org.bson.Document;
import org.springframework.data.mongodb.core.MongoTemplate;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.stream.Collectors;

import static com.mongodb.client.model.Filters.and;
import static com.mongodb.client.model.Filters.eq;

@ChangeLog(order = "0027")
public class M0027_FixTSetValueDropdown {

    @ChangeSet(order = "001", id = "fixSetValueDropDown", author = "neelesh")
    public void fixSetValueDropDown(MongoTemplate template) {
    	// No-op
    }
}
