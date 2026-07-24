package com.syncari.core.quickstart.v2;

import com.syncari.utils.KeyValue;
import lombok.Data;
import lombok.experimental.Accessors;

import java.util.ArrayList;
import java.util.List;

@Data
@Accessors(chain = true)
public class QSInstallWizardConfig {
    String displayName;
    String title;
    String id;
    List<String> requiredSynapses = new ArrayList<>();
    List<KeyValue> configuration = new ArrayList<>();
    List<KeyValue> steps = new ArrayList<>();

}
