package com.syncari.core.changelogs.customer;

import java.util.ArrayList;
import java.util.List;

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

@ChangeLog(order = "0005")
public class M0005_TextFunctionsMetadataSeed {

	@ChangeSet(order = "001", id = "addFunctionsMetadataSeed", author = "varsha")
	public void addFunctionsMetadataSeed(MongoTemplate template) {
		MongoCollection<Document> functions = template.getCollection("functionDefinition");

		functions.insertOne(new Document("name", "concatenate")
				.append("seeded", true)
				.append("scope", Scope.ATTRIBUTE.name()));
		
		functions.insertOne(new Document("name", "lpad")
				.append("seeded", true)
				.append("scope", Scope.ATTRIBUTE.name()));
		
		functions.insertOne(new Document("name", "rpad")
				.append("seeded", true)
				.append("scope", Scope.ATTRIBUTE.name()));
		
		functions.insertOne(new Document("name", "decode")
				.append("displayName", "Decode")
				.append("helpSummary",
						"Decodes the text using the Base64 encoding scheme")
				.append("helpPath", "")
				.append("seeded", true)

				.append("iconPath", "/assets/icons/functions/decode.svg")
				.append("scope", Scope.ATTRIBUTE.name())
				.append("engineType", EngineType.FUNCTION.name())
				.append("outputType", "string")
				.append("type", Type.BUILT_IN.name())
				.append("positionalParams", List.of(getParameterDoc("text", DatatypeFactory.getDatatype("string")))));
		
		functions.insertOne(new Document("name", "decrypt")
				.append("displayName", "Decrypt")
				.append("helpSummary",
						"Decrypts the given input text using the key provided. Make sure to use the key that was used to encrypt the text.")
				.append("helpPath", "")
				.append("seeded", true)

				.append("iconPath", "/assets/icons/functions/decrypt.svg")
				.append("scope", Scope.ATTRIBUTE.name())
				.append("engineType", EngineType.FUNCTION.name())
				.append("outputType", "string")
				.append("type", Type.BUILT_IN.name())
				.append("configuration", getConfigDocs("key","string","Key", "secretKey"))
				.append("positionalParams", List.of(getParameterDoc("text", DatatypeFactory.getDatatype("string")))));

		// setValue on Attribute function
		functions.insertOne(new Document("name", "setValue")
				.append("seeded", true)
				.append("scope", Scope.ATTRIBUTE.name()));

		functions.insertOne(new Document("name", "empty")
				.append("displayName", "Is Empty")
				.append("helpSummary",
						"A function which checks if the given input is empty")
				.append("helpPath", "")
				.append("seeded", true)

				.append("iconPath", "/assets/icons/functions/empty.svg")
				.append("scope", Scope.ATTRIBUTE.name())
				.append("engineType", EngineType.FUNCTION.name())
				.append("outputType", "object")
				.append("type", Type.BUILT_IN.name())
				.append("positionalParams", List.of(getParameterDoc("value", DatatypeFactory.getDatatype("object")))));

		functions.insertOne(new Document("name", "encode")
				.append("displayName", "Encode")
				.append("helpSummary",
						"Encodes the specified text into a String using the Base64 encoding scheme")
				.append("helpPath", "")
				.append("seeded", true)

				.append("iconPath", "/assets/icons/functions/encode.svg")
				.append("scope", Scope.ATTRIBUTE.name())
				.append("engineType", EngineType.FUNCTION.name())
				.append("outputType", "string")
				.append("type", Type.BUILT_IN.name())
				.append("positionalParams", List.of(getParameterDoc("text", DatatypeFactory.getDatatype("string")))));
		
		functions.insertOne(new Document("name", "encrypt")
				.append("displayName", "Encrypt")
				.append("helpSummary",
						"Encrypts the given input text using the key provided.")
				.append("helpPath", "")
				.append("seeded", true)

				.append("iconPath", "/assets/icons/functions/encrypt.svg")
				.append("scope", Scope.ATTRIBUTE.name())
				.append("engineType", EngineType.FUNCTION.name())
				.append("outputType", "string")
				.append("type", Type.BUILT_IN.name())
				.append("positionalParams", List.of(getParameterDoc("text", DatatypeFactory.getDatatype("string")))));

		functions.insertOne(new Document("name", "extractDomain")
				.append("scope", Scope.ATTRIBUTE.name()));

		functions.insertOne(new Document("name", "indexOf")
				.append("seeded", true)
				.append("scope", Scope.ATTRIBUTE.name()));

		functions.insertOne(new Document("name", "length")
				.append("displayName", "Length")
				.append("helpSummary",
						"A function which returns the size of the input object")
				.append("helpPath", "")
				.append("seeded", true)

				.append("iconPath", "/assets/icons/functions/length.svg")
				.append("scope", Scope.ATTRIBUTE.name())
				.append("engineType", EngineType.FUNCTION.name())
				.append("outputType", "integer")
				.append("type", Type.BUILT_IN.name())
				.append("positionalParams", List.of(getParameterDoc("value", DatatypeFactory.getDatatype("object")))));
		
		functions.insertOne(new Document("name", "lower")
				.append("displayName", "Lower")
				.append("helpSummary",
						"A function which takes a string and changes all characters to lower case")
				.append("helpPath", "")
				.append("seeded", true)

				.append("iconPath", "/assets/icons/functions/lowercase.svg")
				.append("scope", Scope.ATTRIBUTE.name())
				.append("engineType", EngineType.FUNCTION.name())
				.append("outputType", "string")
				.append("type", Type.BUILT_IN.name())
				.append("positionalParams", List.of(getParameterDoc("value", DatatypeFactory.getDatatype("string")))));

		functions.insertOne(new Document("name", "ltrim")
				.append("displayName", "Ltrim")
				.append("helpSummary",
						"The ltrim function removes any leading whitespace characters from the given argument.")
				.append("helpPath", "")
				.append("seeded", true)

				.append("iconPath", "/assets/icons/functions/left-trim.svg")
				.append("scope", Scope.ATTRIBUTE.name())
				.append("engineType", EngineType.FUNCTION.name())
				.append("outputType", "string")
				.append("type", Type.BUILT_IN.name())
				.append("positionalParams", List.of(getParameterDoc("value", DatatypeFactory.getDatatype("string")))));

		functions.insertOne(new Document("name", "mask")
				.append("displayName", "Mask")
				.append("helpSummary",
						"A function which takes a text value and masks the values")
				.append("helpPath", "")
				.append("seeded", true)

				.append("iconPath", "/assets/icons/functions/mask.svg")
				.append("scope", Scope.ATTRIBUTE.name())
				.append("engineType", EngineType.FUNCTION.name())
				.append("outputType", "string")
				.append("type", Type.BUILT_IN.name())
				.append("configuration", getConfigDocs("maskCharacter","string","Mask Character", "*"))
				.append("positionalParams", List.of(getParameterDoc("text", DatatypeFactory.getDatatype("string")))));
		
		functions.insertOne(new Document("name", "number_format")
				.append("displayName", "Number Format")
				.append("helpSummary",
						"A function which returns the size of the input object")
				.append("helpPath", "")
				.append("seeded", true)

				.append("iconPath", "/assets/icons/functions/length.svg")
				.append("scope", Scope.ATTRIBUTE.name())
				.append("engineType", EngineType.FUNCTION.name())
				.append("outputType", "string")
				.append("type", Type.BUILT_IN.name())
				.append("configuration", getConfigDocs(
						"noofractionalDigits","integer","Number of Fractional Digits",2,
									"decimalSeparator","string","Decimal Separator",".",
									"groupingSeparator","string","Grouping Separator",","
									))
				.append("positionalParams", List.of(getParameterDoc("number", DatatypeFactory.getDatatype("number")))));

		functions.insertOne(new Document("name", "removeNonPrintable")
				.append("displayName", "Remove Non Printable")
				.append("helpSummary",
						"This function removes all non printable characters from the given text.")
				.append("helpPath", "")
				.append("seeded", true)

				.append("iconPath", "/assets/icons/functions/remove-non-printable.svg")
				.append("scope", Scope.ATTRIBUTE.name())
				.append("engineType", EngineType.FUNCTION.name())
				.append("outputType", "string")
				.append("type", Type.BUILT_IN.name())
				.append("positionalParams", List.of(getParameterDoc("text", DatatypeFactory.getDatatype("string")))));

		functions.insertOne(new Document("name", "replace")
				.append("displayName", "Replace")
				.append("helpSummary",
						"A function which takes a text value and replaces the values")
				.append("helpPath", "")
				.append("seeded", true)

				.append("iconPath", "/assets/icons/functions/replace.svg")
				.append("scope", Scope.ATTRIBUTE.name())
				.append("engineType", EngineType.FUNCTION.name())
				.append("outputType", "string")
				.append("type", Type.BUILT_IN.name())
				.append("positionalParams", List.of(getParameterDoc("text", DatatypeFactory.getDatatype("string")),
						getParameterDoc("args", DatatypeFactory.getDatatype("list")))));

		functions.insertOne(new Document("name", "reverseString")
				.append("displayName", "Reverse String")
				.append("helpSummary",
						"A function which takes a string and reverses the characters")
				.append("helpPath", "")
				.append("seeded", true)

				.append("iconPath", "/assets/icons/functions/reverse-string.svg")
				.append("scope", Scope.ATTRIBUTE.name())
				.append("engineType", EngineType.FUNCTION.name())
				.append("outputType", "string")
				.append("type", Type.STANDARD.name())
				.append("positionalParams", List.of(getParameterDoc("values", DatatypeFactory.getDatatype("string")))));

		functions.insertOne(new Document("name", "rtrim")
				.append("displayName", "Rtrim")
				.append("helpSummary",
						"The rtrim function removes any leading whitespace characters from the given argument.")
				.append("helpPath", "")
				.append("seeded", true)

				.append("iconPath", "/assets/icons/functions/right-trim.svg")
				.append("scope", Scope.ATTRIBUTE.name())
				.append("engineType", EngineType.FUNCTION.name())
				.append("outputType", "string")
				.append("type", Type.STANDARD.name())
				.append("positionalParams", List.of(getParameterDoc("value", DatatypeFactory.getDatatype("string")))));

		functions.insertOne(new Document("name", "split")
				.append("displayName", "Split")
				.append("helpSummary",
						"A function which takes a text value and splits the values")
				.append("helpPath", "")
				.append("seeded", true)

				.append("iconPath", "/assets/icons/functions/split.svg")
				.append("scope", Scope.ATTRIBUTE.name())
				.append("engineType", EngineType.FUNCTION.name())
				.append("outputType", "list")
				.append("type", Type.BUILT_IN.name())
				.append("configuration", getConfigDocs("delimiter","string","Delimiter", ",")
				)

				.append("positionalParams", List.of(getParameterDoc("text", DatatypeFactory.getDatatype("string")))));
		
		functions.insertOne(new Document("name", "striptags")
				.append("displayName", "Strip Tags")
				.append("helpSummary",
						"A function which takes a text value and strips any tags")
				.append("helpPath", "")
				.append("seeded", true)

				.append("iconPath", "/assets/icons/functions/strip-tags.svg")
				.append("scope", Scope.ATTRIBUTE.name())
				.append("engineType", EngineType.FUNCTION.name())
				.append("outputType", "string")
				.append("type", Type.BUILT_IN.name())
				.append("configuration", getConfigDocs("allowedTags","string","Allowed Tags", "")
				)
				.append("positionalParams", List.of(getParameterDoc("text", DatatypeFactory.getDatatype("string")))));

		functions.insertOne(new Document("name", "substring")
				.append("displayName", "Substring")
				.append("helpSummary",
						"The substring function gives back a portion of the original text using the provide begin index and end index.")
				.append("helpPath", "")
				.append("seeded", true)

				.append("iconPath", "/assets/icons/functions/substring.svg")
				.append("scope", Scope.ATTRIBUTE.name())
				.append("engineType", EngineType.FUNCTION.name())
				.append("outputType", "string")
				.append("type", Type.BUILT_IN.name())
				.append("configuration",
						getConfigDocs("startIndex","integer","Start Index",0,"endIndex","integer","End Index",0)
				)
				.append("positionalParams", List.of(getParameterDoc("text", DatatypeFactory.getDatatype("string")))));
		
		functions.insertOne(new Document("name", "camelCase")
				.append("displayName", "Camel Case")
				.append("helpSummary",
						"A function which takes a text value and makes it a title")
				.append("helpPath", "")
				.append("seeded", true)

				.append("iconPath", "/assets/icons/functions/camel-case.svg")
				.append("scope", Scope.ATTRIBUTE.name())
				.append("engineType", EngineType.FUNCTION.name())
				.append("outputType", "string")
				.append("type", Type.BUILT_IN.name())
				.append("positionalParams", List.of(getParameterDoc("text", DatatypeFactory.getDatatype("string")))));

		functions.insertOne(new Document("name", "translate")
				.append("displayName", "Translate Text")
				.append("helpSummary",
						"A function which takes a text and translate it to the desired language")
				.append("helpPath", "")
				.append("seeded", true)

				.append("iconPath", "/assets/icons/functions/translate.svg")
				.append("scope", Scope.ATTRIBUTE.name())
				.append("engineType", EngineType.FUNCTION.name())
				.append("outputType", "string")
				.append("type", Type.BUILT_IN.name())
				.append("configuration", getConfigDocs("targetLanguage","string","Target Language", "English"))
				.append("positionalParams", List.of(getParameterDoc("text", DatatypeFactory.getDatatype("string")))));

		functions.insertOne(new Document("name", "trim")
				.append("displayName", "Trim")
				.append("helpSummary",
						"A function which takes a string and trims the whitespaces at the begining and the end")
				.append("helpPath", "")
				.append("seeded", true)

				.append("iconPath", "/assets/icons/functions/trim.svg")
				.append("scope", Scope.ATTRIBUTE.name())
				.append("engineType", EngineType.FUNCTION.name())
				.append("outputType", "string")
				.append("type", Type.BUILT_IN.name())
				.append("positionalParams", List.of(getParameterDoc("value", DatatypeFactory.getDatatype("string")))));
		
		functions.insertOne(new Document("name", "upper")
				.append("displayName", "Upper")
				.append("helpSummary",
						"A function which takes a string and changes all characters to upper case")
				.append("helpPath", "")
				.append("seeded", true)

				.append("iconPath", "/assets/icons/functions/upper.svg")
				.append("scope", Scope.ATTRIBUTE.name())
				.append("engineType", EngineType.FUNCTION.name())
				.append("outputType", "string")
				.append("type", Type.BUILT_IN.name())
				.append("positionalParams", List.of(getParameterDoc("value", DatatypeFactory.getDatatype("string")))));

		functions.insertOne(new Document("name", "url_encode")
				.append("displayName", "Url Encode")
				.append("helpSummary",
						"A function which takes an url and encodes it")
				.append("helpPath", "")
				.append("seeded", true)

				.append("iconPath", "/assets/icons/functions/url-encode.svg")
				.append("scope", Scope.ATTRIBUTE.name())
				.append("engineType", EngineType.FUNCTION.name())
				.append("outputType", "string")
				.append("type", Type.BUILT_IN.name())
				.append("positionalParams", List.of(getParameterDoc("url", DatatypeFactory.getDatatype("string")))));
		
		functions.insertOne(new Document("name", "uuid")
				.append("displayName", "Uuid")
				.append("helpSummary",
						"This function gives a randomly generated UUID. The UUID is generated using a cryptographically strong pseudo random number generator.")
				.append("helpPath", "")
				.append("seeded", true)

				.append("iconPath", "/assets/icons/functions/uuid.svg")
				.append("scope", Scope.ATTRIBUTE.name())
				.append("engineType", EngineType.FUNCTION.name())
				.append("outputType", "string")
				.append("type", Type.STANDARD.name())
				.append("positionalParams", List.of()));

	}

