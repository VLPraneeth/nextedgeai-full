package com.syncari.core.repositories.customer;

import com.syncari.core.model.QuickStartRun;

import java.util.List;

public interface CustomQuickStartRunRepo {

    List<QuickStartRun> getHistoryByQuickStartType(String qsType);
}
