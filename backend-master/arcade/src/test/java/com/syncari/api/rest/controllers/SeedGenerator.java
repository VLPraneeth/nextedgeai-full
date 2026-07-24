package com.syncari.api.rest.controllers;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;

import org.json.JSONException;
import org.junit.Ignore;
import org.junit.Test;

import com.sforce.soap.partner.Connector;
import com.sforce.soap.partner.DescribeSObjectResult;
import com.sforce.soap.partner.Field;
import com.sforce.soap.partner.PartnerConnection;
import com.sforce.ws.ConnectionException;
import com.sforce.ws.ConnectorConfig;

public class SeedGenerator {

//	@Test
	public void sfdc() throws ConnectionException, IOException {
		String entity = "ActivityHistory";
		ConnectorConfig config = new ConnectorConfig();
		config.setUsername("varsha@syncari.com");
		config.setPassword(System.getenv().getOrDefault("TEST_PASSWORD", "REPLACE_ME"));
		PartnerConnection newConnection = Connector.newConnection(config);
		DescribeSObjectResult describe = newConnection.describeSObject(entity);
		Field[] sobjects = describe.getFields();
		String template = "		"+entity.toLowerCase()+"Attributes.add(new Document(\"entityId\", "+entity.toLowerCase()+".get(\"_id\").toString())\n"
				+ "				.append(\"apiName\", \"%s\")\n" 
				+ "				.append(\"displayName\", \"%s\")\n"
				+ "				.append(\"custom\", \"%s\")\n" 
				+ "				.append(\"dataType\", \"%s\")\n"
				+ "%s"
				+ "				.append(\"nillable\", \"%s\")\n" 
				+ "				.append(\"calculated\", \"%s\")\n"
				+ "				.append(\"unique\", %s)\n"
				+ "				.append(\"initializable\", %s)\n" 
				+ "				.append(\"updatable\", %s));";
		for (Field o : sobjects) {
			String context = "				.append(\"context\", Map.of(";
			if("reference".equalsIgnoreCase(o.getType().name())) {
				String target = o.getReferenceTargetField() == null ? "Id" : o.getReferenceTargetField();
				context = context + "\"referenceTo\", \""+o.getReferenceTo()[0]+"\", \"referenceTargetField\", \""+target+"\", ";
			}
			context = context + "\"length\", "+o.getLength()+", \"precision\", "+o.getPrecision()+", \"scale\", "+o.getScale()+"))\n";
			System.out.println(String.format(template, o.getName(), o.getLabel(), o.isCustom(), o.getType(), context,
					o.isNillable(), o.isCalculated(), o.isUnique(), o.isCreateable(), o.isUpdateable()));

		}
	}
	
	@Test
	@Ignore
	public void hubspot() throws ConnectionException, IOException, JSONException, InterruptedException {
		String entity = "contact";
		String url = "https://api.hubapi.com/properties/v2/"+entity+"s/properties?hapikey=demo";
		HttpClient client = HttpClient.newHttpClient();
	    HttpRequest request = HttpRequest.newBuilder()
	          .uri(URI.create(url))
	          .build();

//	    HttpResponse<String> response =
//	          client.send(request, BodyHandlers.ofString());
//		String template = "		"+entity+"Attributes.add(new Document(\"entityId\", "+entity+".get(\"id\"))\n"
//				+ "				.append(\"apiName\", \"%s\")\n" 
//				+ "				.append(\"displayName\", \"%s\")\n"
//				+ "				.append(\"custom\", \"%s\")\n" 
//				+ "				.append(\"dataType\", \"%s\")\n"
//				+ "				.append(\"calculated\", \"%s\")\n"
//				+ "				.append(\"unique\", %s)\n"
//				+ "				.append(\"updatable\", %s));";
//	    JSONArray jsonArray = new JSONArray(response.body()); 
//	    for (int i=0;i<jsonArray.length();i++) {
//	    	JSONObject o = jsonArray.getJSONObject(i);
//	    	System.out.println(String.format(template, o.get("name"), o.get("label"), false, o.get("type"),
//					o.get("calculated"), o.get("hasUniqueValue"),
//					!((Boolean)o.get("readOnlyValue"))));
//	    	System.out.println(o);
//		}
	}

}
