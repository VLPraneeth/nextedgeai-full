package com.syncari.core.pipeline;

import com.jayway.jsonpath.JsonPath;
import com.jayway.jsonpath.ReadContext;
import com.syncari.connector.EntityData;
import com.syncari.core.pipeline.expression.Expression;
import com.syncari.core.pipeline.expression.In;
import com.syncari.core.pipeline.expression.NotIn;
import com.syncari.core.pipeline.jtwig.JTwigTemplateGenerationVisitor;
import com.syncari.core.pipeline.jtwig.TokenEnvironment;
import com.syncari.core.token.TokenHelper;
import com.syncari.utils.Pair;
import net.minidev.json.JSONArray;
import net.minidev.json.JSONObject;
import net.snowflake.client.jdbc.internal.apache.arrow.flatbuf.Bool;
import org.jtwig.JtwigModel;
import org.jtwig.JtwigTemplate;
import org.jtwig.environment.DefaultEnvironmentConfiguration;
import org.jtwig.environment.Environment;
import org.jtwig.environment.EnvironmentFactory;
import org.junit.Test;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.junit.Assert.*;

import static com.syncari.core.pipeline.expression.Expression.*;

public class TemplateGeneratorTest {

    @Test
    public void testSimpleExpressionGeneratesCorrectTemplate() {
        Expression expression = ifElse(eq(var("zz"), lit("qq")), renderedLit("true"), renderedVar("false"));
        JTwigTemplateGenerationVisitor visitor = new JTwigTemplateGenerationVisitor(new TokenHelper(
                new TokenEnvironment(new EnvironmentFactory().create(new DefaultEnvironmentConfiguration()),Map.of())));
        expression.accept(visitor);
        assertEquals("{% if ((zz == \"qq\")) %}true{% else %}{{value(false)}}{% endif %}",visitor.getGeneratedBody());

    }

    @Test
    public void inTest() {
        Expression expression = Expression.in(Expression.var("myVar"),Expression.lit(List.of("1","2","3")));
        Environment environment = new EnvironmentFactory().create(new DefaultEnvironmentConfiguration());
        TokenHelper tokenHelper = new TokenHelper(
                new TokenEnvironment(environment, Map.of()));
        JTwigTemplateGenerationVisitor visitor = new JTwigTemplateGenerationVisitor(tokenHelper);
        expression.accept(visitor);
        assertEquals("([\"1\", \"2\", \"3\"].contains(myVar))",visitor.getGeneratedBody());
        String result = JtwigTemplate.inlineTemplate("{{"+visitor.getGeneratedBody()+"}}").render(new JtwigModel().with("myVar", "2"));
        assertTrue(Boolean.parseBoolean(result));
    }
    @Test
    public void inNumbersTest() {
        Expression expression = Expression.in(Expression.var("myVar"),Expression.lit(List.of(1,2,3)));
        Environment environment = new EnvironmentFactory().create(new DefaultEnvironmentConfiguration());
        TokenHelper tokenHelper = new TokenHelper(
                new TokenEnvironment(environment, Map.of()));
        JTwigTemplateGenerationVisitor visitor = new JTwigTemplateGenerationVisitor(tokenHelper);
        expression.accept(visitor);
        assertEquals("([1, 2, 3].contains(myVar))",visitor.getGeneratedBody());
        String result = JtwigTemplate.inlineTemplate("{{"+visitor.getGeneratedBody()+"}}")
                .render(new JtwigModel().with("myVar", new BigDecimal(2)));
        assertTrue(Boolean.parseBoolean(result));
    }
    @Test
    public void notInNumbersTest() {
        Expression expression = Expression.notIn(Expression.var("myVar"),Expression.lit(List.of(1,2,3)));
        Environment environment = new EnvironmentFactory().create(new DefaultEnvironmentConfiguration());
        TokenHelper tokenHelper = new TokenHelper(
                new TokenEnvironment(environment, Map.of()));
        JTwigTemplateGenerationVisitor visitor = new JTwigTemplateGenerationVisitor(tokenHelper);
        expression.accept(visitor);
        assertEquals("(not ([1, 2, 3].contains(myVar)))",visitor.getGeneratedBody());
        String result = JtwigTemplate.inlineTemplate("{{"+visitor.getGeneratedBody()+"}}")
                .render(new JtwigModel().with("myVar", new BigDecimal(9)));
        assertTrue(Boolean.parseBoolean(result));
    }
    @Test
    public void notInTest() {
        Expression expression = Expression.notIn(Expression.var("myVar"),Expression.lit(List.of("1","2","3")));
        Environment environment = new EnvironmentFactory().create(new DefaultEnvironmentConfiguration());
        JTwigTemplateGenerationVisitor visitor = new JTwigTemplateGenerationVisitor(new TokenHelper(
                new TokenEnvironment(environment,Map.of())));
        expression.accept(visitor);
        assertEquals("(not ([\"1\", \"2\", \"3\"].contains(myVar)))",visitor.getGeneratedBody());
        String result = JtwigTemplate.inlineTemplate("{{"+visitor.getGeneratedBody()+"}}")
                .render(new JtwigModel().with("myVar", "9"));
        assertTrue(Boolean.parseBoolean(result));
    }

