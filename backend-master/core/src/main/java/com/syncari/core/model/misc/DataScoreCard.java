package com.syncari.core.model.misc;

import java.util.ArrayList;
import java.util.List;

import com.syncari.core.Link;

import lombok.Data;

@Data
public class DataScoreCard {
    int score;
    String label;
    Integer sourceScore;
    int percentIncrease;
    List<ScoreContributingFactor> factors = new ArrayList<>();
    Trend trend;
    String entityName;
    List<Link> links;
    
    public void addFactor(ScoreContributingFactor factor) {
        factors.add(factor);
    }
}