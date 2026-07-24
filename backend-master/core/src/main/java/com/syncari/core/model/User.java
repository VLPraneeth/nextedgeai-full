package com.syncari.core.model;

import com.syncari.core.SyncariContext;
import com.syncari.core.model.misc.UserLoginDetails;
import com.syncari.core.model.misc.UserOAuthDetails;
import com.syncari.core.model.util.Status;
import com.syncari.core.utils.PasswordConstraintValidator;
import com.syncari.utils.I18n;
import lombok.Data;
import lombok.experimental.Accessors;
import org.apache.commons.lang3.StringUtils;
import org.springframework.data.mongodb.core.mapping.Document;

import javax.validation.constraints.Email;
import javax.validation.constraints.NotNull;
import java.time.Duration;
import java.time.Instant;
import java.util.*;

import static com.syncari.core.utils.ValidationUtils.validateCondition;

@Data
@Document
@Accessors(chain = true)
public class User extends UUIDAuditModel {

    public static final String SYSTEM_USER_PREFIX = "systemuser@nextedge.ai";
    public static final String DEFAULT_SUPER_ADMIN_EMAIL = System.getenv().getOrDefault("NEXTEDGE_ADMIN_EMAIL", "admin@nextedge.ai");
    private static final long PASSWORD_VALIDITY_DURATION = Duration.ofDays(90).getSeconds() * 1000;
    @Email
    @NotNull(message = "User email is required")
    private String email;
    private String password;
    @NotNull(message = "User status is required")
    private Status status;
    private String firstName;
    private String lastName;
    private boolean isAdmin;
    private boolean isSuperAdmin;
    private boolean isGhostUser;
    private boolean syncariDev;
    private boolean systemUser;
    private String photoLocation;
    private String timeZone;
    private Instant lastLoggedIn;
    private Instant lastPasswordResetTimestamp;
    private String currentInstanceId;
    private Set<String> availableInstances = new HashSet<>();
    private String orgId;
    private List<UserLoginDetails> userLoginDetails = new ArrayList<>();
    private int failedLoginAttempts;
    private boolean restrictedFromLogin;

    // fields used for public apis oauth
    private boolean isApiUser;
    private String clientId;
    private String clientSecret;
    private String refreshToken;
    private String insightsProviderUserId;
    private String insightsProviderUserName;
    //the list of oauth details for the services that
    // require the user to authenticate against NextEdge AI (we act as the OAuth server/IDP),

    private List<UserOAuthDetails> oauthServices = new ArrayList<>();

    public User(String email, String password, Status status, String currentInstanceId) {
        this.email = email;
        this.password = password;
        this.currentInstanceId = currentInstanceId;
        this.status = status;
    }

    public User(String email, String password, String currentInstanceId) {
        this(email, password,null,currentInstanceId);
    }

    /**
     * Autogenerates a random password
     * @param email
     * @param currentInstanceId
     */
    public User(String email, String currentInstanceId) {
        this(email, generatePassword(),null,currentInstanceId);
    }

    public User() {
    }

    public void addAvailableInstance(Instance instance) {
        availableInstances.add(instance.getNextEdgeId());
    }

    public void addAvailableInstance(String instanceId) {
        availableInstances.add(instanceId);
    }

    public void removeAvailableInstance(String instanceId) {
        availableInstances.remove(instanceId);
    }
    
    public boolean hasAccess(String nextEdgeId) {
        return availableInstances.contains(nextEdgeId);
    }

    @Override
    public String toString() {
        return "User{" + "id='" + id + '\'' + ", firstName='" + firstName + '\'' + ", lastName='" + lastName + '\''
                + ", email='" + email + '\'' + ", password='*****'" + ", timeZone='" + timeZone + '\'' + ", status="
                + status + ", userLoginDetails" + userLoginDetails +'}';
    }

    public boolean canInvite() {
        return status == Status.PENDING;
    }

    public boolean isActive() {
        return status == Status.ACTIVE;
    }

    public void setStatus(String status) {
        this.status = Status.valueOf(status);
    }

    public void setStatus(Status status) {
        this.status = status;
    }

    public String getName() {
        if (StringUtils.isBlank(firstName) && StringUtils.isBlank(lastName))
            return email;
        return (StringUtils.isBlank(firstName) ? "" : firstName) + " "
                + (StringUtils.isBlank(lastName) ? "" : lastName);
    }

    public static String generatePassword() {
        //atleast one upper case,
        return Character.toString('A'+new Random().nextInt(26))
                + UUID.randomUUID().toString()
                //one number
                + new Random().nextInt(1000)
                //one lowercase
                + Character.toString('a'+new Random().nextInt(26));
    }

    public void validatePassword(String newPassword) {
        // exclude password validation for system user, api user and default admin
        if(isExcludedFromPasswordValidation()){
            return;
        }
        validateCondition(StringUtils.isBlank(newPassword), I18n.i18n("empty_user_password"));
        PasswordConstraintValidator.validatePassword(newPassword);
    }

    public Optional<UserLoginDetails> findLoginDetails(String tokenId) {
        return this.userLoginDetails.stream().filter(details -> tokenId.equals(details.getTokenId())).findFirst();
    }

    public boolean removeLoginDetails(UserLoginDetails loginDetails){
        return this.userLoginDetails.removeIf(dbLoginDetails -> dbLoginDetails.getTokenId().equals(loginDetails.getTokenId()));
    }
    
    //Important: Call this if current instance logged in is same which is trying to be deleted
    public boolean removeAllLoginDetails(){
        return this.userLoginDetails.removeAll(getUserLoginDetails());
    }

    public void addUserlogindetail(UserLoginDetails userLoginDetails) {
        this.getUserLoginDetails().add(userLoginDetails);
    }

    public boolean hasPasswordExpired(){
        if(SyncariContext.getOrganziation() != null && SyncariContext.getOrganziation().isSSOEnabled()) return false;
        if(lastPasswordResetTimestamp == null) return false;
        if(isExcludedFromPasswordValidation()) return false;
        return Instant.now().toEpochMilli() - lastPasswordResetTimestamp.toEpochMilli() > PASSWORD_VALIDITY_DURATION;
    }

    public boolean isExcludedFromPasswordValidation(){
        // exclude password validation for system user, API user and the NextEdge AI bootstrap admin
        return isSystemUser() || isApiUser() || getEmail().equalsIgnoreCase(DEFAULT_SUPER_ADMIN_EMAIL);
    }
    
    public boolean isAccountLocked() {
    	return failedLoginAttempts >= 5;
    }

    public Optional<UserOAuthDetails> getOauthDetails(String code) {
        return oauthServices.stream()
                .filter(o -> code.equals(o.getAuthorizationCode()))
                .findFirst();
    }

    public void setOauthDetails(UserOAuthDetails oauthDetails) {
        //remove existing
        final Iterator<UserOAuthDetails> iterator = oauthServices.iterator();
        while (iterator.hasNext()) {
            final UserOAuthDetails next = iterator.next();
            if (oauthDetails.getAuthorizationCode().equals(next.getAuthorizationCode())) {
                iterator.remove();
                break;
            }
        }
        //add incoming
        oauthServices.add(oauthDetails);
    }
}
