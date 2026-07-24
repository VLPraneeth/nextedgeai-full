package com.syncari.core.quickstart;

import com.syncari.utils.I18n;
import lombok.Data;

@Data
public abstract class AbstractQuickStartConfig implements QuickStartConfig {

    String name;

    @Override
    public String getName() {
        return name;
    }

    @Override
    public String getDisplayName() {
        return I18n.i18n(name);
    }
}
