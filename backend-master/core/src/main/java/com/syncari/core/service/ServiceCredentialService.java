package com.syncari.core.service;

import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import org.apache.commons.lang3.StringUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import com.syncari.core.exception.NotFoundException;
import com.syncari.core.exceptions.SyncariValidationException;
import com.syncari.core.functions.FunctionConstants;
import com.syncari.core.model.MappingGraph;
import com.syncari.core.model.MappingNode;
import com.syncari.core.model.ServiceCredential;
import com.syncari.core.model.misc.ServiceCredentialType;
import com.syncari.core.model.misc.ServiceType;
import com.syncari.core.repositories.customer.ServiceCredentialRepo;
import static com.syncari.utils.I18n.i18n;

import lombok.extern.slf4j.Slf4j;

@Slf4j
@Service
public class ServiceCredentialService {
	@Autowired
	ServiceCredentialRepo credRepo;
	@Autowired
	EncryptionService encryptionService;
	@Autowired
	SchemaService schemaService;
	@Autowired
    MappingGraphService graphService;
  
  public void validate(ServiceCredential credential) {
    if (StringUtils.isBlank(credential.name)) {
        throw new SyncariValidationException(i18n("missing_credential_name"));
    }
  
    if (StringUtils.isBlank(credential.apiKey)) {
        throw new SyncariValidationException(i18n("missing_clearbit_api_key"));
    }
  }  
	
	public ServiceCredential addServiceCredential(ServiceCredential credential) {
    validate(credential);
  
    if ((credential.serviceType == ServiceType.Clearbit) || (credential.serviceType == ServiceType.Salesintel)){
        credential.setCredentialType(ServiceCredentialType.ENRICH);
    }
    credential.setApiKey(encryptionService.encrypt(credential.apiKey));
    return credRepo.save(credential);
  }
	
	public List<ServiceCredential> getCredentials() {
	    List<ServiceCredential> all = credRepo.findAll();
	    all.stream().forEach(c -> {
	        if(!StringUtils.isBlank(c.apiKey)) {
	            c.setApiKey(encryptionService.decrypt(c.getApiKey()));
	        }
	    });
	    return all;
	}
	
	public Optional<ServiceCredential> getCredentials(String serviceId) {
	    Optional<ServiceCredential> byId = credRepo.findById(serviceId);
	    byId.ifPresent(s -> s.setApiKey(encryptionService.decrypt(s.apiKey)));
	    return byId;
	}
	
	public List<ServiceCredential> getCredentials(ServiceCredentialType type) {
	    List<ServiceCredential> all = getCredentials();
        return all.stream().filter(m -> m.getCredentialType() == type).collect(Collectors.toList());
	}

    public void canDelete(String serviceId) {
        // for each syncari entity which has a draft/approved, check if there is an enrich function with the serviceId
        List<MappingGraph> allGraphs = graphService.retrieveEntityGraphs();
        doCheck(serviceId, null, allGraphs);
        allGraphs.forEach(g -> {
            List<MappingGraph> approvedAttrGraphs = graphService.retrieveApprovedAttributeGraphs(g.getId());
            doCheck(serviceId, g, approvedAttrGraphs);
            List<MappingGraph> draftAttrGraphs = graphService.retrieveDraftAttributeGraphs(g.getId());
            doCheck(serviceId, g, draftAttrGraphs);
        });
    }

    private void doCheck(String serviceId, MappingGraph entityGraph, List<MappingGraph> attrGraphs) {
        attrGraphs.forEach(a -> {
            Stream<MappingNode> functions = a.getFunctions();
            functions.forEach(f -> {
                if(FunctionConstants.ENRICH_COMPANY.equalsIgnoreCase(f.getApiName()) || FunctionConstants.ENRICH_PERSON.equalsIgnoreCase(f.getApiName())) {
                    var id = f.getConfiguration().getConfigMap().getOrDefault("serviceId", "");
                    if(id.equals(serviceId)) {
                        String name = entityGraph == null ? a.getName() : entityGraph.getName()+" "+a.getName();
                        throw new SyncariValidationException(String.format(i18n("cred_cannot_delete"), name));  
                    }
                }
            });
        });
    }

    public void delete(String serviceId) {
				canDelete(serviceId);
				
				ServiceCredential credential = credRepo.findById(serviceId).orElseThrow(() -> new NotFoundException(ServiceCredential.class, "Id", serviceId));

				credRepo.delete(credential);
				log.info(String.format("Credential %s deleted successfully", credential.getName()));
		}
}
