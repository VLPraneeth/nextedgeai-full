package com.syncari.core.pipeline.jtwig;

import org.jtwig.environment.Environment;
import org.jtwig.environment.EnvironmentConfiguration;
import org.jtwig.environment.EnvironmentConfigurationBuilder;
import org.jtwig.environment.EnvironmentFactory;
import org.jtwig.escape.EscapeEngine;
import org.jtwig.model.tree.OutputNode;
import org.jtwig.render.expression.CalculateExpressionService;
import org.jtwig.render.node.renderer.NodeRender;
import org.jtwig.renderable.impl.StringRenderable;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.DependsOn;
import org.springframework.context.annotation.Primary;

@Configuration
public class JTwigEnvironment {
    @Autowired
    private SyncariExtension syncariExtension;

    @DependsOn("syncariExtension")
    @Bean
    @Primary
    Environment jtwigEnvironment() {
        EnvironmentConfiguration configuration = EnvironmentConfigurationBuilder.configuration()
                .extensions().add(syncariExtension)

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

        EnvironmentFactory environmentFactory = new EnvironmentFactory();
        return environmentFactory.create(configuration);
    }



}

