package com.syncari.core.model;

import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

public enum EngineType {
    ACTION {
        public String compile(FunctionDefinition function, List<ParameterValue> params) {
            if (params == null || params.isEmpty()) {
                return String.format("{{%s(functionCall,context)}}", function.getName());
            }
            List<String> tail = new ArrayList<>(params.stream().map(p -> p.getContextName()).collect(Collectors.toList()));
            tail.add("functionCall");
            tail.add("context");
            return String.format("{{%s(%s)}}", function.getName(), String.join(",", tail));
        }

        public String compile(FunctionCall functionCall) {
            FunctionDefinition function = functionCall.getFunctionDefinition();
            List<ParameterValue> params = functionCall.getParams();
            
            if (params == null || params.isEmpty()) {
                return String.format("{{%s(functionCall,context)}}", function.getName());
            }
            List<String> tail = new ArrayList<>(params.stream().map(p -> p.getContextName()).collect(Collectors.toList()));
            tail.add("functionCall");
            tail.add("context");
            return String.format("{{%s(%s)}}", function.getName(), String.join(",", tail));
        }
    },
    FUNCTION {
        public String compile(FunctionDefinition function, List<ParameterValue> params) {
            if (params == null || params.isEmpty()) {
                return String.format("{{%s(functionCall,context)}}", function.getName());
            }
            List<String> tail = new ArrayList<>(params.stream().map(p -> p.getContextName()).collect(Collectors.toList()));
            tail.add("functionCall");
            tail.add("context");
            return String.format("{{%s(%s)}}", function.getName(), String.join(",", tail));
        }
        
        public String compile(FunctionCall functionCall) {
            FunctionDefinition function = functionCall.getFunctionDefinition();
            List<ParameterValue> params = functionCall.getParams();
            
            if (params == null || params.isEmpty()) {
                return String.format("{{%s(functionCall,context)}}", function.getName());
            }
            return String.format("{{%s(%s,functionCall, context)}}", function.getName(), functionCall.getCurrentParamName());
        }
    },
    FILTER {
        public String compile(FunctionDefinition function, List<ParameterValue> params) {
            List<String> tail = new ArrayList<>(params.stream().skip(1).map(p -> p.getContextName()).collect(Collectors.toList()));
            tail.add("functionCall");
            tail.add("context");
            return String.format("{{%s | %s(%s)}}", params.get(0).getContextName(), function.getName(), String.join(",", tail));
        }

        public String compile(FunctionCall functionCall) {
            FunctionDefinition function = functionCall.getFunctionDefinition();
            List<ParameterValue> params = functionCall.getParams();
            List<String> tail = new ArrayList<>(params.stream().skip(1).map(p -> p.getContextName()).collect(Collectors.toList()));
            tail.add("functionCall");
            tail.add("context");
            return String.format("{{%s | %s(%s)}}", params.get(0).getContextName(), function.getName(), String.join(",", tail));
        }
    };


    public abstract String compile(FunctionDefinition function, List<ParameterValue> params);

    public abstract String compile(FunctionCall functionCall);


}
