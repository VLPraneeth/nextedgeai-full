package com.syncari.core.changelogs.customer;

import static com.mongodb.client.model.Filters.and;
import static com.mongodb.client.model.Filters.eq;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import org.bson.Document;
import org.springframework.data.mongodb.core.MongoTemplate;

import com.github.mongobee.changeset.ChangeLog;
import com.github.mongobee.changeset.ChangeSet;
import com.mongodb.client.MongoCollection;
import com.mongodb.client.model.UpdateOptions;
import com.syncari.core.datatype.Datatype;
import com.syncari.core.datatype.DatatypeFactory;
import com.syncari.core.model.EngineType;
import com.syncari.core.model.util.Scope;
import com.syncari.core.model.util.Type;

@ChangeLog(order = "0026")
public class M0026_FixTextFunctionsMetadataSeed {

    @ChangeSet(order = "001", id = "fixFunctionsMetadataSeed", author = "varsha")
    public void fixFunctionsMetadataSeed(MongoTemplate template) {
        MongoCollection<Document> functions = template.getCollection("functionDefinition");

        functions.replaceOne(and(eq("name", "decode"), eq("scope", Scope.ATTRIBUTE.name())), new Document("name", "decode")
                .append("displayName", "Decode")
                .append("helpSummary",
                        "Decodes the text using the Base64 encoding scheme")
                .append("helpPath", "")
                .append("seeded", true)

                .append("iconPath", "/assets/icons/functions/decode.svg")
                .append("scope", Scope.ATTRIBUTE.name())
                .append("engineType", EngineType.FUNCTION.name())
                .append("outputType", "string")
                .append("type", Type.STANDARD.name())
                .append("positionalParams", List.of(getParameterDoc("text", DatatypeFactory.getDatatype("string")))));

        functions.replaceOne(and(eq("name", "decrypt"), eq("scope", Scope.ATTRIBUTE.name())), new Document("name", "decrypt")
                .append("displayName", "Decrypt")
                .append("helpSummary",
                        "Decrypts the given input text using the key provided. Make sure to use the key that was used to encrypt the text.")
                .append("helpPath", "")
                .append("seeded", true)

                .append("iconPath", "/assets/icons/functions/decrypt.svg")
                .append("scope", Scope.ATTRIBUTE.name())
                .append("engineType", EngineType.FUNCTION.name())
                .append("outputType", "string")
                .append("type", Type.STANDARD.name())
                .append("configuration", getConfigDocs("key", "string", "Key", "secretKey"))
                .append("positionalParams", List.of(getParameterDoc("text", DatatypeFactory.getDatatype("string")))));

        functions.replaceOne(and(eq("name", "empty"), eq("scope", Scope.ATTRIBUTE.name())), new Document("name", "isEmpty")
                .append("displayName", "Is Empty")
                .append("helpSummary",
                        "A function which returns true if the given input is empty, and false otherwise")
                .append("helpPath", "")
                .append("seeded", true)

                .append("iconPath", "/assets/icons/functions/empty.svg")
                .append("scope", Scope.ATTRIBUTE.name())
                .append("engineType", EngineType.FUNCTION.name())
                .append("outputType", "object")
                .append("type", Type.STANDARD.name())
                .append("positionalParams", List.of(getParameterDoc("value", DatatypeFactory.getDatatype("object")))));

        functions.replaceOne(and(eq("name", "encode"), eq("scope", Scope.ATTRIBUTE.name())), new Document("name", "encode")
                .append("displayName", "Encode")
                .append("helpSummary",
                        "Encodes the specified text into a String using the Base64 encoding scheme")
                .append("helpPath", "")
                .append("seeded", true)

                .append("iconPath", "/assets/icons/functions/encode.svg")
                .append("scope", Scope.ATTRIBUTE.name())
                .append("engineType", EngineType.FUNCTION.name())
                .append("outputType", "string")
                .append("type", Type.STANDARD.name())
                .append("positionalParams", List.of(getParameterDoc("text", DatatypeFactory.getDatatype("string")))));

        functions.replaceOne(and(eq("name", "encrypt"), eq("scope", Scope.ATTRIBUTE.name())), new Document("name", "encrypt")
                .append("displayName", "Encrypt")
                .append("helpSummary",
                        "Encrypts the given input text using the key provided.")
                .append("helpPath", "")
                .append("seeded", true)

                .append("iconPath", "/assets/icons/functions/encrypt.svg")
                .append("scope", Scope.ATTRIBUTE.name())
                .append("engineType", EngineType.FUNCTION.name())
                .append("outputType", "string")
                .append("type", Type.STANDARD.name())
                .append("positionalParams", List.of(getParameterDoc("text", DatatypeFactory.getDatatype("string")))));

        functions.replaceOne(and(eq("name", "indexOf"), eq("scope", Scope.ATTRIBUTE.name())), new Document("name", "indexOf")
                .append("displayName", "Index Of")
                .append("helpSummary",
                        "A function which returns the starting index of the search string inside the input. Returns -1 if search string is not found.")
                .append("helpPath", "")
                .append("seeded", true)

                .append("iconPath", "/assets/icons/functions/index-of.svg")
                .append("scope", Scope.ATTRIBUTE.name())
                .append("engineType", EngineType.FUNCTION.name())
                .append("outputType", "integer")
                .append("type", Type.STANDARD.name())
                .append("configuration", getConfigDocs("searchString", "string", "Search String", " ")
                )

                .append("positionalParams", List.of(getParameterDoc("text", DatatypeFactory.getDatatype("string")))));

        functions.replaceOne(and(eq("name", "length"), eq("scope", Scope.ATTRIBUTE.name())), new Document("name", "length")
                .append("displayName", "Length")
                .append("helpSummary",
                        "A function which returns the size of the input object")
                .append("helpPath", "")
                .append("seeded", true)

                .append("iconPath", "/assets/icons/functions/length.svg")
                .append("scope", Scope.ATTRIBUTE.name())
                .append("engineType", EngineType.FUNCTION.name())
                .append("outputType", "integer")
                .append("type", Type.STANDARD.name())
                .append("positionalParams", List.of(getParameterDoc("value", DatatypeFactory.getDatatype("object")))));

        functions.replaceOne(and(eq("name", "lower"), eq("scope", Scope.ATTRIBUTE.name())), new Document("name", "lower")
                .append("displayName", "Lower")
                .append("helpSummary",
                        "A function which takes a string and changes all characters to lower case")
                .append("helpPath", "")
                .append("seeded", true)

                .append("iconPath", "/assets/icons/functions/lowercase.svg")
                .append("scope", Scope.ATTRIBUTE.name())
                .append("engineType", EngineType.FUNCTION.name())
                .append("outputType", "string")
                .append("type", Type.STANDARD.name())
                .append("positionalParams", List.of(getParameterDoc("value", DatatypeFactory.getDatatype("string")))));

        functions.replaceOne(and(eq("name", "ltrim"), eq("scope", Scope.ATTRIBUTE.name())), new Document("name", "ltrim")
                .append("displayName", "Ltrim")
                .append("helpSummary",
                        "The ltrim function removes any leading whitespace characters from the input.")
                .append("helpPath", "")
                .append("seeded", true)

                .append("iconPath", "/assets/icons/functions/left-trim.svg")
                .append("scope", Scope.ATTRIBUTE.name())
                .append("engineType", EngineType.FUNCTION.name())
                .append("outputType", "string")
                .append("type", Type.STANDARD.name())
                .append("positionalParams", List.of(getParameterDoc("value", DatatypeFactory.getDatatype("string")))));

        functions.replaceOne(and(eq("name", "mask"), eq("scope", Scope.ATTRIBUTE.name())), new Document("name", "mask")
                .append("displayName", "Mask")
                .append("helpSummary",
                        "A function which replaces each letter of the input with the mask value")
                .append("helpPath", "")
                .append("seeded", true)

                .append("iconPath", "/assets/icons/functions/mask.svg")
                .append("scope", Scope.ATTRIBUTE.name())
                .append("engineType", EngineType.FUNCTION.name())
                .append("outputType", "string")
                .append("type", Type.STANDARD.name())
                .append("configuration", getConfigDocs("maskCharacter", "string", "Mask Character", "*"))
                .append("positionalParams", List.of(getParameterDoc("text", DatatypeFactory.getDatatype("string")))));

        functions.replaceOne(and(eq("name", "number_format"), eq("scope", Scope.ATTRIBUTE.name())), new Document("name", "numberFormat")
                .append("displayName", "Number Format")
                .append("helpSummary",
                        "A function which formats the input number based on configuration and returns it as a string")
                .append("helpPath", "")
                .append("seeded", true)

                .append("iconPath", "/assets/icons/functions/length.svg")
                .append("scope", Scope.ATTRIBUTE.name())
                .append("engineType", EngineType.FUNCTION.name())
                .append("outputType", "string")
                .append("type", Type.STANDARD.name())
                .append("configuration", getConfigDocs(
                        "noofractionalDigits", "integer", "Number of Fractional Digits", 2,
                        "decimalSeparator", "string", "Decimal Separator", ".",
                        "groupingSeparator", "string", "Grouping Separator", ","
                ))
                .append("positionalParams", List.of(getParameterDoc("number", DatatypeFactory.getDatatype("number")))));

        functions.replaceOne(and(eq("name", "removeNonPrintable"), eq("scope", Scope.ATTRIBUTE.name())), new Document("name", "removeNonPrintable")
                .append("displayName", "Remove Non Printable")
                .append("helpSummary",
                        "This function removes all non printable characters from the given text.")
                .append("helpPath", "")
                .append("seeded", true)

                .append("iconPath", "/assets/icons/functions/remove-non-printable.svg")
                .append("scope", Scope.ATTRIBUTE.name())
                .append("engineType", EngineType.FUNCTION.name())
                .append("outputType", "string")
                .append("type", Type.STANDARD.name())

                .append("positionalParams", List.of(getParameterDoc("text", DatatypeFactory.getDatatype("string")))));

        functions.replaceOne(and(eq("name", "replace"), eq("scope", Scope.ATTRIBUTE.name())), new Document("name", "replace")
                .append("displayName", "Replace")
                .append("helpSummary",
                        "A function which replaces every part of the input that matches the given expression with the given replacement string")
                .append("helpPath", "")
                .append("seeded", true)

                .append("iconPath", "/assets/icons/functions/replace.svg")
                .append("scope", Scope.ATTRIBUTE.name())
                .append("engineType", EngineType.FUNCTION.name())
                .append("outputType", "string")
                .append("type", Type.STANDARD.name())
                .append("configuration", getConfigDocs(
                        "searchExpression", "text", "Search Regular Expression ", "",
                        "replaceWith", "string", "Replace All Matches With", ""
                ))

                .append("positionalParams", List.of(getParameterDoc("text", DatatypeFactory.getDatatype("string")))));

        functions.replaceOne(and(eq("name", "reverseString"), eq("scope", Scope.ATTRIBUTE.name())), new Document("name", "reverseString")
                .append("displayName", "Reverse String")
                .append("helpSummary",
                        "A function which reverses the input string")
                .append("helpPath", "")
                .append("seeded", true)

                .append("iconPath", "/assets/icons/functions/reverse-string.svg")
                .append("scope", Scope.ATTRIBUTE.name())
                .append("engineType", EngineType.FUNCTION.name())
                .append("outputType", "string")
                .append("type", Type.STANDARD.name())
                .append("positionalParams", List.of(getParameterDoc("values", DatatypeFactory.getDatatype("string")))));

        functions.replaceOne(and(eq("name", "rtrim"), eq("scope", Scope.ATTRIBUTE.name())), new Document("name", "rtrim")
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


        functions.replaceOne(and(eq("name", "striptags"), eq("scope", Scope.ATTRIBUTE.name())), new Document("name", "striptags")
                .append("displayName", "Strip Tags")
                .append("helpSummary",
                        "A function which takes a text value and strips any tags")
                .append("helpPath", "")
                .append("seeded", true)

                .append("iconPath", "/assets/icons/functions/strip-tags.svg")
                .append("scope", Scope.ATTRIBUTE.name())
                .append("engineType", EngineType.FUNCTION.name())
                .append("outputType", "string")
                .append("type", Type.STANDARD.name())
                .append("configuration", getConfigDocs("allowedTags", "string", "Allowed Tags", "")
                )
                .append("positionalParams", List.of(getParameterDoc("text", DatatypeFactory.getDatatype("string")))));

        functions.replaceOne(and(eq("name", "substring"), eq("scope", Scope.ATTRIBUTE.name())), new Document("name", "substring")
                .append("displayName", "Substring")
                .append("helpSummary",
                        "The substring function gives back a portion of the original text between begin index and end index.")
                .append("helpPath", "")
                .append("seeded", true)

                .append("iconPath", "/assets/icons/functions/substring.svg")
                .append("scope", Scope.ATTRIBUTE.name())
                .append("engineType", EngineType.FUNCTION.name())
                .append("outputType", "string")
                .append("type", Type.STANDARD.name())
                .append("configuration",
                        getConfigDocs("startIndex", "integer", "Start Index", 0, "endIndex", "integer", "End Index", 0)
                )
                .append("positionalParams", List.of(getParameterDoc("text", DatatypeFactory.getDatatype("string")))));

        functions.replaceOne(and(eq("name", "camelCase"), eq("scope", Scope.ATTRIBUTE.name())), new Document("name", "camelCase")
                .append("displayName", "Camel Case")
                .append("helpSummary",
                        "A function which takes a text value and makes it a title")
                .append("helpPath", "")
                .append("seeded", true)

                .append("iconPath", "/assets/icons/functions/camel-case.svg")
                .append("scope", Scope.ATTRIBUTE.name())
                .append("engineType", EngineType.FUNCTION.name())
                .append("outputType", "string")
                .append("type", Type.STANDARD.name())
                .append("positionalParams", List.of(getParameterDoc("text", DatatypeFactory.getDatatype("string")))));

        functions.replaceOne(and(eq("name", "translate"), eq("scope", Scope.ATTRIBUTE.name())), new Document("name", "translate")
                .append("displayName", "Translate Text")
                .append("helpSummary",
                        "A function which takes a text and translate it to the desired language")
                .append("helpPath", "")
                .append("seeded", true)

                .append("iconPath", "/assets/icons/functions/translate.svg")
                .append("scope", Scope.ATTRIBUTE.name())
                .append("engineType", EngineType.FUNCTION.name())
                .append("outputType", "string")
                .append("type", Type.STANDARD.name())
                .append("configuration", getConfigDocs("targetLanguage", "string", "Target Language", "English"))
                .append("positionalParams", List.of(getParameterDoc("text", DatatypeFactory.getDatatype("string")))));

        functions.replaceOne(and(eq("name", "trim"), eq("scope", Scope.ATTRIBUTE.name())), new Document("name", "trim")
                .append("displayName", "Trim")
                .append("helpSummary",
                        "A function which takes a string and trims the whitespaces at the beginning and the end")
                .append("helpPath", "")
                .append("seeded", true)

                .append("iconPath", "/assets/icons/functions/trim.svg")
                .append("scope", Scope.ATTRIBUTE.name())
                .append("engineType", EngineType.FUNCTION.name())
                .append("outputType", "string")
                .append("type", Type.STANDARD.name())
                .append("positionalParams", List.of(getParameterDoc("value", DatatypeFactory.getDatatype("string")))));

        functions.replaceOne(and(eq("name", "upper"), eq("scope", Scope.ATTRIBUTE.name())), new Document("name", "upper")
                .append("displayName", "Upper")
                .append("helpSummary",
                        "A function which takes a string and changes all characters to upper case")
                .append("helpPath", "")
                .append("seeded", true)

                .append("iconPath", "/assets/icons/functions/upper.svg")
                .append("scope", Scope.ATTRIBUTE.name())
                .append("engineType", EngineType.FUNCTION.name())
                .append("outputType", "string")
                .append("type", Type.STANDARD.name())
                .append("positionalParams", List.of(getParameterDoc("value", DatatypeFactory.getDatatype("string")))));

        functions.replaceOne(and(eq("name", "url_encode"), eq("scope", Scope.ATTRIBUTE.name())), new Document("name", "urlEncode")
                .append("displayName", "Url Encode")
                .append("helpSummary",
                        "A function which takes a url and encodes it")
                .append("helpPath", "")
                .append("seeded", true)

                .append("iconPath", "/assets/icons/functions/url-encode.svg")
                .append("scope", Scope.ATTRIBUTE.name())
                .append("engineType", EngineType.FUNCTION.name())
                .append("outputType", "string")
                .append("type", Type.STANDARD.name())
                .append("positionalParams", List.of(getParameterDoc("url", DatatypeFactory.getDatatype("string")))));

        functions.replaceOne(and(eq("name", "uuid"), eq("scope", Scope.ATTRIBUTE.name())), new Document("name", "uuid")
                .append("displayName", "UUID")
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


    @ChangeSet(order = "002", id = "addConfigToExtractDomain", author = "varsha")
    public void addConfigToExtractDomain(MongoTemplate template) {

    }
    @ChangeSet(order = "003", id = "updateEncryptDecrypt", author = "neelesh")
    public void updateEncryptDecrypt(MongoTemplate template) {
        MongoCollection<Document> functions = template.getCollection("functionDefinition");
        functions.updateOne(and(eq("name", "encrypt"), eq("scope", Scope.ATTRIBUTE.name())),
                new Document("$set",new Document("configuration",List.of(getConfig("key","password","Encryption Key",
                        "Choose a random key","Choose a random key","",Map.of())))),
                new UpdateOptions().upsert(false)
                );
        functions.updateOne(and(eq("name", "decrypt"), eq("scope", Scope.ATTRIBUTE.name())),
                new Document("$set",new Document("configuration",List.of(getConfig("key","password","Decryption Key",
                        "Must be the same as the encryption key","Must be the same as the encryption key","",Map.of())))),
                new UpdateOptions().upsert(false)
        );
    }
    @ChangeSet(order = "004", id = "updateConcat", author = "varsha")
    public void updateConcat(MongoTemplate template) {

    }
    @ChangeSet(order = "005", id = "updateFunctions", author = "varsha")
    public void updateFunctions(MongoTemplate template) {
        MongoCollection<Document> functions = template.getCollection("functionDefinition");
        functions.updateOne(and(eq("name", "uuid"), eq("scope", Scope.ATTRIBUTE.name())),
                new Document("$set", new Document("positionalParams", List.of(getParameterDoc("value", DatatypeFactory.getDatatype("string"))))),
                new UpdateOptions().upsert(false)
                );
        functions.updateOne(and(eq("name", "translate"), eq("scope", Scope.ATTRIBUTE.name())),
                new Document("$set", new Document("hidden", true)), new UpdateOptions().upsert(false)
                );
        functions.updateOne(and(eq("name", "add"), eq("scope", Scope.ATTRIBUTE.name())),
                new Document("$set", new Document("hidden", true)), new UpdateOptions().upsert(false)
                );
        functions.updateOne(and(eq("name", "subtract"), eq("scope", Scope.ATTRIBUTE.name())),
                new Document("$set", new Document("hidden", true)), new UpdateOptions().upsert(false)
                );
        functions.updateOne(and(eq("name", "min"), eq("scope", Scope.ATTRIBUTE.name())),
                new Document("$set", new Document("hidden", true)), new UpdateOptions().upsert(false)
                );
        functions.updateOne(and(eq("name", "max"), eq("scope", Scope.ATTRIBUTE.name())),
                new Document("$set", new Document("hidden", true)), new UpdateOptions().upsert(false)
                );
    }

	private List<Document> getConfigDocs(Object... arguments) {
        List<Document> config = new ArrayList<>();
        for (int i = 0; i < arguments.length; i += 4) {
            config.add(getConfig( arguments[i].toString(),arguments[i + 1].toString(), arguments[i + 2].toString(),arguments[i + 3],Map.of()));
        }
        return config;
    }
	private Document getConfig(String name, String datatype,String label, Object defaultValue, Map<String, Object> additionalProps) {
		return new Document("name", name).append("datatype", datatype)
				.append("defaultValue", defaultValue)
				.append("label", label)
				.append("additionalProperties", additionalProps);
	}
    private Document getConfig(String name, String datatype,String label, String helpSummary, String helpText, Object defaultValue, Map<String, Object> additionalProps) {
        return getConfig(name, datatype,label,helpSummary,helpText,defaultValue,additionalProps,Map.of());
    }
    private Document getConfig(String name, String datatype,String label, String helpSummary, String helpText, Object defaultValue, Map<String, Object> additionalProps, Map<String, Object> inlineAdditionalProps) {
        Document config = new Document();
        config.putAll(inlineAdditionalProps);
        config.append("name", name).append("datatype", datatype)
                .append("defaultValue", defaultValue)
                .append("label", label)
                .append("helpSummary", helpSummary)
                .append("helpText", helpText)
                .append("additionalProperties", additionalProps);

        return config;
    }

    private Document getParameterDoc(String name, Datatype datatype) {
        return new Document("name", name)
                .append("datatype", datatype.getName())
                .append("vararg", false);
    }

}
