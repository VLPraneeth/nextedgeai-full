package com.syncari.api.rest.controllers.data;

import lombok.Data;
import lombok.experimental.Accessors;

import java.util.List;

@Data
@Accessors(chain = true)
public class WizardStep{
    String stepName;
    List<String> fields;
}
