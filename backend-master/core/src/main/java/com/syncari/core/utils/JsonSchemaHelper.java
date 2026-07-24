package com.syncari.core.utils;

import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

import org.apache.commons.collections4.CollectionUtils;
import org.apache.commons.lang3.StringUtils;
import org.everit.json.schema.Schema;
import org.everit.json.schema.ValidationException;
import org.everit.json.schema.loader.SchemaLoader;
import org.json.JSONArray;
import org.json.JSONObject;
import org.json.JSONTokener;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.JsonNodeType;
import com.syncari.connector.data.AttributeSchema;
import com.syncari.core.datatype.DateType;
import com.syncari.core.datatype.DatetimeType;
import com.syncari.core.token.XPathTokenResolver;
import com.syncari.utils.TextUtil;
import lombok.extern.slf4j.Slf4j;

@Slf4j
public final class JsonSchemaHelper {

    private static ObjectMapper objectMapper = new ObjectMapper();

    public static String outputAsString(String json) {
        return cleanup(outputAsString(json, null));
    }

    private static String outputAsString(String json, JsonNodeType type) {
        JsonNode jsonNode;
		try {
			jsonNode = objectMapper.readTree(json);
		} catch (IOException e) {
			e.printStackTrace();
			throw new RuntimeException(e);
		}
        StringBuilder output = new StringBuilder();
        output.append("{");

        if (type == null) output.append(
        		"\"$schema\": \"http://json-schema.org/draft-04/schema#\", \"type\": \"object\", \"properties\": {");

        for (Iterator<String> iterator = jsonNode.fieldNames(); iterator.hasNext();) {
            String fieldName = iterator.next();
            log.debug("processing " + fieldName + "...");

            JsonNodeType nodeType = jsonNode.get(fieldName).getNodeType();

            output.append(convertNodeToStringSchemaNode(jsonNode, nodeType, fieldName));
        }

        if (type == null) output.append("}");

        output.append("}");

        log.debug("generated schema = " + output.toString());
        return output.toString();
    }

    private static String convertNodeToStringSchemaNode(
            JsonNode jsonNode, JsonNodeType nodeType, String key) {
        StringBuilder result = new StringBuilder("\"" + key + "\": { \"type\": \"");

        log.debug(key + " node type " + nodeType + " with value " + jsonNode.get(key));
        JsonNode node = null;
        switch (nodeType) {
            case ARRAY :
            	if(jsonNode.get(key).isEmpty()) {
            		result.append("array\", \"items\": { \"properties\":");
            		result.append(outputAsString("{}", JsonNodeType.ARRAY));
            		result.append("}},");
            	} else {
            		node = jsonNode.get(key).get(0);
            		log.debug(key + " is an array with value of " + node.toString());
            		result.append("array\", \"items\": { \"properties\":");
            		result.append(outputAsString(node.toString(), JsonNodeType.ARRAY));
            		result.append("}},");
            	}
                break;
            case BOOLEAN:
                result.append("boolean\" },");
                break;
            case NUMBER:
                result.append("number\" },");
                break;
            case OBJECT:
                node = jsonNode.get(key);
                result.append("object\", \"properties\": ");
                result.append(outputAsString(node.toString(), JsonNodeType.OBJECT));
                result.append("},");
                break;
            case STRING:
            	result.append("string\"");
                String value = jsonNode.get(key).asText();
                if (new DatetimeType().convert(value) != null) {
                    result.append(", \"format\": \"date-time\"");
                } else if (new DateType().convert(value) != null) {
                    result.append(", \"format\": \"date\"");
                }
                result.append(" },");
                break;
           default:
        	   result.append("string\" },");
               break;
        }

        return result.toString();
    }

    private static String cleanup(String dirty) {
        JSONObject rawSchema = new JSONObject(new JSONTokener(dirty));
        Schema schema = SchemaLoader.load(rawSchema);
        return schema.toString();
    }
    
    public static void validateSchemaSyntax(String schema) {
    	//This will throw exception if the schema is not valid
    	JSONObject rawSchema = new JSONObject(new JSONTokener(schema));
    	SchemaLoader.load(rawSchema);
    }
    
