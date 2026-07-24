package com.syncari.viper.scheduler;

import com.syncari.core.service.PipelineNodeAuditService;
import com.syncari.viper.config.ViperConfig;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;

@Component
@Slf4j
public class NodeAuditWriter {
    @Autowired
    protected PipelineNodeAuditService nodeAuditService;
    /**
     * This version of spring only supports a global executor
     * for schedulers, with no control on task allocation by scheduler.
     * So we use the global executor to just trigger this scheduler,
     * but use another executor service to put multiple audit log
     * consumers to work. Remove and reconfigure
     * this when we upgrade to Spring 6.1+
     */
    @Autowired
    @Qualifier("nodeAuditWriterPool")
    protected ExecutorService nodeAuditWriterPool;

    @Scheduled(fixedDelay = 100)
    public void process() {
        List<CompletableFuture<Void>> tasks = new ArrayList<>();
        for (int i = 0; i < ViperConfig.MAX_NODE_AUDIT_WORKERS; i++) {
            tasks.add(CompletableFuture.runAsync(() -> {
                try {
                    nodeAuditService.flush();
                } catch (Exception e) {
                    log.error(e.getMessage(), e);
                }
            }));
        }
        //merge all tasks
        final CompletableFuture<Void> merged = CompletableFuture.allOf(tasks.toArray(new CompletableFuture[]{}));
        //wait for them to finish
        merged.join();
    }
}
