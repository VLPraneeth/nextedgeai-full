package com.syncari.core.model;

import java.util.Map;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@Data
@AllArgsConstructor
@NoArgsConstructor
@Builder
public class ErrorNotification extends UUIDAuditModel {
	@Deprecated
    private ErrorPriority priority;
	@Deprecated
    private ErrorCategory category;
    private String catalogId;
    private String key;
    private String componentId;
    private String subject;
    private String body;
    private Map<String, String> details;
    
    @Deprecated
    public void setPriority(ErrorPriority priority) {
        this.priority = priority;
    }
    
    @Deprecated
    public void setPriority(String priority) {
    	try {
            this.priority = ErrorPriority.valueOf(priority);
        } catch (Exception e) {
            log.error("Unknown error priority {}", priority);
        }
    }
    
    @Deprecated
    public void setCategory(ErrorCategory category) {
        this.category = category;
    }
    
    @Deprecated
    public void setCategory(String category) {
    	try {
            this.category = ErrorCategory.valueOf(category);
        } catch (Exception e) {
            log.error("Unknown error category {}", category);
        }
    }

}
