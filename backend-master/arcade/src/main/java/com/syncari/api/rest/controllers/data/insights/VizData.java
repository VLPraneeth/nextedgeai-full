package com.syncari.api.rest.controllers.data.insights;

import com.syncari.api.rest.controllers.data.ErrorDTO;
import lombok.Data;

@Data
public abstract class VizData {
    ErrorDTO error;
}
