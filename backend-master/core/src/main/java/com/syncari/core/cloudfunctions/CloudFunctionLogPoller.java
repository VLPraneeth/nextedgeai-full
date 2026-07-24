package com.syncari.core.cloudfunctions;

import com.google.api.gax.paging.Page;
import com.google.auth.oauth2.GoogleCredentials;
import com.google.cloud.logging.LogEntry;
import com.google.cloud.logging.Logging;
import com.google.cloud.logging.Logging.EntryListOption;
import com.google.cloud.logging.LoggingOptions;
import com.google.cloud.logging.Payload;
import com.syncari.core.config.AppConfig;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.text.DateFormat;
import java.text.SimpleDateFormat;
import java.util.Base64;


@Slf4j
@Component
public class CloudFunctionLogPoller {

    @Autowired
    AppConfig appConfig;

    public String getErrorLogEntries(String cloudFunctionName, long since) {
        String logName = "cloudfunctions.googleapis.com%2Fcloud-functions";
        StringBuffer logEntries = new StringBuffer("");
        try (Logging logging = LoggingOptions.newBuilder().setCredentials(getCredentials()).build().getService()) {

            // Syncari Text: Keeping this reference text from GCP sample for future reference.
            //
            // When composing a filter, using indexed fields, such as timestamp, resource.type, logName
            // and
            // others can help accelerate the results
            // Full list of indexed fields here:
            // https://cloud.google.com/logging/docs/view/advanced-queries#finding-quickly
            // This sample restrict the results to only last minute to minimize number of API calls
            /*
            Calendar calendar = Calendar.getInstance(TimeZone.getTimeZone("UTC"));
            calendar.add(Calendar.HOUR, -3);
             */
            DateFormat rfc3339 = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSS'Z'");
            String logFilter =
                    "logName=projects/" + logging.getOptions().getProjectId() + "/logs/" + logName
                            + " AND resource.labels.function_name = \"" + cloudFunctionName + "\" "
                            + " AND (severity>=ERROR OR \" ERROR \") "
                            + " AND timestamp>=\"" + rfc3339.format(since) + "\"";

            // List all log entries
            Page<LogEntry> entries = logging.listLogEntries(EntryListOption.filter(logFilter));
            while (entries != null) {
                for (LogEntry logEntry : entries.iterateAll()) {
                    Payload.StringPayload payload = logEntry.getPayload();
                    if (payload != null && payload.getData() != null && payload.getData() != null) {
                        log.debug(payload.getData());
                        logEntries.append(payload.getData() + "\n");
                    }
                }
                entries = entries.getNextPage();
            }
        } catch (Exception e) {
            log.error("error {}", e.getMessage(), e);
            throw new RuntimeException("Failed to read error log entries for custom synapse " + cloudFunctionName, e);
        }
        return logEntries.toString();
    }

    public String getRecentDeploymentError(String cloudFunctionName, long since) {
        String logName = "cloudaudit.googleapis.com%2Factivity";

        String recentDeploymentError = "";

        try (Logging logging = LoggingOptions.newBuilder().setCredentials(getCredentials()).build().getService()) {

            /*
            Calendar calendar = Calendar.getInstance(TimeZone.getTimeZone("UTC"));
            calendar.add(Calendar.HOUR, -3);
             */
            DateFormat rfc3339 = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSS'Z'");
            String logFilter =
                    "logName=projects/" + logging.getOptions().getProjectId() + "/logs/" + logName
                            + " AND resource.type = \"cloud_function\" "
                            + " AND resource.labels.function_name = \"" + cloudFunctionName + "\" "
                            + " AND severity>=DEFAULT "
                            + " AND timestamp>=\"" + rfc3339.format(since) + "\"";

            // List all log entries
            Page<LogEntry> entries = logging.listLogEntries(EntryListOption.filter(logFilter));
            while (entries != null) {
                for (LogEntry logEntry : entries.iterateAll()) {
                    Payload.ProtoPayload payload = logEntry.getPayload();
                    if (payload != null && payload.getData() != null && payload.getData().getValue() != null) {
                        recentDeploymentError = payload.getData().getValue().toStringUtf8();
                    }
                }
                entries = entries.getNextPage();
            }
        } catch (Exception e) {
            log.error("error {}", e.getMessage(), e);
        }
        // Strip off internal cloud function details.
        if (StringUtils.isNotEmpty(recentDeploymentError) && recentDeploymentError.contains("Error ID:")) {
            recentDeploymentError = recentDeploymentError.substring(0, recentDeploymentError.indexOf("Error ID:"));
        }
        log.info("Recent cloud function '{}' deployment error: {}", cloudFunctionName, recentDeploymentError);
        return recentDeploymentError;
    }

    private GoogleCredentials getCredentials() {
        try {
            return GoogleCredentials
                    .fromStream(new ByteArrayInputStream(Base64.getDecoder().decode(appConfig.getCfDeployerCredentialsKey())));
        } catch (IOException e) {
            throw new RuntimeException("Google credentials creation failed - " + e.getMessage());
        }
    }
}
