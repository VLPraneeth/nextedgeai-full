package com.syncari.dbm.customscripts.customer;

import com.github.mongobee.changeset.ChangeSet;
import com.syncari.core.MigrationContext;
import com.syncari.core.model.Layout;
import com.syncari.core.repositories.customer.LayoutRepo;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.springframework.data.mongodb.core.MongoTemplate;

import java.util.Optional;

@Slf4j
public class SYN_16402_RemoveLayout {

    @ChangeSet(order = "001", id = "removeLayoutById", author = "rohit", runAlways = true)
    public void removeLayoutById(MongoTemplate template) {
        var dryRun = Boolean.parseBoolean(System.getProperty("dryRun"));
        var layoutId = System.getProperty("id");
        LayoutRepo layoutRepo = MigrationContext.getLayoutRepo();
        if (StringUtils.isNotEmpty(layoutId)) {
            Optional<Layout> layout = layoutRepo.findById(layoutId);
            layout.ifPresentOrElse(l -> {
                if (!dryRun) {
                    log.info("Layout with id {} is present to delete", layoutId);
                    layoutRepo.deleteById(layoutId);
                } else {
                    log.info("Layout with id {} is present but running in dry runmode", layoutId);
                }
            }, () -> log.info("Layout with id {} is not present", layoutId));
        }
    }
}
