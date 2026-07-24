/*
 * Copyright (c) 2014, Oracle America, Inc.
 * All rights reserved.
 *
 * Redistribution and use in source and binary forms, with or without
 * modification, are permitted provided that the following conditions are met:
 *
 *  * Redistributions of source code must retain the above copyright notice,
 *    this list of conditions and the following disclaimer.
 *
 *  * Redistributions in binary form must reproduce the above copyright
 *    notice, this list of conditions and the following disclaimer in the
 *    documentation and/or other materials provided with the distribution.
 *
 *  * Neither the name of Oracle nor the names of its contributors may be used
 *    to endorse or promote products derived from this software without
 *    specific prior written permission.
 *
 * THIS SOFTWARE IS PROVIDED BY THE COPYRIGHT HOLDERS AND CONTRIBUTORS "AS IS"
 * AND ANY EXPRESS OR IMPLIED WARRANTIES, INCLUDING, BUT NOT LIMITED TO, THE
 * IMPLIED WARRANTIES OF MERCHANTABILITY AND FITNESS FOR A PARTICULAR PURPOSE
 * ARE DISCLAIMED. IN NO EVENT SHALL THE COPYRIGHT HOLDER OR CONTRIBUTORS BE
 * LIABLE FOR ANY DIRECT, INDIRECT, INCIDENTAL, SPECIAL, EXEMPLARY, OR
 * CONSEQUENTIAL DAMAGES (INCLUDING, BUT NOT LIMITED TO, PROCUREMENT OF
 * SUBSTITUTE GOODS OR SERVICES; LOSS OF USE, DATA, OR PROFITS; OR BUSINESS
 * INTERRUPTION) HOWEVER CAUSED AND ON ANY THEORY OF LIABILITY, WHETHER IN
 * CONTRACT, STRICT LIABILITY, OR TORT (INCLUDING NEGLIGENCE OR OTHERWISE)
 * ARISING IN ANY WAY OUT OF THE USE OF THIS SOFTWARE, EVEN IF ADVISED OF
 * THE POSSIBILITY OF SUCH DAMAGE.
 */

package com.syncari.microbenchmark;

import com.github.mustachejava.DefaultMustacheFactory;
import com.github.mustachejava.Mustache;
import com.github.mustachejava.MustacheFactory;
import com.syncari.core.actions.Actions;
import com.syncari.core.datatype.DatetimeType;
import com.syncari.core.datatype.IntegerType;
import com.syncari.core.functions.FunctionsSeed;
import com.syncari.core.functions.LookUpFunctions;
import com.syncari.core.model.*;
import com.syncari.core.pipeline.*;
import com.syncari.core.pipeline.expression.Expression;
import com.syncari.core.pipeline.jtwig.JTwigPipelineEvaluator;
import com.syncari.core.pipeline.jtwig.JTwigResult;
import com.syncari.core.pipeline.jtwig.SyncariExtension;
import com.syncari.core.pipeline.jtwig.TokenEnvironment;
import com.syncari.core.pipeline.jtwig.functions.*;
import com.syncari.core.repositories.customer.AttributeRepo;
import com.syncari.core.repositories.customer.StagedBatchRecordRepo;
import com.syncari.core.service.FunctionService;
import com.syncari.core.sync.CurrentBatch;
import com.syncari.core.token.JtwigModelSanitizer;
import com.syncari.core.token.TokenHelper;
import com.syncari.core.utils.LookupCriteriaVisitor;
import com.syncari.utils.Pair;
import org.apache.commons.codec.digest.DigestUtils;
import org.apache.commons.lang3.StringUtils;
import org.bson.types.ObjectId;
import org.jtwig.JtwigModel;
import org.jtwig.JtwigTemplate;
import org.jtwig.environment.*;
import org.jtwig.escape.EscapeEngine;
import org.jtwig.model.tree.OutputNode;
import org.jtwig.render.expression.CalculateExpressionService;
import org.jtwig.render.node.renderer.NodeRender;
import org.jtwig.renderable.impl.StringRenderable;
import org.jtwig.resource.reference.ResourceReference;
import org.openjdk.jmh.annotations.*;
import org.openjdk.jmh.infra.Blackhole;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.ApplicationContext;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.support.ClassPathXmlApplicationContext;

import java.io.*;
import java.math.BigDecimal;
import java.time.ZonedDateTime;
import java.util.*;
import java.util.concurrent.ThreadLocalRandom;
import java.util.concurrent.TimeUnit;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.DoubleStream;

import static com.syncari.core.pipeline.jtwig.functions.SideChannelFunction.extractResult;

@State(Scope.Benchmark)
/*
@SpringBootApplication
@ComponentScan(basePackages = "com.syncari")
*/
public class CustomFiltetBenchmark {

