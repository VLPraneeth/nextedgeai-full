package com.syncari.dbm;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;

import com.syncari.dbm.dbclient.SyncariMongoDBClient;

import org.apache.commons.lang3.StringUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import org.springframework.util.ObjectUtils;

import lombok.extern.slf4j.Slf4j;
import picocli.CommandLine.Command;
import picocli.CommandLine.Option;

@Component
@Slf4j
@Command(name = "execute", header = "Execute one-off custom scripts on Syncari DB or one or more Customer DBs")
public class ExecuteCommand implements Runnable {

    private static final String ALL = "all";

    private static final String SYNCARI = "syncari";

    private static final String CUSTOMER = "customer";

    @Autowired
    SyncariMongoDBClient client;

    @Option(required = true, description = "Target system. Specifiy 'syncari' or 'customer'", names = { "--target",
            "-t" })
    String target;

    @Option(description = "Help", names = { "-h", "--help", "-?", "-help" }, required = false)
    private boolean helpRequested;

    @Option(description = "Comma separated list of syncari ids, or the special value 'all'. Required when target is 'customer'", names = {
        "-s", "--sids" }, required = false, arity = "1..*")
    private String[] sid;

    @Option(description = "SQL file with BSON commands", names = { "-f", "--file" }, required = true)
    private String fileName;

    @Option(description = "Is for write operation", names = { "-w", "--write" }, required = false)
    private String writeMode = "false";

    @Override
    public void run() {
        
        client.setWriteMode("true".equalsIgnoreCase(writeMode));
        String commandStr = "";
        
        if (ObjectUtils.isEmpty(fileName)) {
            log.error("ERROR: Execute/Exec command requires the 'fileName' to be specified.");
            return;
        }
        log.info("fileName is {}", fileName);
        try {
            commandStr = Files.readString(Path.of(fileName), StandardCharsets.US_ASCII);
        } catch (IOException e) {
            log.error("ERROR: Failed to load contents of file {}", fileName);
            return;
        }
        log.info("commandStr is {} ", commandStr);

        if (isSyncariDB()) {
            client.executeCommand(client.getSyncariDb(), commandStr);
            return;
        }

        if (isCustomerDB()) {
            if (ObjectUtils.isEmpty(sid)) {
                log.error("ERROR: At least one sid required when target is 'customer'.");
                return;
            }
            List<String> sids = Arrays.asList(sid);
            sids = sids.stream().map(x -> x.trim()).filter(x -> !StringUtils.isEmpty(x)).collect(Collectors.toList());

            client.executeForCustomers(commandStr, sids);
            return;
        }
    }

    private boolean isSyncariDB() {
        return SYNCARI.equals(target);
    }

    private boolean isCustomerDB() {
        return CUSTOMER.equals(target);
    }
    
}
