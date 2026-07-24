package com.syncari.core.repositories.customer;

import com.syncari.core.model.ActionDefinition;
import com.syncari.core.quickstart.v2.QuickStart;
import com.syncari.core.repositories.DraftableRepo;
import org.springframework.data.mongodb.repository.Query;

import java.util.List;
import java.util.Optional;

public interface ActionRepo extends DraftableRepo<ActionDefinition> {}
