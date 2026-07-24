package com.syncari.core.model.insights;

import lombok.Data;
import lombok.experimental.Accessors;

import java.util.List;

@Data
@Accessors(chain = true)
public class DataCardSetting {

    private String datacardId;
    private DashboardLayout layout;

    @Override
    public String toString(){
        return String.format(" datacardId %s , layout %s", datacardId, layout);
    }
}
