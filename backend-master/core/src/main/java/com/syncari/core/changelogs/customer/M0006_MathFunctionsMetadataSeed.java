package com.syncari.core.changelogs.customer;

import java.util.List;

import com.syncari.connector.Constants;
import com.syncari.core.functions.FunctionConstants;
import org.bson.Document;
import org.springframework.data.mongodb.core.MongoTemplate;

import com.github.mongobee.changeset.ChangeLog;
import com.github.mongobee.changeset.ChangeSet;
import com.mongodb.client.MongoCollection;
import com.syncari.core.datatype.Datatype;
import com.syncari.core.datatype.DatatypeFactory;
import com.syncari.core.model.EngineType;
import com.syncari.core.model.util.Type;
import com.syncari.core.model.util.Scope;

@ChangeLog(order = "0006")
public class M0006_MathFunctionsMetadataSeed {

	@ChangeSet(order = "001", id = "addMathFunctionsMetadataSeed", author = "varsha")
	public void addMathFunctionsMetadataSeed(MongoTemplate template) {
		MongoCollection<Document> functions = template.getCollection("functionDefinition");

		functions.insertOne(new Document("name", "abs")
				.append("displayName", "Abs")
				.append("helpSummary",
						"Returns the absolute value of a double value")
				.append("helpPath", "")
				.append("seeded", true)

				.append("iconPath", "/assets/icons/functions/absolute.svg")
				.append("scope", Scope.ATTRIBUTE.name())
				.append("engineType", EngineType.FUNCTION.name())
				.append("outputType", "double")
				.append("type", Type.STANDARD.name())
				.append("positionalParams", List.of(getParameterDoc("a", DatatypeFactory.getDatatype("double")))));

		functions.insertOne(new Document("name", "add")
				.append("displayName", "Add")
				.append("helpSummary",
						"Returns the sum of its arguments.")
				.append("helpPath", "")
				.append("seeded", true)

				.append("iconPath", "/assets/icons/functions/add.svg")
				.append("scope", Scope.ATTRIBUTE.name())
				.append("engineType", EngineType.FUNCTION.name())
				.append("outputType", "double")
				.append("type", Type.STANDARD.name())
				.append("positionalParams", List.of(getParameterDoc("first", DatatypeFactory.getDatatype("double")), 
						getParameterDoc("second", DatatypeFactory.getDatatype("double")))));
		
		functions.insertOne(new Document("name", "ceil")
				.append("displayName", "Ceil")
				.append("helpSummary",
						"Returns the smallest double value that is greater than or equal to the argument and is equal to a mathematical integer")
				.append("helpPath", "")
				.append("seeded", true)

				.append("iconPath", "/assets/icons/functions/ceiling.svg")
				.append("scope", Scope.ATTRIBUTE.name())
				.append("engineType", EngineType.FUNCTION.name())
				.append("outputType", "double")
				.append("type", Type.STANDARD.name())
				.append("positionalParams", List.of(getParameterDoc("first", DatatypeFactory.getDatatype("double")))));
		
		functions.insertOne(new Document("name", "decrement")
				.append("displayName", "Decrement")
				.append("helpSummary",
						"Returns the argument decremented by one.")
				.append("helpPath", "")
				.append("seeded", true)

				.append("iconPath", "/assets/icons/functions/decrement.svg")
				.append("scope", Scope.ATTRIBUTE.name())
				.append("engineType", EngineType.FUNCTION.name())
				.append("outputType", "double")
				.append("type", Type.STANDARD.name())
				.append("positionalParams", List.of(getParameterDoc("first", DatatypeFactory.getDatatype("double")))));
		
		functions.insertOne(new Document("name", "floor")
				.append("displayName", "Floor")
				.append("helpSummary",
						"Returns the largest (closest to positive infinity) double value that is less than or equal to the argument and is equal to a mathematical integer")
				.append("helpPath", "")
				.append("seeded", true)

				.append("iconPath", "/assets/icons/functions/floor.svg")
				.append("scope", Scope.ATTRIBUTE.name())
				.append("engineType", EngineType.FUNCTION.name())
				.append("outputType", "double")
				.append("type", Type.STANDARD.name())
				.append("positionalParams", List.of(getParameterDoc("first", DatatypeFactory.getDatatype("double")))));
		
		functions.insertOne(new Document("name", "increment")
				.append("displayName", "Increment")
				.append("helpSummary",
						"Returns the argument incremented by one")
				.append("helpPath", "")
				.append("seeded", true)

				.append("iconPath", "/assets/icons/functions/increment.svg")
				.append("scope", Scope.ATTRIBUTE.name())
				.append("engineType", EngineType.FUNCTION.name())
				.append("outputType", "double")
				.append("type", Type.STANDARD.name())
				.append("positionalParams", List.of(getParameterDoc("first", DatatypeFactory.getDatatype("double")))));
		
		functions.insertOne(new Document("name", "max")
				.append("displayName", "Max")
				.append("helpSummary",
						"Returns the greater of the two given double values.")
				.append("helpPath", "")
				.append("seeded", true)

				.append("iconPath", "/assets/icons/functions/max.svg")
				.append("scope", Scope.ATTRIBUTE.name())
				.append("engineType", EngineType.FUNCTION.name())
				.append("outputType", "double")
				.append("type", Type.STANDARD.name())
				.append("positionalParams", List.of(getParameterDoc("first", DatatypeFactory.getDatatype("double")), 
						getParameterDoc("second", DatatypeFactory.getDatatype("double")))));
		
		functions.insertOne(new Document("name", "min")
				.append("displayName", "Min")
				.append("helpSummary",
						"Returns the lesser of the two given double values.")
				.append("helpPath", "")
				.append("seeded", true)

				.append("iconPath", "/assets/icons/functions/min.svg")
				.append("scope", Scope.ATTRIBUTE.name())
				.append("engineType", EngineType.FUNCTION.name())
				.append("outputType", "double")
				.append("type", Type.STANDARD.name())
				.append("positionalParams", List.of(getParameterDoc("first", DatatypeFactory.getDatatype("double")), 
						getParameterDoc("second", DatatypeFactory.getDatatype("double")))));
		
		functions.insertOne(new Document("name", "multiply")
				.append("displayName", "Multiply")
				.append("helpSummary",
						"Returns the product of the arguments.")
				.append("helpPath", "")
				.append("seeded", true)

				.append("iconPath", "/assets/icons/functions/multiply.svg")
				.append("scope", Scope.ATTRIBUTE.name())
				.append("engineType", EngineType.FUNCTION.name())
				.append("outputType", "double")
				.append("type", Type.STANDARD.name())
				.append("positionalParams", List.of(getParameterDoc("first", DatatypeFactory.getDatatype("double")), 
						getParameterDoc("second", DatatypeFactory.getDatatype("double")))));
		
		functions.insertOne(new Document("name", "random")
				.append("seeded", true)
				.append("scope", Scope.ATTRIBUTE.name()));
		
		functions.insertOne(new Document("name", "round")
				.append("displayName", "Round")
				.append("helpSummary",
						"Returns the closest long to the argument, with ties rounding to positive infinity")
				.append("helpPath", "")
				.append("seeded", true)

				.append("iconPath", "/assets/icons/functions/round.svg")
				.append("scope", Scope.ATTRIBUTE.name())
				.append("engineType", EngineType.FUNCTION.name())
				.append("outputType", "double")
				.append("type", Type.STANDARD.name())
				.append("positionalParams", List.of(getParameterDoc("first", DatatypeFactory.getDatatype("double")))));
		
		functions.insertOne(new Document("name", "subtract")
				.append("displayName", "Subtract")
				.append("helpSummary",
						"Returns the difference of the arguments.")
				.append("helpPath", "")
				.append("seeded", true)

				.append("iconPath", "/assets/icons/functions/subtract.svg")
				.append("scope", Scope.ATTRIBUTE.name())
				.append("engineType", EngineType.FUNCTION.name())
				.append("outputType", "double")
				.append("type", Type.STANDARD.name())
				.append("positionalParams", List.of(getParameterDoc("first", DatatypeFactory.getDatatype("double")), 
						getParameterDoc("second", DatatypeFactory.getDatatype("double")))));		
	}