	@ChangeSet(order = "002", id = "moreTextFunctions", author = "varsha")
	public void moreTextFunctions(MongoTemplate template) {
		MongoCollection<Document> functions = template.getCollection("functionDefinition");
		functions.insertOne(new Document("name", "lengthOnEntity")
				.append("seeded", true)
				.append("scope", Scope.ENTITY.name()));
		functions.insertOne(new Document("name", "substringOnEntity")
				.append("seeded", true)
				.append("scope", Scope.ENTITY.name()));
		functions.insertOne(new Document("name", FunctionConstants.SPLIT_ON_ENTITY)
				.append("seeded", true)
				.append("scope", Scope.ENTITY.name()));
		functions.insertOne(new Document("name", "lowerOnEntity")
				.append("seeded", true)
				.append("scope", Scope.ENTITY.name()));
		functions.insertOne(new Document("name", "charAtOnEntity")
				.append("seeded", true)
				.append("scope", Scope.ENTITY.name()));
		functions.insertOne(new Document("name", "charAt")
				.append("seeded", true)
				.append("scope", Scope.ATTRIBUTE.name()));
	}

	@ChangeSet(order = "003", id = "moreTextFunctions1", author = "varsha")
	public void moreTextFunctions1(MongoTemplate template) {
		MongoCollection<Document> functions = template.getCollection("functionDefinition");
		functions.insertOne(new Document("name", "upperOnEntity")
				.append("seeded", true)
				.append("scope", Scope.ENTITY.name()));
		functions.insertOne(new Document("name", "multiplyOnEntity")
				.append("seeded", true)
				.append("scope", Scope.ENTITY.name()));
	}

	@ChangeSet(order = "004", id = "moreTextFunctions2", author = "varsha")
	public void moreTextFunctions2(MongoTemplate template) {
		MongoCollection<Document> functions = template.getCollection("functionDefinition");
		functions.insertOne(new Document("name", "jwtToken")
				.append("seeded", true)
				.append("scope", Scope.ATTRIBUTE.name()));
		functions.insertOne(new Document("name", "jwtTokenOnEntity")
				.append("seeded", true)
				.append("scope", Scope.ENTITY.name()));
	}

	private List<Document> getConfigDocs(Object... arguments) {
		List<Document> config = new ArrayList<>();
		for(int i=0;i<arguments.length;i+=4){
			config.add(new Document("name", arguments[i].toString()).append("datatype", arguments[i+1].toString())
					//Label is set to datatype!!
					//Fixed in M0026
					.append("label", arguments[i+1].toString())
					.append("defaultValue", arguments[i+2]));
		}
		return config;
	}

	private Document getParameterDoc(String name, Datatype datatype) {
		return new Document("name", name)
				.append("datatype", datatype.getName())
				.append("vararg", false);
	}

}
