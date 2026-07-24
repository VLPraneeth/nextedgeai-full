package com.syncari.connector.intercom;

import com.syncari.connector.data.AttributeSchema;
import com.syncari.connector.data.EntitySchema;
import org.apache.commons.lang3.StringUtils;

import java.util.Map;

public class IntercomSeed {

    public static EntitySchema getSeedEntitySchema(String entityName) {
        switch (entityName) {
            case IntercomService.TICKET:
                return getTicketEntitySchema();
            default:
                throw new RuntimeException(String.format("Entity %s is not seeded", entityName));
        }
    }

    private static EntitySchema getTicketEntitySchema() {
        EntitySchema ticket = new EntitySchema(IntercomService.TICKET.toLowerCase(), StringUtils.capitalize(IntercomService.TICKET));
        ticket.addField(new AttributeSchema("id", "string").setDisplayName("Id")
                .setIdField(true).setUpdateable(false).setSystem(true).setUnique(true).setNillable(false));
        ticket.addField(new AttributeSchema("ticket_id", "string").setDisplayName("Ticket ID")
                .setUpdateable(false));
        ticket.addField(new AttributeSchema("ticket_type", "object").setDisplayName("Ticket Type"));
        ticket.addField(new AttributeSchema("ticket_type_id", "string").setDisplayName("Ticket Type Id"));
        ticket.addField(new AttributeSchema("ticket_state", "string").setDisplayName("Ticket State"));
        ticket.addField(new AttributeSchema("category", "string").setDisplayName("Category"));
        ticket.addField(new AttributeSchema("is_open", "boolean").setDisplayName("Is Open"));
        ticket.addField(new AttributeSchema("ticket_attributes", "object").setDisplayName("Ticket Attributes"));
        ticket.addField(new AttributeSchema("contacts", "object").setDisplayName("Contacts")
                .setMultiValueField(true));
        ticket.addField(new AttributeSchema("admin_assignee_id", "reference").setDisplayName("Admin Assignee")
                .setReferenceTo("admin"));
        ticket.addField(new AttributeSchema("team_id", "reference").setDisplayName("Team")
                .setReferenceTo("team"));
        ticket.addField(new AttributeSchema("created_at", "datetime").setDisplayName("Created At")
                .setCreatedAtField(true).setUpdateable(false).setSystem(true).setNillable(false));
        ticket.addField(new AttributeSchema("updated_at", "datetime").setDisplayName("Updated At")
                .setWatermarkField(true).setUpdateable(false).setSystem(true).setNillable(false));
        return ticket;
    }
}