	@ChangeSet(order = "002", id = "addComputeRatio", author = "neelesh")
	public void addComputeRatio(MongoTemplate template) {
		MongoCollection<Document> functions = template.getCollection("functionDefinition");
		functions.insertOne(new Document("name", "computeRatio")
				.append("seeded", true)
				.append("scope", Scope.ATTRIBUTE.name()));
	}

	@ChangeSet(order = "003", id = "autoIncrement", author = "varsha")
	public void autoIncrement(MongoTemplate template) {
		MongoCollection<Document> functions = template.getCollection("functionDefinition");
		functions.insertOne(new Document("name", FunctionConstants.AUTO_INCREMENT)
				.append("seeded", true)
				.append("scope", Scope.ATTRIBUTE.name()));
		functions.insertOne(new Document("name", FunctionConstants.AUTO_INCREMENT_ON_ENTITY)
				.append("seeded", true)
				.append("scope", Scope.ENTITY.name()));
	}

	@ChangeSet(order = "004", id = "moreMathFunctions", author = "varsha")
	public void moreMathFunctions(MongoTemplate template) {
		MongoCollection<Document> functions = template.getCollection("functionDefinition");
		functions.insertOne(new Document("name", "floorOnEntity")
				.append("seeded", true)
				.append("scope", Scope.ENTITY.name()));
		functions.insertOne(new Document("name", "ceilOnEntity")
				.append("seeded", true)
				.append("scope", Scope.ENTITY.name()));
		functions.insertOne(new Document("name", "randomOnEntity")
				.append("seeded", true)
				.append("scope", Scope.ENTITY.name()));
	}
	
	@ChangeSet(order = "005", id = "incrementOnEntity", author = "sibin")
    public void incrementOnEntity(MongoTemplate template) {
        MongoCollection<Document> functions = template.getCollection("functionDefinition");
        functions.insertOne(new Document("name", "incrementOnEntity")
                .append("seeded", true)
                .append("scope", Scope.ENTITY.name()));
    }

	private Document getParameterDoc(String name, Datatype datatype) {
		return new Document("name", name)
				.append("datatype", datatype.getName())
				.append("vararg", false);
	}

}
