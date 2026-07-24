package com.syncari.dbm;

import java.util.Arrays;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.CommandLineRunner;
import org.springframework.stereotype.Component;
import org.springframework.util.ObjectUtils;

import picocli.CommandLine;
import picocli.CommandLine.Command;
import picocli.CommandLine.Option;

@Component
public class SyncariCommandLine implements CommandLineRunner {
    @Autowired
    MigrateCommand migrationCommand;

    @Autowired
    ExecuteCommand executeCommand;

    @Autowired
    CustomScriptCommand customScriptCommand;

    @Override
    public void run(String... args) throws Exception {
        
        if(!ObjectUtils.isEmpty(args) && Arrays.asList(args).contains("cli")) {
            CommandLine cmd = new CommandLine(new SyncariCommand()).addSubcommand(migrationCommand)
                .addSubcommand(executeCommand).addSubcommand(customScriptCommand);
            cmd.setUnmatchedArgumentsAllowed(true);
            int exitCode = cmd.execute(args);
            System.exit(exitCode);
        }

    }
    
}

@Command(name = "syncari", header = "Syncari Commandline. Used for one-off commands like database migration, Data/Schema fixup etc", version = {
        "Syncari Command Line 1.0", "(c) 2021, Syncari, Inc" })
class SyncariCommand implements Runnable {
    @Option(description = "Help", names = { "-h", "--help", "-?", "-help" }, usageHelp = true, required = false)
    private boolean helpRequested;

    @Override
    public void run() {
        new CommandLine(this).usage(System.out);
    }
}
