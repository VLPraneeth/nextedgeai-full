package com.syncari.core.model.misc;

import java.util.ArrayList;
import java.util.List;

import com.syncari.core.model.EntityDataScoreSnapshot;
import com.syncari.core.model.FieldDataScoreSnapshot;

import lombok.Data;

@Data
public class EntityScoreWrapper {
    EntityDataScoreSnapshot entityScore = new EntityDataScoreSnapshot();
    List<FieldDataScoreSnapshot> fieldScores = new ArrayList<>();
}
