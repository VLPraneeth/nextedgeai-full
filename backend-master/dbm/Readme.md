<h1> dbm project</h1>

This new project has three capabilities.
* Run migrations as part of the regular upgrade for all customers using `migrate` cli command. This runs changelogs from these packages as before `com.syncari.core.changelogs.syncari` and `com.syncari.core.changelogs.customer`
* Run custom BSON command scripts using `execute` cli command.
* Run custom scripts (java based) using `customscript` cli command. This runs customscripts (the one specified in the command) from these locations `com.syncari.dbm.customscripts.syncari` and `com.syncari.dbm.customscripts.customer`

<h2>Details

<h3>Migrate command

We can now run the following CLI migration commands from this project instead of arcade
```
mvn clean install -DskipTests -pl dbm spring-boot:run \
 -Dspring-boot.run.arguments=cli,migrate,--target,syncari

mvn clean install -DskipTests -pl dbm spring-boot:run \
 -Dspring-boot.run.arguments=cli,migrate,--target,customer,--sids,syncari_admin
```

<h3>Customscript command

Also can run one-off custom commands like this from a file,
```
mvn clean install -DskipTests -pl dbm spring-boot:run -Dspring-boot.run.arguments="cli,customscript,--target,customer,--sids,syncari_admin,-sn,InstanceConfiguration"
```
The script runs above are run only once and there is a `customscriptslog` collection that keeps track of the scripts run. For scripts that needs to be re-run (Like scoring calculations) just annotate the ChangeSet with `runAlways = true`.
Here is a sample execution with a file that was already executed but a new ChangeSetMethod was added to the same file.
```
13:29:46.110 ::: [main] INFO  com.syncari.dbm.CustomScriptCommand - Applying custom script on Instance Syncari Master Instance:syncari_admin in Organization Syncari Master
13:29:46.135 Syncari Master:Syncari Master Instance:syncari_admin: [main] INFO  com.syncari.dbm.dbclient.Syncaribee - Syncaribee acquired process lock, starting the custom (one-off) script migration sequence..
13:29:46.139 Syncari Master:Syncari Master Instance:syncari_admin: [main] INFO  com.syncari.dbm.dbclient.Syncaribee - [ChangeSet: id=setTestFlag, author=sudee, changeLogClass=com.syncari.dbm.customscripts.customer.InstanceConfiguration, changeSetMethod=setTestFlag] passed over
13:29:46.163 Syncari Master:Syncari Master Instance:syncari_admin: [main] INFO  com.syncari.dbm.dbclient.Syncaribee - [ChangeSet: id=setTestFlag2, author=sudee, changeLogClass=com.syncari.dbm.customscripts.customer.InstanceConfiguration, changeSetMethod=setTestFlag2] applied
13:29:46.163 Syncari Master:Syncari Master Instance:syncari_admin: [main] INFO  com.syncari.dbm.dbclient.Syncaribee - Syncaribee is releasing process lock.
13:29:46.166 Syncari Master:Syncari Master Instance:syncari_admin: [main] INFO  com.syncari.dbm.dbclient.Syncaribee - Syncaribee has finished his job.
13:29:46.175 Syncari Master:Syncari Master Instance:syncari_admin: [main] INFO  com.syncari.dbm.CustomScriptCommand - Applied custom script for customer:[syncari_admin]:1
```

<h3>Execute command

Also can run one-off commands like below from a file. The files for this is checked into resources executecmd_scripts folder
```
mvn clean install -DskipTests -pl dbm spring-boot:run \
 -Dspring-boot.run.arguments="cli,execute,--target,customer,--sids,all,-f,<PATH>/SampleCommand.sql"
```