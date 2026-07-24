package com.syncari.core.service;

import static java.lang.String.format;

import java.util.Date;

import org.apache.commons.lang3.StringUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.syncari.core.SyncariContext;
import com.syncari.core.event.Message;
import com.syncari.core.event.Publisher;
import com.syncari.core.model.Event;

import lombok.extern.slf4j.Slf4j;

@Slf4j
@Component
public class EventService {
    @Autowired
    Publisher queue;

    public void log(String type, String subType, String component, String... details) {
        log(Event.from(type, subType, component,details));
    }

    public void log(Event event) {
        if (event == null || StringUtils.isBlank(event.getType())) {
        	throw new RuntimeException("Event type cannot be null");
        }
        event.setLoggedTime(new Date());
        try {
            Message message = new Message(SyncariContext.getInstance().getSyncariId(), event);
            ObjectMapper mapper = new ObjectMapper();
            String eventString = mapper.writeValueAsString(message);
            log.info(String.format("Sending Message: %s", eventString));
            queue.publishToEventLog(eventString);
        } catch (Exception e) {
            log.error(format("Error while logging %s", e.getMessage()));
            throw new RuntimeException(e);
        }
    }



}