    /*@Autowired
    private JTwigPipelineEvaluator evaluator;
    @Autowired
    FunctionService fRepo;
    @Autowired
    AttributeRepo attributeRepo;
    @Autowired
    StagedBatchRecordRepo recordRepo;

    ApplicationContext context; // added this here*/

    EnvironmentConfiguration configuration = EnvironmentConfigurationBuilder.configuration()
            .functions()
            .add(new Sum())
            .add(new HasConflicts())
            .add(new NonEmpty())
            .add(new FirstOf())
            .add(new Latest())
            .add(new Value())
            .add(new Null())
            .add(new FilterFailed())
            .and()
            //override outputnode renderer and add value to function channel
            .render().nodeRenders().add(OutputNode.class, (NodeRender<OutputNode>) (request, node) -> {
                CalculateExpressionService calculateExpressionService = request.getEnvironment().getRenderEnvironment().getCalculateExpressionService();
                Object calculate = calculateExpressionService.calculate(request, node.getExpression());
                //Set the result of calculation in a threadlocal, before its stringified by JTwig
                JTwigResult.set(calculate);
                EscapeEngine escapeEngine = request.getRenderContext().getCurrent(EscapeEngine.class);
                return new StringRenderable(request.getEnvironment().getValueEnvironment().getStringConverter().convert(calculate), escapeEngine);
            }).and().and().build();


    TokenEnvironment environment =new TokenEnvironment(new EnvironmentFactory().create(configuration),Map.of());
    TokenHelper tokenHelper = new TokenHelper(environment);
    GraphContext graphContext = new GraphContext().set("Corelight 1Google Sheets", "GoogleSheets").set("2vWpgiN35p__fLYCISEL3vFa9JLN3Jcey",
            Map.of("3company", "1All_COMPANYVAL"));
    MustacheFactory mf = new DefaultMustacheFactory();
    Mustache mustache = mf.compile(new StringReader("{{name}}, {{feature.key}}!"), "example");


    @Benchmark()
    @BenchmarkMode(Mode.AverageTime)
    @OutputTimeUnit(TimeUnit.MICROSECONDS)
    public void filterEval(Blackhole blackhole) throws IOException {
        GraphContext graphContext = new GraphContext();
        graphContext.put("field_sfdc_account_owner_id","0056Q000007Z945QAC");
        graphContext.put("field_act_src","Outbound-G2");
        graphContext.put("field_sfdc_created_date", ZonedDateTime.now().minusDays(3));

        var functionResult = "output_" + ObjectId.get().toHexString();
        var functionResultVariableName = functionResult + ".x";
        graphContext.put(functionResult, Pair.of(new FunctionResult(null, null), new MappingNode()));

        final FilterEvaluationVisitor filterEvaluationVisitor = new FilterEvaluationVisitor(graphContext, tokenHelper);
        Expression sfAccountOwnerId = Expression.eq(Expression.var("field_sfdc_account_owner_id"),Expression.lit("0056Q000007Z945QAC"));
        Expression sdrAssignmentStatus = Expression.empty(Expression.var("field_sdr"));
        Expression billingCountry = Expression.empty(Expression.var("field_country"));
        Expression actSource =  Expression.eq(Expression.var("field_act_src"),Expression.lit("Clearbit Created Account"));
        Expression actSource2 =  Expression.eq(Expression.var("field_act_src"),Expression.lit("Outbound-G2"));
        Expression createdDate =  Expression.gte(Expression.var("field_sfdc_created_date"),Expression.lit("before 48 hours"));
        Expression filterExp = Expression.and(Expression.and(Expression.and(Expression.and(sfAccountOwnerId, sdrAssignmentStatus),Expression.or(actSource, actSource2)),billingCountry),createdDate);

        Expression trueValue = Expression.renderedVar(functionResultVariableName);
        Expression falseValue = Expression.filterFailed(functionResultVariableName);

        var expr = Expression.ifElse(filterExp, trueValue, falseValue);

        expr.accept(filterEvaluationVisitor);
        //var v = filterEvaluationVisitor.getValue();
        blackhole.consume(filterEvaluationVisitor.getValue());
    }


