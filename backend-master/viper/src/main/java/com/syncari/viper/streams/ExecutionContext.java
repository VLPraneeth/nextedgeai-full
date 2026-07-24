package com.syncari.viper.streams;

import java.util.Map;

import lombok.AllArgsConstructor;

@AllArgsConstructor
public class ExecutionContext {
    private Map<String, Object> context;

    public <T> T get(String path){
        return get(path.split("\\."));
    }
    public boolean contains(String path){
        return contains(path.split("\\."));
    }

    public <T> T get(String... path) {
        var current = context;
        for(int i=0;i<path.length;i++){
            if(current.containsKey(path[i]) && current.get(path[i]) instanceof Map){
                current = (Map<String, Object>) current.get(path[i]);
            }else{
                return (T) current.get(path[i]);
            }
        }
        return null;
    }

    public boolean contains(String... path) {
        var current = context;
        for(int i=0;i<path.length;i++){
            if(current.containsKey(path[i]) && current.get(path[i]) instanceof Map){
                current = (Map<String, Object>) current.get(path[i]);
            }else{
                return current.containsKey(path[i]);
            }
        }
        return false;
    }


}
