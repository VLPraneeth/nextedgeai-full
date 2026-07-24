package com.syncari.core.repositories.customer;

public interface CustomDataQualityRuleRepo {
    void moveRulesToOtherCategory(String categoryId, String otherCategoryId);
}