    /* @Benchmark()
    @BenchmarkMode(Mode.AverageTime)
    @OutputTimeUnit(TimeUnit.MICROSECONDS)
    public void md5Digest(Blackhole blackhole) throws IOException {
        GraphContext graphContext = new GraphContext();
        graphContext.put("field_sfdc_account_owner_id","0056Q000007Z945QAC");
        graphContext.put("token_1","0056Q000007Z945QAC");
        graphContext.put("field_act_src","Outbound-G2");
        graphContext.put("field_sfdc_created_date", ZonedDateTime.now().minusDays(3));

        var functionResult = "output_" + ObjectId.get().toHexString();
        graphContext.put(functionResult, Pair.of(new FunctionResult(null, null), new MappingNode()));

        Expression sfAccountOwnerId = Expression.eq(Expression.var("field_sfdc_account_owner_id"),Expression.lit("0056Q000007Z945QAC"));
        Expression sfAccountUserId = Expression.eq(Expression.var("field_sfdc_account_user_id"),Expression.lit("0056Q000007Z945QAD"));
        Expression sdrAssignmentStatus = Expression.empty(Expression.var("field_sdr"));
        Expression billingCountry = Expression.notEmpty(Expression.var("field_country"));
        Expression actSource =  Expression.eq(Expression.var("field_act_src"),Expression.lit("Clearbit Created Account"));
        Expression actSource2 =  Expression.eq(Expression.var("field_act_src"),Expression.lit("Outbound-G2"));
        Expression createdDate =  Expression.gte(Expression.var("field_sfdc_created_date"),Expression.lit("before 48 hours"));
        //Expression expression = Expression.and(Expression.and(Expression.and(Expression.and(sfAccountOwnerId, sdrAssignmentStatus),Expression.or(actSource, actSource2)),billingCountry),createdDate);
        Expression expression = Expression.and(Expression.or(sfAccountOwnerId, sfAccountUserId),billingCountry);

        LookupCriteriaVisitor lookupCriteria = new LookupCriteriaVisitor(new GraphContext(), expression, tokenHelper,
                Map.of(), List.of(), (key) -> false);

        String incomingHash = DigestUtils.md5Hex(lookupCriteria.createCriteria().toString());
    }*/

   /* @Benchmark()
    @BenchmarkMode(Mode.AverageTime)
    @OutputTimeUnit(TimeUnit.MICROSECONDS)
    public void filterEval(Blackhole blackhole) throws IOException {
        GraphContext graphContext = new GraphContext();
        graphContext.put("field_sfdc_account_owner_id","0056Q000007Z945QAC");
        graphContext.put("field_act_src","Outbound-G2");
        graphContext.put("field_sfdc_created_date", ZonedDateTime.now().minusDays(3));

        final FilterEvaluationVisitor filterEvaluationVisitor = new FilterEvaluationVisitor(graphContext, tokenHelper);
        Expression sfAccountOwnerId = Expression.eq(Expression.var("field_sfdc_account_owner_id"),Expression.lit("0056Q000007Z945QAC"));
        Expression sdrAssignmentStatus = Expression.empty(Expression.var("field_sdr"));
        Expression billingCountry = Expression.empty(Expression.var("field_country"));
        Expression actSource =  Expression.eq(Expression.var("field_act_src"),Expression.lit("Clearbit Created Account"));
        Expression actSource2 =  Expression.eq(Expression.var("field_act_src"),Expression.lit("Outbound-G2"));
        Expression createdDate =  Expression.gte(Expression.var("field_sfdc_created_date"),Expression.lit("before 48 hours"));
        Expression filterExp = Expression.and(Expression.and(Expression.and(Expression.and(sfAccountOwnerId, sdrAssignmentStatus),Expression.or(actSource, actSource2)),billingCountry),createdDate);
        filterExp.accept(filterEvaluationVisitor);
    }*/