    @Test
    public void testParsing(){

        var hubspotJSON = "{\n" +
                "  \"results\": [\n" +
                "    {\n" +
                "      \"portalId\": 62515,\n" +
                "      \"companyId\": 19411477,\n" +
                "      \"isDeleted\": false,\n" +
                "      \"properties\": {\n" +
                "        \"hs_lastmodifieddate\": {\n" +
                "          \"value\": \"1419968097561\",\n" +
                "          \"timestamp\": 1419968097561,\n" +
                "          \"source\": null,\n" +
                "          \"sourceId\": null,\n" +
                "          \"versions\": [\n" +
                "            {\n" +
                "              \"name\": \"hs_lastmodifieddate\",\n" +
                "              \"value\": \"1419968097561\",\n" +
                "              \"timestamp\": 1419968097561,\n" +
                "              \"sourceVid\": [\n" +
                "                \n" +
                "              ]\n" +
                "            }\n" +
                "          ]\n" +
                "        },\n" +
                "        \"createdate\": {\n" +
                "          \"value\": \"1419966010576\",\n" +
                "          \"timestamp\": 1419966010576,\n" +
                "          \"source\": null,\n" +
                "          \"sourceId\": null,\n" +
                "          \"versions\": [\n" +
                "            {\n" +
                "              \"name\": \"createdate\",\n" +
                "              \"value\": \"1419966010576\",\n" +
                "              \"timestamp\": 1419966010576,\n" +
                "              \"sourceVid\": [\n" +
                "                \n" +
                "              ]\n" +
                "            }\n" +
                "          ]\n" +
                "        },\n" +
                "        \"hs_analytics_source_data_1\": {\n" +
                "          \"value\": \"API\",\n" +
                "          \"timestamp\": 1419968066626,\n" +
                "          \"source\": null,\n" +
                "          \"sourceId\": null,\n" +
                "          \"versions\": [\n" +
                "            {\n" +
                "              \"name\": \"hs_analytics_source_data_1\",\n" +
                "              \"value\": \"API\",\n" +
                "              \"timestamp\": 1419968066626,\n" +
                "              \"sourceVid\": [\n" +
                "                \n" +
                "              ]\n" +
                "            }\n" +
                "          ]\n" +
                "        }\n" +
                "      }\n" +
                "  }\n" +
                "],\n" +
                "\"hasMore\": false,\n" +
                "\"offset\": 4,\n" +
                "\"total\": 4\n" +
                "}";
        var zendeskJSON ="{\n" +
                "  \"tickets\": [ {\n" +
                "    \"id\":      35436,\n" +
                "    \"subject\": \"My printer is on fire!\"\n" +
                "  },\n" +
                "  {\n" +
                "    \"id\":      35436,\n" +
                "    \"subject\": \"My printer is on fire!\"\n" +
                "  }\n" +
                "   ]\n" +
                "}";

        String resultsArrayPath ="results";
        String idFieldName ="companyId";
        String idField ="companyId";
        String fieldsPath ="results[{i}].properties";
        String idPath ="results[{i}].companyId";
        boolean isFieldKey = true;
        String valuePath ="results[{i}].properties.__key__.value";

        ReadContext ctx = JsonPath.parse(hubspotJSON);
        List<EntityData> parse = parse(resultsArrayPath, fieldsPath,idFieldName, isFieldKey, valuePath,idPath, ctx);
        System.out.println(parse);

        resultsArrayPath ="tickets";
        idFieldName ="id";
        fieldsPath ="tickets[{i}]";
        idPath = null;

        valuePath ="tickets[{i}].__key__";

        ctx = JsonPath.parse(zendeskJSON);
        var parse1 = parse(resultsArrayPath, fieldsPath,idFieldName, isFieldKey, valuePath,idPath, ctx);
        System.out.println(parse1);

    }

    private List<EntityData> parse(String resultsArrayPath, String fieldsPath, String idFieldName, boolean isFieldKey , String valuePath, String idPath,ReadContext ctx) {
        JSONArray results = ctx.read(resultsArrayPath);
        List<EntityData> extracted = new ArrayList<>();
        for(int i=0;i< results.size();i++){
            var e = new EntityData();
            if(isFieldKey){
                Map<String, Object> obj = ctx.read(fieldsPath.replace("{i}",String.valueOf(i)));
                for(String  key : obj.keySet()){
                    Object value = ctx.read(valuePath.replace("{i}",String.valueOf(i)).replace("__key__",key));
                    e.addValue(key, value);
                    if(key.equals(idFieldName)){
                        e.setId(value.toString());
                    }
                }
                if (idPath != null) {
                    e.setId(ctx.read(idPath.replace("{i}",String.valueOf(i))).toString());
                }
            }else{
                //TODO: This would be an array of properties

            }
            extracted.add(e);

        }
        return extracted;
    }
}
