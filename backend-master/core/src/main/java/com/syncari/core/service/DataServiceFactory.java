package com.syncari.core.service;

import com.syncari.connector.Constants;
import com.syncari.connector.service.def.*;
import com.syncari.core.model.ConnectorMetadata;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.ApplicationContext;
import org.springframework.stereotype.Component;

@Component
public class DataServiceFactory {
	private final ApplicationContext context;

	@Autowired
	public DataServiceFactory(ApplicationContext context) {
		this.context = context;
	}

    private Object getClazz(ConnectorMetadata metadata) {
        if (metadata.isCustom()) {
            return context.getBean(Constants.CUSTOM);
        } else if(metadata.isHttpSource()) {
        	return context.getBean(Constants.HTTP_SOURCES);
        } else if(metadata.isWebhook()) {
          return context.getBean(Constants.WEBHOOK_RECEIVER);
        }
        return context.getBean(metadata.getName());
	}

	public DataService getDataService(ConnectorMetadata metadata) {
		Object clazz = getClazz(metadata);
		if (clazz == null || !DataService.class.isAssignableFrom(clazz.getClass())) {
			throw new RuntimeException(String.format("%s does not implement DataService interface",
					metadata.getName()));
		}
		return (DataService) clazz;
	}

	public FileService getFileService(ConnectorMetadata metadata) {
		Object clazz = getClazz(metadata);
		if (clazz == null || !FileService.class.isAssignableFrom(clazz.getClass())) {
			throw new RuntimeException(String.format("%s does not implement FileService interface",
					metadata.getName()));
		}
		return (FileService) clazz;
	}

	public WebhookService getWebhookService(ConnectorMetadata metadata) {
		Object clazz = getClazz(metadata);
		if (clazz == null || !WebhookService.class.isAssignableFrom(clazz.getClass())) {
			throw new RuntimeException(String.format("%s does not implement DataService interface",
					metadata.getName()));
		}
		return (WebhookService) clazz;
	}

	public boolean isWebhookService(ConnectorMetadata metadata) {
		Object clazz = getClazz(metadata);
		if (clazz == null || !WebhookService.class.isAssignableFrom(clazz.getClass())) {
			return false;
		}
		return true;
	}

	public MetadataService getSchemaService(ConnectorMetadata metadata) {
	    Object clazz = getClazz(metadata);
		if (clazz == null || !MetadataService.class.isAssignableFrom(clazz.getClass())) {
			throw new RuntimeException(String.format("%s does not implement MetadataService interface",
					metadata.getName()));
		}
		return (MetadataService) clazz;
	}

	public AuthenticationService getAuthenticationService(ConnectorMetadata metadata) {
	    Object clazz = getClazz(metadata);
        if (clazz == null || !AuthenticationService.class.isAssignableFrom(clazz.getClass())) {
            throw new RuntimeException(String.format("%s does not implement AuthenticationService interface",
                    metadata.getName()));
        }
        return (AuthenticationService) clazz;
	}
	
	public OauthAuthenticationService getOauthAuthenticationService(ConnectorMetadata metadata) {
	    Object clazz = getClazz(metadata);
	    if (clazz == null || !OauthAuthenticationService.class.isAssignableFrom(clazz.getClass())) {
            throw new RuntimeException(String.format("%s does not implement OauthAuthenticationService interface",
                    metadata.getName()));
        }
        return (OauthAuthenticationService) clazz;
	}

	public boolean isSynapseService(ConnectorMetadata metadata) {
		try {
			Object clazz = getClazz(metadata);
			if (clazz == null || !SynapseInfoService.class.isAssignableFrom(clazz.getClass())) {
				return false;
			}
			return true;
		} catch (Exception e) {
			return false;
		}
	}

	public boolean isFileService(ConnectorMetadata metadata) {
		try {
			Object clazz = getClazz(metadata);
			return FileService.class.isInstance(clazz);
		} catch (Exception e) {
			return false;
		}
	}

	public SynapseInfoService getSynapseService(ConnectorMetadata metadata) {
		Object clazz = getClazz(metadata);
		if (clazz == null || !SynapseInfoService.class.isAssignableFrom(clazz.getClass())) {
			throw new RuntimeException(String.format("%s does not implement SynapseInfoService interface",
					metadata.getName()));
		}
		return (SynapseInfoService) clazz;
	}

	public LookupService getLookupService(ConnectorMetadata metadata) {
		Object clazz = getClazz(metadata);
		if (clazz == null || !LookupService.class.isAssignableFrom(clazz.getClass())) {
			throw new RuntimeException(String.format("%s does not implement LookupService interface",
					metadata.getName()));
		}
		return (LookupService) clazz;
	}

	public boolean isRestClientService(ConnectorMetadata metadata) {
		Object clazz = getClazz(metadata);
		if (clazz == null || !RestClientService.class.isAssignableFrom(clazz.getClass())) {
			return false;
		}
		return true;
	}

	public RestClientService getRestClientService(ConnectorMetadata metadata) {
		Object clazz = getClazz(metadata);
		if (clazz == null || !RestClientService.class.isAssignableFrom(clazz.getClass())) {
			throw new RuntimeException(String.format("%s does not implement OauthAuthenticationService interface",
					metadata.getName()));
		}
		return (RestClientService) clazz;
	}

}
