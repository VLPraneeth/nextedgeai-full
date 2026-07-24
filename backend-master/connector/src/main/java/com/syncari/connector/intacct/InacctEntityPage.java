package com.syncari.connector.intacct;

import com.syncari.connector.EntityPage;
import lombok.Data;
import lombok.experimental.Accessors;

@Data
@Accessors(chain = true)
public class InacctEntityPage extends EntityPage {
    private String resultId;
    private Integer totalCount;
}
