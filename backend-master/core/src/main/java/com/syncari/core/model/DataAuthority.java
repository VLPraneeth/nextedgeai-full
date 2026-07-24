package com.syncari.core.model;

import lombok.Data;
import lombok.experimental.Accessors;

import javax.validation.constraints.NotNull;
import java.io.Serializable;
import java.util.HashMap;
import java.util.Map;

@Data
@Accessors(chain = true)
public class DataAuthority  implements Serializable {

    private DatAuthorityStrategy datAuthorityStrategy = DatAuthorityStrategy.NONE;

    private Map<String, Object> dataAuthorityConfiguration=new HashMap<>();

    public static DataAuthority latest(){
        return new DataAuthority().setDatAuthorityStrategy(DatAuthorityStrategy.LATEST_RECORD);
    }
    public static DataAuthority none(){
        return new DataAuthority().setDatAuthorityStrategy(DatAuthorityStrategy.NONE);
    }

    public static DataAuthority selectedConnector(@NotNull  String connectorId){
        return new DataAuthority().setDatAuthorityStrategy(DatAuthorityStrategy.SELECTED_CONNECTOR)
                .setDataAuthorityConfiguration(Map.of("connectorId",connectorId));

    }

    public Map<String, Object> getConfigMap(){
        var configMap = new HashMap<String, Object>();
        configMap.put("dataAuthorityStrategy",datAuthorityStrategy.name());
        configMap.putAll(dataAuthorityConfiguration);
        return configMap;
    }

}
