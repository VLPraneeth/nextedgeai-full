package com.syncari.dbm.dbclient;

import com.github.mongobee.changeset.ChangeEntry;
import com.github.mongobee.dao.ChangeEntryDao;
import com.github.mongobee.exception.MongobeeChangeSetException;
import com.github.mongobee.exception.MongobeeConnectionException;
import com.github.mongobee.exception.MongobeeException;
import com.github.mongobee.utils.ChangeService;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.List;

import com.mongodb.DB;
import com.mongodb.MongoClient;
import com.mongodb.client.MongoDatabase;
import com.syncari.core.exceptions.SyncariValidationException;

import org.jongo.Jongo;
import org.springframework.core.env.Environment;
import org.springframework.data.mongodb.core.MongoTemplate;

import lombok.extern.slf4j.Slf4j;

import static org.springframework.util.StringUtils.hasText;

@Slf4j
public class Syncaribee {

    private static final String DEFAULT_CUSTOMSCRIPTSLOG_COLLECTION_NAME = "customscriptsLog";
    private static final String DEFAULT_LOCK_COLLECTION_NAME = "syncaribeelock";

    private MongoClient mongoClient;
    private String dbName;
    private ChangeEntryDao dao;
    private String customScriptsFileName;
    private Environment springEnvironment;

    private MongoTemplate mongoTemplate;
    private Jongo jongo;

    public Syncaribee(MongoClient mongoClient) {
        this.mongoClient = mongoClient;
        this.dao = new ChangeEntryDao(DEFAULT_CUSTOMSCRIPTSLOG_COLLECTION_NAME, DEFAULT_LOCK_COLLECTION_NAME, true, 30, 5, true);
    }
    
    /**
     * Executing one-off migration
     *
     * @throws SyncariValidationException exception
     */
    public void execute() throws SyncariValidationException, MongobeeException {

        validateConfig();

        if (this.mongoClient != null) {
            dao.connectMongoDb(this.mongoClient, dbName);
        } else {
            throw new SyncariValidationException("Cannot find a valid mongoclient.");
        }

        if (!dao.acquireProcessLock()) {
            log.info("Syncaribee did not acquire process lock. Exiting.");
            return;
        }

        log.info("Syncaribee acquired process lock, starting the custom (one-off) script migration sequence..");

        try {
            executeMigration();
        } finally {
            log.info("Syncaribee is releasing process lock.");
            dao.releaseProcessLock();
        }

        log.info("Syncaribee has finished his job.");
    }

    private void executeMigration() throws MongobeeConnectionException, MongobeeException {

        ChangeService service = new ChangeService(customScriptsFileName, springEnvironment);
        boolean isDryrun = Boolean.parseBoolean(System.getProperty("dryRun"));
            
        try {
            Class<?> customScript = Class.forName(customScriptsFileName);
            Object changelogInstance = null;
            changelogInstance = customScript.getConstructor().newInstance();
            List<Method> changesetMethods = service.fetchChangeSets(changelogInstance.getClass());

            for (Method changesetMethod : changesetMethods) {
                ChangeEntry changeEntry = service.createChangeEntry(changesetMethod);

                try {
                    if (dao.isNewChange(changeEntry)) {
                        executeChangeSetMethod(changesetMethod, changelogInstance, dao.getDb(), dao.getMongoDatabase());
                        if (!isDryrun) {
                            dao.save(changeEntry);
                            log.info(changeEntry + " applied");
                        } else {
                            log.info("Running in Dry Mode, not updating changelogs");
                        }
                    } else if (service.isRunAlwaysChangeSet(changesetMethod)) {
                        executeChangeSetMethod(changesetMethod, changelogInstance, dao.getDb(), dao.getMongoDatabase());
                        log.info(changeEntry + " reapplied");
                    } else {
                        log.info(changeEntry + " passed over");
                    }
                } catch (MongobeeChangeSetException e) {
                    log.error(e.getMessage());
                }
            }
        } catch (ClassNotFoundException e) {
            throw new SyncariValidationException(e.getMessage(), e);
        } catch (NoSuchMethodException e) {
            throw new MongobeeException(e.getMessage(), e);
        } catch (IllegalAccessException e) {
            throw new MongobeeException(e.getMessage(), e);
        } catch (InvocationTargetException e) {
            Throwable targetException = e.getTargetException();
            throw new MongobeeException(targetException.getMessage(), e);
        } catch (InstantiationException e) {
            throw new MongobeeException(e.getMessage(), e);
        }
    }

    private Object executeChangeSetMethod(Method changeSetMethod, Object changeLogInstance, DB db, MongoDatabase mongoDatabase)
        throws IllegalAccessException, InvocationTargetException, MongobeeChangeSetException {

        if (changeSetMethod.getParameterTypes().length == 1
            && changeSetMethod.getParameterTypes()[0].equals(DB.class)) {
            log.debug("method with DB argument");

            return changeSetMethod.invoke(changeLogInstance, db);
        } else if (changeSetMethod.getParameterTypes().length == 1
            && changeSetMethod.getParameterTypes()[0].equals(Jongo.class)) {
            log.debug("method with Jongo argument");

            return changeSetMethod.invoke(changeLogInstance, jongo != null ? jongo : new Jongo(db));
        } else if (changeSetMethod.getParameterTypes().length == 1
            && changeSetMethod.getParameterTypes()[0].equals(MongoTemplate.class)) {
            log.debug("method with MongoTemplate argument");

            return changeSetMethod.invoke(changeLogInstance, mongoTemplate);
        } else if (changeSetMethod.getParameterTypes().length == 2
            && changeSetMethod.getParameterTypes()[0].equals(MongoTemplate.class)
            && changeSetMethod.getParameterTypes()[1].equals(Environment.class)) {
            log.debug("method with MongoTemplate and environment arguments");

            return changeSetMethod.invoke(changeLogInstance, mongoTemplate);
        } else if (changeSetMethod.getParameterTypes().length == 1
            && changeSetMethod.getParameterTypes()[0].equals(MongoDatabase.class)) {
            log.debug("method with DB argument");

            return changeSetMethod.invoke(changeLogInstance, mongoDatabase);
        } else if (changeSetMethod.getParameterTypes().length == 0) {
            log.debug("method with no params");

            return changeSetMethod.invoke(changeLogInstance);
        } else {
            throw new MongobeeChangeSetException("ChangeSet method " + changeSetMethod.getName() +
                " has wrong arguments list. Please see docs for more info!");
        }
    }

    public Syncaribee setDbName(String dbName) {
        this.dbName = dbName;
        return this;
    }

    public Syncaribee setMongoTemplate(MongoTemplate mongoTemplate) {
        this.mongoTemplate = mongoTemplate;
        return this;
    }

    public Syncaribee setCustomScriptsFileName(String customScriptsFileName) {
        this.customScriptsFileName = customScriptsFileName;
        return this;
    }

    private void validateConfig() throws SyncariValidationException {
        if (!hasText(dbName)) {
            throw new SyncariValidationException("DB name is not set. It should be defined in MongoDB URI or via setter");
        }
        if (!hasText(customScriptsFileName)) {
            throw new SyncariValidationException("Custom scripts file name is not set: use appropriate setter");
        }
        if (mongoTemplate == null) {
            throw new SyncariValidationException("MongoTemplate is not set: use appropriate setter");
        }
    }
    
}
