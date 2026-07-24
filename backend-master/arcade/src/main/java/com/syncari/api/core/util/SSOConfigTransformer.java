package com.syncari.api.core.util;

import com.syncari.api.rest.controllers.data.SSOAuthConfigDTO;
import com.syncari.core.model.SSOAuthConfig;
import com.syncari.core.model.SSOAuthProvider;
import com.syncari.core.service.EncryptionService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

@Component
public class SSOConfigTransformer {

    @Autowired
    EncryptionService encryptionService;

    public SSOAuthConfig toSSOAuthConfig(SSOAuthConfigDTO dto){
        return new SSOAuthConfig().setProvider(SSOAuthProvider.valueOf(dto.getProvider()))
                .setEntityId(dto.getEntityId()).setSsoUrl(dto.getSsoUrl()).setX509Key(dto.getCertificate());
    }

    public SSOAuthConfigDTO toSSOAuthConfigDTO(SSOAuthConfig config){
        if(config == null){
            return null;
        }
        return new SSOAuthConfigDTO().setProvider(config.getProvider().name()).setEntityId(config.getEntityId())
                .setSsoUrl(config.getSsoUrl()).setCertificate(encryptionService.decrypt(config.getX509Key()));
    }
}
