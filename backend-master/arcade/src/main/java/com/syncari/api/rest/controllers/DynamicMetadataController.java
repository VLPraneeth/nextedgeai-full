package com.syncari.api.rest.controllers;

import com.syncari.api.rest.controllers.data.ComponentDataRequest;
import com.syncari.core.exceptions.SyncariValidationException;
import com.syncari.core.quickstart.QuickStartFactory;
import com.syncari.core.quickstart.QuickStartService;
import com.syncari.utils.KeyValue;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.access.annotation.Secured;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;

import static com.syncari.core.security.Permissions.READ_STUDIO;

@Slf4j
@RestController
@RequestMapping(value = "/api/v1/metadata")
public class DynamicMetadataController {

    @Autowired
    QuickStartFactory qsFactory;

    @Secured(READ_STUDIO)
    @RequestMapping(method = RequestMethod.POST, value = "/values")
    public List<KeyValue> getData(@RequestBody ComponentDataRequest request) {
        switch (request.getComponentType()){
            case "quickstart":
                QuickStartService qsService = qsFactory.getQuickStartServiceByName(request.getComponentName());
                return qsService.getData(request.getConfigName(), request.getConfigType(), request.getInputs());

            default:
                throw new SyncariValidationException(String.format("ComponentType %s is not supported", request.getComponentType()));

        }
    }

}
