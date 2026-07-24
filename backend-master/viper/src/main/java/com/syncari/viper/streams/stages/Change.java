package com.syncari.viper.streams.stages;

import com.syncari.connector.EntityData;
import com.syncari.core.model.TransactionLog;
import lombok.AllArgsConstructor;
import lombok.Data;

import java.util.Optional;

@Data
@AllArgsConstructor
class Change {
    private final TransactionLog transactionLog;
    private final EntityData changes;
    private Optional<EntityData> existing;
}
