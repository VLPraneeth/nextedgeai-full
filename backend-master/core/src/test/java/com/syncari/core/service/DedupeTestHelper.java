package com.syncari.core.service;

import com.syncari.core.pipeline.DynamicDispatchVisitor;
import com.syncari.core.pipeline.ExpressionToMapVisitor;
import com.syncari.core.pipeline.expression.Expression;
import org.bson.types.ObjectId;

import java.util.*;
import java.util.stream.Collectors;

public class DedupeTestHelper {
    public static Map<String, Object> toFindDupesMap(Expression... expressions) {
        ExpressionToMapVisitor visitor = new ExpressionToMapVisitor();
        List<Map<String, Object>> predicateMaps = Arrays.asList(expressions).stream().map(e -> toCompositeMap(visitor, e,"findDupesPredicate")).collect(Collectors.toList());
        return Map.of("configId", ObjectId.get().toHexString(), "name", "findDupes", "compositeValues", predicateMaps);
    }

    public static Map<String, Object> toSelectWinnerMap(Expression... expressions) {

        List<Map<String, Object>> predicateMaps =new ArrayList<>();
        for(Expression expression : expressions) {
            ExpressionToMapVisitor visitor = new ExpressionToMapVisitor();
            Map<String, Object> winnerSelectionPredicate = toCompositeMap(visitor, expression, "winnerSelectionPredicate");
            predicateMaps.add(winnerSelectionPredicate);
        }
        return Map.of("configId", ObjectId.get().toHexString(), "name", "selectWinner", "compositeValues", predicateMaps);
    }

    public static Map<String, Object> toSelectWinnerMap(String... nameFieldPairs) {
        List<Map<String, Object>> predicateMaps =new ArrayList<>();
        for(int i=0;i<nameFieldPairs.length-1;i+=2) {

            Map<String, Object> predicateMap = new HashMap<>();
            Map<String, Object> predicate = new HashMap<>();

            predicate.put("operator", "AND");
            predicate.put("predicates", List.of(Map.of("operator", nameFieldPairs[i], "left", Map.of("type","variable","value", nameFieldPairs[i+1]))));
            predicateMap.put("winnerSelectionPredicate",Map.of("value",predicate,"name","winnerSelectionPredicate"));
            predicateMaps.add(predicateMap);
        }
        return Map.of("configId", ObjectId.get().toHexString(), "name", "selectWinner", "compositeValues", predicateMaps);
    }

    public static Map<String, Object> toCompositeMap(ExpressionToMapVisitor visitor, Expression expression, String predicateName) {
        expression.accept(new DynamicDispatchVisitor(visitor));
        Map<String, Object> predicateMap = visitor.getMap();
        return Map.of(predicateName,Map.of("name",predicateName,"value",predicateMap),"repeatId",ObjectId.get().toHexString());
    }

}