    public static String validateJson(String strSchema, String json) {
    	try {
			JSONObject rawSchema = new JSONObject(new JSONTokener(strSchema));
			Schema schema = SchemaLoader.load(rawSchema);
			schema.validate(new JSONObject(new JSONTokener(json)));
		} catch (ValidationException e) {
			log.debug("Json validation error ", e);
			if(CollectionUtils.isNotEmpty(e.getCausingExceptions())) {
				return e.getCausingExceptions().stream().map(ee -> ee.getMessage()).collect(Collectors.toList()).toString();
			} else {
				return e.getErrorMessage();
			}
		} catch (Exception e) {
			log.debug("Json general error ", e);
			return e.getMessage();
		}
    	return null;
    }
    
    public static List<AttributeSchema> getAttributesFromSchema(String schema, String recordSelector, String idSelector, String wmSelector) {
      if(StringUtils.isBlank(schema)) {
          return List.of();
      }
      List<AttributeSchema> attribs = new ArrayList<>();
      try {
          JsonSchemaHelper.validateSchemaSyntax(schema);
      }catch (Exception e) {
          log.error("Schema validation error", e);
          return attribs;
      }
      JSONObject schemaObject = new JSONObject(schema);
      if(StringUtils.isNotBlank(recordSelector)) {
          var recordJsonSchema = navigateJsonSchema(schemaObject, recordSelector);
          if(recordJsonSchema != null) {
              schemaObject = recordJsonSchema;
          }
      }
      String idKey = getLastPartOfJsonXPathWithoutIndex(idSelector);
      String wmKey = getLastPartOfJsonXPathWithoutIndex(wmSelector);
      Set<String> required = Set.of();
      if(schemaObject.has("required")) {
          JSONArray requiredProperties = schemaObject.getJSONArray("required");
          required = requiredProperties.toList().stream().map(r -> r.toString()).collect(Collectors.toSet());
      }
      JSONObject properties = schemaObject.getJSONObject("properties");
      for (String key : properties.keySet()) {
          //set id selector field as id
          //water mark selector field as water mark
          JSONObject property = properties.getJSONObject(key);
          String jsonDataType = property.has("type") ? property.getString("type") : "object";
          String jsonDataFormat = property.has("format") ? property.getString("format") : null;
          boolean readOnly = false;
          if (property.has("readOnly") && property.getBoolean("readOnly")) {
              readOnly = true;
          }
          String dataType = mapJSonDataTypeToSyncari(jsonDataType, jsonDataFormat);
          boolean multivalued = false;
          if("array".equals(dataType)) {
              multivalued = true;
              if(property.getJSONObject("items").has("type")) {
                  jsonDataType = property.getJSONObject("items").getString("type");
                  jsonDataFormat = property.getJSONObject("items").has("format") ? property.getJSONObject("items").getString("format") : null;
                  dataType = mapJSonDataTypeToSyncari(jsonDataType, jsonDataFormat);
                  if("array".equals(dataType)) {//Don't nest further. Treat it as object
                      dataType = "object";
                  }
              } else {
                  dataType = "object";
              }
          }
          AttributeSchema attr = new AttributeSchema(TextUtil.sanitizeFieldName(key), dataType);
          attr.setDisplayName(key);
          attr.setUpdateable(!readOnly);
          attr.setNillable(!required.contains(key));
          attr.setMultiValueField(multivalued);
          if(key.equalsIgnoreCase(idKey)) {
              attr.setIdField(true);
              attr.setUpdateable(false);
          } else if(key.equalsIgnoreCase(wmKey)) {
              attr.setWatermarkField(true);
              attr.setUpdateable(false);
          }
          attribs.add(attr);
      }
      
      return attribs;
  }
  
