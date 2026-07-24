package com.syncari.core.model.insights.provider.ts;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.experimental.Accessors;

import java.util.List;

@Data
@AllArgsConstructor
@Accessors(chain = true)
public class TSUserResponse {

    private String id;
    private String name;
    private String display_name;
    private String author_id;
    private String visibility;
    private boolean can_change_password;
    private boolean complete_detail;
    private Double creation_time_in_millis;
    private Org current_org;
    private List<Org> orgs;
    private boolean deleted;
    private boolean deprecated;
    private String account_type;
    private String account_status;
    private String email;
    private Double expiration_time_in_millis;
    private boolean external;
    private boolean welcome_email_sent;

}
