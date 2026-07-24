package com.syncari.api.rest.controllers.data;

import lombok.Data;

@Data
public class FeedbackRequest {
    String reason;
    String feedback;
}
