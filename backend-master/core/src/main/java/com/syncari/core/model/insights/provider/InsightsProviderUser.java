package com.syncari.core.model.insights.provider;

import lombok.Data;
import lombok.experimental.Accessors;

import java.util.List;

@Data
@Accessors(chain = true)
public class InsightsProviderUser {

    private String user_identifier;
    private String name;
    private String display_name;
    private String email;
    private String password;
    private String account_type="LOCAL_USER";
    private String account_status="ACTIVE";
    private String visibility="SHARABLE";
    private boolean notify_on_share=false;
    private boolean show_onboarding_experience=false;
    private boolean onboarding_experience_completed=true;
    private String operation;
    private String trigger_welcome_email;
    private String trigger_activation_email;
    List<String> org_identifiers;
    List<String> group_identifiers;
}
