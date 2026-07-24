package com.syncari.api.rest.controllers.data.studio;

import com.syncari.core.model.misc.DataScoreCard;

import lombok.Data;

@Data
public class DataScoreCardResponse {
    ScoreStatus status;
    DataScoreCard data;
}