package com.syncari.connector.data;

import lombok.Data;
import lombok.experimental.Accessors;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;


@Data
@Accessors(chain = true)
public class BatchJob {
    String connectorId;
    String externalEntityName;
    String jobId;
    String internalId;
    Map<String, Object> jobDetails=Map.of();
    BatchJobStatus status = BatchJobStatus.NEW;
    String contentType;
    List<String> downloadedFielURLs = new ArrayList<>();

    public Object getJobDetail(String key){
        return jobDetails.get(key);
    }
    public String getJobDetailString(String key){
        return jobDetails.get(key)==null?null : jobDetails.get(key).toString();
    }

    public boolean isError(){
        return status == BatchJobStatus.ERROR;
    }
    public boolean isCompleted(){
        return status == BatchJobStatus.COMPLETED;
    }
    public boolean isPending(){
        return status == BatchJobStatus.PENDING;
    }

    public boolean hasContent(){
        return  downloadedFielURLs!=null && !downloadedFielURLs.isEmpty();
    }
    public Optional<String> getDownloadedFileURL(int index){
        if(downloadedFielURLs==null || downloadedFielURLs.isEmpty() || index >=downloadedFielURLs.size()){
            return Optional.empty();
        }
        return Optional.ofNullable(downloadedFielURLs.get(index));
    }

}

