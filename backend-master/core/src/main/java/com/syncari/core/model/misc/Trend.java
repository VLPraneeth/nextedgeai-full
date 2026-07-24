package com.syncari.core.model.misc;

import java.util.HashMap;
import java.util.Map;

import lombok.AllArgsConstructor;
import lombok.Data;

@Data
@AllArgsConstructor
public class Trend {
    int rangeInDays;
    int deltaPercent;
    Map<String, Integer> dataPoints = new HashMap<String, Integer>();
    
}