  private static JSONObject navigateJsonSchema(JSONObject schemaNode, String xmlXPath) {
      // Remove leading '/' if present
      String cleanedXPath = xmlXPath.startsWith("/") ? xmlXPath.substring(1) : xmlXPath;

      // Split the XPath into parts
      String[] pathParts = cleanedXPath.split("/");

      JSONObject currentNode = schemaNode;
      JSONObject propertiesNode = currentNode.optJSONObject("properties"); // Start at the root properties

      for (String part : pathParts) {
          if (propertiesNode == null) {
              return null; // If propertiesNode is null, path cannot be navigated further
          }

          // Handle array indices using regex to capture name and index
          String arrayName = part.replaceAll("\\[\\d+\\]", "");
          boolean isArray = part.matches(".+\\[\\d+\\]");

          // Navigate into the next level
          if (isArray) {
              // Traverse into the array (handling 'items' for JSON Schema)
              if (propertiesNode.has(arrayName)) {
                  JSONObject arrayNode = propertiesNode.optJSONObject(arrayName);
                  currentNode = arrayNode.optJSONObject("items");
                  propertiesNode = currentNode.optJSONObject("properties");
              } else {
                  return null; // Property not found
              }
          } else {
              // Traverse into the object property
              if (propertiesNode.has(part)) {
                  currentNode = propertiesNode.optJSONObject(part);
                  propertiesNode = currentNode.optJSONObject("properties");
              } else {
                  return null; // Property not found
              }
          }
      }

      if(currentNode != null && currentNode.has("items")) {
          return currentNode.getJSONObject("items");
      } else {
          return currentNode;
      }
  }
  
  private static String getLastPartOfJsonXPathWithoutIndex(String jsonXPath) {
      if (jsonXPath == null || jsonXPath.isEmpty()) {
          return "";
      }

      // Remove leading '/' if present
      String cleanedXPath = jsonXPath.startsWith("/") ? jsonXPath.substring(1) : jsonXPath;

      // Split the XPath into parts
      String[] pathParts = cleanedXPath.split("/");

      // Extract the last part
      String lastPart = pathParts[pathParts.length - 1];

      // Remove any array index (e.g., [1]) using a regular expression
      return lastPart.replaceAll("\\[\\d+\\]", "");
  }
  
  private static String mapJSonDataTypeToSyncari(String jsonDataType, String jsonDataFormat) {
      String dataType = "object";
      switch (jsonDataType) {
      case "string":
          if(StringUtils.isNotBlank(jsonDataFormat)) {
              switch (jsonDataFormat) {
              case "date-time":
                  dataType = "datetime";
                  break;
              case "date":
                  dataType = "date";
                  break;
              default:
                  dataType = "string";
                  break;
              }
          } else {
              dataType = "string";
          }
          break;
      case "integer":
      case "number":
      case "boolean":
      case "object":
      case "array":
          dataType = jsonDataType;
          break;
      default:
          dataType = "object";
          break;
      }
      return dataType;
  }
  
    public static Object jsonNodeToMap(JsonNode jsonNode) {
      if (jsonNode.isObject()) {
          Map<String, Object> result = new HashMap<>();
          Iterator<Map.Entry<String, JsonNode>> fields = jsonNode.fields();
          while (fields.hasNext()) {
              Map.Entry<String, JsonNode> entry = fields.next();
              result.put(entry.getKey(), jsonNodeToMap(entry.getValue())); // Recursively convert child nodes
          }
          return result;
      } else if (jsonNode.isArray()) {
          List<Object> result = new ArrayList<Object>();
          for (JsonNode node : jsonNode) {
              result.add(jsonNodeToMap(node)); // Recursively convert child nodes
          }
          return result;
      } else if (jsonNode.isValueNode()) {
          if (jsonNode.isTextual()) {
              return jsonNode.asText();
          } else if (jsonNode.isNumber()) {
              return jsonNode.numberValue();
          } else if (jsonNode.isBoolean()) {
              return jsonNode.booleanValue();
          } else {
              return null;
          }
      } else {
          return null;
      }
  }
  public static Optional<Object> getFieldValueBySelector(Map<String, Object> values, String selector) {
    if(!StringUtils.isBlank(selector)) {
        XPathTokenResolver xpathResolver = new XPathTokenResolver(selector.trim());
        var resolved = xpathResolver.resolveToken(values);
        if (resolved.hasTokenSyntaxErrors() || !resolved.isKeyFoundInContext()) {
            log.error("XPath evaluation failed for {}. Returning empty", selector);
            return Optional.empty();
        } else {
            return Optional.ofNullable(resolved.getResolvedValue());
        }
    }
    return Optional.empty();
  }
    
}