    /*@Benchmark
    @BenchmarkMode(Mode.AverageTime)
    @OutputTimeUnit(TimeUnit.MICROSECONDS)
    public void testJTwigFilter(Blackhole blackhole) throws IOException {

        var filter =   new FunctionCall().setFunctionDefinition(FunctionsSeed.get("filter", com.syncari.core.model.util.Scope.ATTRIBUTE))
                .setParams(List.of(ParameterValue.string("zendesk.account.name", "input")));

        var predicate1 = Expression.lt(Expression.var("zendesk.account.revenue"), Expression.lit(500));
        var predicate2 = Expression.gt(Expression.var("zendesk.account.revenue"), Expression.lit(100));

*//*

        var predicate1 = Expression.lt(Expression.var("zendesk.account.revenue"), Expression.lit(ThreadLocalRandom.current().nextDouble(401, 500)));
        var predicate2 = Expression.gt(Expression.var("zendesk.account.revenue"), Expression.lit(ThreadLocalRandom.current().nextDouble(0, 199)));
*//*

*//*
        var predicate3 = Expression.lt(Expression.var("zendesk.account.revenue"), Expression.lit(ThreadLocalRandom.current().nextDouble(401, 500)));
        var predicate4 = Expression.gt(Expression.var("zendesk.account.revenue"), Expression.lit(ThreadLocalRandom.current().nextDouble(0, 199)));

*//*
        var predicate = Expression.and(predicate1, predicate2);

        ExpressionToMapVisitor mapper = new ExpressionToMapVisitor();
        predicate.accept(mapper);
        var predicateMap = Map.of("predicates", List.of(mapper.getMap()), "operator", "AND");

        //JTwigPipelineEvaluator evaluator = new JTwigPipelineEvaluator(environment, tokenHelper, null);

        filter.setConfig(Map.of("predicate", predicateMap));

        GraphContext context = creatSimpleContext(Map.of(
                "zendesk", Map.of("account", Map.of("name", "SOme Acct Name","revenue", Double.toString(ThreadLocalRandom.current().nextDouble(200, 400))))
        ));

        context.put("functionCall",filter);
        context.put("context", context);
        var result = evaluateFilter(filter.compileFilter(tokenHelper), filter, context);
        blackhole.consume(result);
    }


    @Benchmark
    @BenchmarkMode(Mode.AverageTime)
    @OutputTimeUnit(TimeUnit.MICROSECONDS)
    public void testCustomFilter(Blackhole blackhole) throws IOException {

        var filter =   new FunctionCall().setFunctionDefinition(FunctionsSeed.get("filter", com.syncari.core.model.util.Scope.ATTRIBUTE))
                .setParams(List.of(ParameterValue.string("zendesk.account.name", "input")));


        var predicate1 = Expression.lt(Expression.var("zendesk.account.revenue"), Expression.lit(500));
        var predicate2 = Expression.gt(Expression.var("zendesk.account.revenue"), Expression.lit(100));

*//*

        var predicate1 = Expression.lt(Expression.var("zendesk.account.revenue"), Expression.lit(ThreadLocalRandom.current().nextDouble(401, 500)));
        var predicate2 = Expression.gt(Expression.var("zendesk.account.revenue"), Expression.lit(ThreadLocalRandom.current().nextDouble(0, 199)));
*//*

*//*
        var predicate3 = Expression.lt(Expression.var("zendesk.account.revenue"), Expression.lit(ThreadLocalRandom.current().nextDouble(401, 500)));
        var predicate4 = Expression.gt(Expression.var("zendesk.account.revenue"), Expression.lit(ThreadLocalRandom.current().nextDouble(0, 199)));
*//*

        var predicate = Expression.and(predicate1, predicate2);

        ExpressionToMapVisitor mapper = new ExpressionToMapVisitor();
        predicate.accept(mapper);
        var predicateMap = Map.of("predicates", List.of(mapper.getMap()), "operator", "AND");

        //JTwigPipelineEvaluator evaluator = new JTwigPipelineEvaluator(environment, tokenHelper, null);

        filter.setConfig(Map.of("predicate", predicateMap));

        GraphContext context = creatSimpleContext(Map.of(
                "zendesk", Map.of("account", Map.of("name", "SOme Acct Name","revenue", Double.toString(ThreadLocalRandom.current().nextDouble(200, 400))))
        ));

        context.put("functionCall",filter);
        context.put("context", context);
        var result = filter.evaluateFilter(context, tokenHelper);
        blackhole.consume(result);
    }*/


    private GraphContext creatSimpleContext(Map<String, Object> ctx) {
        return new GraphContext(ctx).setCurrentBatch(new CurrentBatch(null)
                .setCurrentBatchId(ObjectId.get().toHexString()))
                .setGraph(new MappingGraph().setScope(com.syncari.core.model.util.Scope.ATTRIBUTE))
                .setCurrentNode(new MappingNode().setConfiguration(new SimpleFunctionNodeConfig()).setName("My Custom Node"));
    }

    public static void main(String[] args) throws Exception {
        org.openjdk.jmh.Main.main(args);
    }

    /*protected Object evaluateFilter(String filterBody,FunctionCall call,GraphContext context){
        try {
            ResourceReference resource = new ResourceReference(
                    ResourceReference.STRING,
                    filterBody

            );
            JtwigTemplate jtwigTemplate = new JtwigTemplate(environment, resource);
            JtwigModelSanitizer sanitizer = JtwigModelSanitizer.newModel(context);
            JtwigModel model = JtwigModel.newModel(sanitizer.getValues());

            Object result = jtwigTemplate.render(model);
            Object extractedResult = extractResult(result);
            if(extractedResult!=null && extractedResult instanceof FunctionResult){
                FunctionResult functionResult = FunctionResult.class.cast(extractedResult);
                return functionResult.getResult();
            }
            return  extractedResult;
        }catch(TerminateExecutionPathException e){
            throw e;
        } catch (Exception e) {
            throw new RuntimeException(e);
        } finally {
            FunctionSideChannel.remove();
        }

    }*/

}
