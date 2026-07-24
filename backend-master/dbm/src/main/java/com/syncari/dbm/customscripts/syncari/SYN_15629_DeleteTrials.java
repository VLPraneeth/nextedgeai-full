package com.syncari.dbm.customscripts.syncari;

import com.github.mongobee.changeset.ChangeSet;
import com.syncari.core.MigrationContext;
import com.syncari.core.model.Instance;
import com.syncari.core.service.ProvisioningService;
import com.syncari.core.service.SubscriptionService;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.ArrayUtils;
import org.apache.commons.lang3.exception.ExceptionUtils;
import org.springframework.data.mongodb.core.MongoTemplate;

import java.util.ArrayList;
import java.util.List;

@Slf4j
public class SYN_15629_DeleteTrials {

    @ChangeSet(order = "001", id = "deleteTrials", author = "varsha")
    public void deleteTrials(MongoTemplate template) {

        ProvisioningService service = MigrationContext.getProvisioningService();
        SubscriptionService subscriptionService = MigrationContext.getSubscriptionService();
        String[] syncariIds = ArrayUtils.toArray("NBJNCC", "QHC3Z0", "CAAAQR", "YPXG0O", "SPMFKF", "MGPFFA", "IWEKRY", "ULMJRJ", "ZZB3SW", "ZBD7LD", "BCBUUM", "EFYMGB", "IJ0XT9", "4W2EZM", "XTND6O", "0OIPSP", "CQXCOM", "YD2HYU", "KGRHYU", "VYEH7C", "YSRQAE", "CBFLWK", "2CWEKG", "CFM3KX", "S6UDBV", "BHYYH2", "BQBODY", "UWRZWF", "8FXFSL", "P6EVHH", "BL4AAQ", "1D2RWM", "BDEQFS", "IA1DLI", "OIVRST", "JYFFSA", "SKR2JK", "XXHYAF", "EGTLCQ", "NLFUFN", "LKJO2M", "BBWRKP", "ZEUDVS", "DZSVCN", "4RZASI", "OK8DTC", "UZ4FLC", "UHMIXR", "YOUNAD", "DZM75F", "SEG0FN", "OP9BOD", "HWNUPR", "ZP2AHG", "TTQCTZ", "FXLZ5A", "MIJ492", "GKIY9D", "J17YLN", "N68EYL", "JCRTOQ", "7RJM0Y", "LZW0MJ", "8SDNAD", "ZCANF6", "S8V9JF", "CFEMVQ", "PCWO80", "DKHVV9", "SWBJJZ", "VGD5UJ", "PKEULE", "PJGZMI", "ZG6BG2", "AZGLUR", "KHNORF", "7RTYJR", "9HNHN7", "S0OKPD", "BRCPIZ", "MW7YBK", "URRXND", "XAILFB", "LYEKA0", "6PUIWA", "V9K0YE", "OWF4DF", "O30DOX", "CSVW6L", "WH2FEW");
        for (String id : syncariIds) {
            try {
                Instance instance = subscriptionService.getInstance(id);
                if(!instance.isTrial()) {
                    log.error("Instance {} is not a trial", id);
                    continue;
                }
                log.info("Starting deprovision for instance {} ", id);
                service.deprovisionInstance(id, true);
                log.info("Deprovisioned instance {} ", id);
            } catch (Exception e) {
                log.error("Error deprovisioning instance {} ", id);
                log.error(ExceptionUtils.getStackTrace(e));
            }
        }
    }

}