package com.syncari.api.rest.controllers;

import com.syncari.api.core.util.Util;
import com.syncari.api.rest.config.security.SecurityConstants;
import com.syncari.api.rest.controllers.data.FeedbackRequest;
import com.syncari.api.rest.controllers.data.GhostAccessRequest;
import com.syncari.core.SyncariContext;
import com.syncari.core.SyncariContextHandler;
import com.syncari.core.config.AppConfig;
import com.syncari.core.event.EventTypes;
import com.syncari.core.exceptions.SyncariValidationException;
import com.syncari.core.model.*;
import com.syncari.core.model.misc.OrganizationType;
import com.syncari.core.model.security.OAuthConfig;
import com.syncari.core.model.util.Status;
import com.syncari.core.repositories.syncari.GhostAccessAuditRepo;
import com.syncari.core.security.Permissions;
import com.syncari.core.service.*;
import com.syncari.core.service.authz.AuthzService;
import com.syncari.utils.KeyValue;
import lombok.Data;
import lombok.experimental.Accessors;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.security.access.annotation.Secured;
import org.springframework.web.bind.annotation.*;

import javax.servlet.http.HttpServletResponse;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import static com.syncari.core.security.Permissions.*;
import static com.syncari.utils.I18n.i18n;
import static java.lang.String.format;

@Slf4j
@RestController
@RequestMapping(value = "/api/v1/specter")
public class SpecterController {

    private static final String TO_SYNCARI_ID = "toSyncariId";

	private static final String FROM_SYNCARI_ID = "fromSyncariId";
	
	private static final Map<String, Long> validDuration = Map.of("8 hours", 8L,  "1 day", 24L, "2 days", 48L, "30 days", 720L);

	@Autowired
    AppConfig config;

    @Autowired
    SubscriptionService subscriptionService;
    
    @Autowired
    SpecterService specterService;

    @Autowired
    private SyncariContextHandler synCtxHandler;

    @Autowired
    private Util util;

    @Autowired
    @Qualifier("defaultEmailService")
    EmailService emailService;

    @Autowired
    UserService userService;

    @Autowired
    InstanceConfigurationService instanceConfigurationService;
    
    @Autowired
    AsyncJobService jobService;
	@Autowired
	AuthzService authzService;
	@Autowired
	GhostAccessAuditRepo ghostAccessAuditRepo;

	@Secured(PROVISION_ORG)
	@RequestMapping(method = RequestMethod.POST, value = "/instance/copy/{fromSyncariId}/{toSyncariId}")
	public String copyInstance(@PathVariable String fromSyncariId, @PathVariable String toSyncariId, @RequestBody CopyRequest request) {
		try {
			if(StringUtils.isBlank(fromSyncariId)) {
				throw new SyncariValidationException("From syncariId is required");
			}
			if(StringUtils.isBlank(toSyncariId)) {
				throw new SyncariValidationException("To syncariId is required");
			}
			List<AsyncJob> existing = jobService.findByTypeAndStatus(EventTypes.COPY_INSTANCE, List.of(Status.NEW.name(), Status.PROCESSING.name()));
			existing.forEach(j -> {
				if (j.getEvent() != null && fromSyncariId.equalsIgnoreCase(getValue(j.getEvent(), FROM_SYNCARI_ID))
						&& toSyncariId.equalsIgnoreCase(getValue(j.getEvent(), TO_SYNCARI_ID))) {
					throw new SyncariValidationException(
							"Copy from " + fromSyncariId + " to " + toSyncariId + " already running");
				}
			});
			subscriptionService.getInstance(fromSyncariId);
			subscriptionService.getInstance(toSyncariId);
			Event event = new Event().setType(EventTypes.COPY_INSTANCE)
					.setDetails(Map.of(FROM_SYNCARI_ID, fromSyncariId, TO_SYNCARI_ID, toSyncariId, "request", request));
			AsyncJob job = new AsyncJob().setStartTime(Instant.now()).setEvent(event).setType(EventTypes.COPY_INSTANCE)
					.setStatus(Status.NEW);
			job = jobService.save(job);
			return "Copy from " + fromSyncariId + " to " + toSyncariId + " initiated successfully!";
		} catch (Exception e) {
			return "Error: " + e.getMessage();
		}
	}
    
    
    @Secured(PROVISION_ORG)
    @RequestMapping(method = RequestMethod.GET, value = "/instance/copy/{fromSyncariId}/{toSyncariId}")
	public List<AsyncJob> instanceCopyStatus(@PathVariable String fromSyncariId, @PathVariable String toSyncariId) {
		return jobService.findByType(EventTypes.COPY_INSTANCE).stream()
				.filter(j -> (contains(j, FROM_SYNCARI_ID, fromSyncariId) && contains(j, TO_SYNCARI_ID, toSyncariId)))
				.collect(Collectors.toList());
	}
    
    private boolean contains(AsyncJob job, String key, String value) {
    	return job.getEvent().getDetails() != null && job.getEvent().getDetails().containsKey(key) && value.equalsIgnoreCase(job.getEvent().getDetails().get(key).toString());
    }

    @Secured(GHOST_LOGIN)
    @RequestMapping(method = RequestMethod.GET, value = "/switch/instance/{syncariId}")
    public void ghostLogin(@PathVariable String syncariId, @RequestHeader(SecurityConstants.TOKEN_HEADER) String previousToken, HttpServletResponse response) {

        if(syncariId == null || syncariId.isEmpty() || syncariId.isBlank()){
            throw new RuntimeException("Please provide a valid subscription name");
        }

        User user = SyncariContext.getUser();
        log.info(String.format("Switching User: %s to Instance: %s", user.getEmail(), syncariId));

        synCtxHandler.setContext(syncariId);

		List<String> perms = user.isSuperAdmin() ? Permissions.allPermissions()
				: new ArrayList<>(userService.getUserPermissionsForInstance(user.getId(),
						subscriptionService.getInstance(syncariId)));
		perms.addAll(Permissions.ghostPermissions());
        // create new JWT token and set in response header
        String token = util.parseJWTTokenAndUpdateUserWithNewLoginDetails(previousToken.replace("Bearer ", ""),
                user, perms,true);
		util.setInsightsProviderContext(SyncariContext.getInstance());
		response.addHeader(SecurityConstants.TOKEN_HEADER, SecurityConstants.TOKEN_PREFIX + token);
    }

    @RequestMapping(method = RequestMethod.POST, value = "/feedback")
    public void feedback(@RequestBody FeedbackRequest feedback) {
        String body = String.format(
                "Hello Syncaroos,\n\n" + "You have a feedback from User: %s, Instance: %s, SyncariId: %s . \n\n"
                        + "Details: %s",
                SyncariContext.getUser().getEmail(), SyncariContext.getInstance().getName(),
                SyncariContext.getInstance().getSyncariId(), feedback.getFeedback());
        emailService.sendText(List.of("support@syncari.com"), "Customer feedback: "+feedback.getReason(), body);
    }
    
	@Secured(PROVISION_ORG)
	@RequestMapping(method = RequestMethod.POST, value = "/setOauthConfig/{fromSyncariId}/{toSyncariId}")
	public String updateSynapseOauthConfig(@PathVariable String fromSyncariId, @PathVariable String toSyncariId) {
    	if(StringUtils.isBlank(fromSyncariId) ||
    			StringUtils.isBlank(toSyncariId)) {
    		throw new SyncariValidationException("Incomplete request");
    	}
    	String success = String.format("Successfully set oauth config for %s", toSyncariId);
    	com.syncari.core.model.Organization fromOrg = subscriptionService.getOrgBySyncariId(fromSyncariId);
    	com.syncari.core.model.Organization to = subscriptionService.getOrgBySyncariId(toSyncariId);
    	if(fromOrg.getId().equalsIgnoreCase(to.getId())) {
    		log.info("Oauth config already set for {}", toSyncariId);
    		return success;
    	}
    	OAuthConfig authConfig = fromOrg.getOauthConfigs().get(com.syncari.connector.Constants.HUBSPOT);
    	if(authConfig == null) {
    		throw new SyncariValidationException("OAuthConfig not set on source");
    	}
    	to.setOrgType(OrganizationType.partner);
    	to.setOauthConfigs(Map.of(com.syncari.connector.Constants.HUBSPOT, authConfig));
    	subscriptionService.updateOrg(to);
    	log.info("Successfully set oauth config for {}", toSyncariId);
    	return success;
    }
	
	@Secured(LIST_ORG)
	@RequestMapping(method = RequestMethod.POST, value = "/ghost")
	public KeyValue requestAccess(@RequestBody GhostAccessRequest request) {
		// all mandatory field check is enforced by model
		Role role = authzService.getRole(request.getRoleId()).get();
		User systemUser = userService.getSystemUser();
		Organization organization = subscriptionService.getOrgBySyncariId(request.getSyncariId());
		User grantingUser = SyncariContext.getUser();

		// Determine the target user for ghost access
		User targetUser;
		if (StringUtils.isNotBlank(request.getUserId())) {
			// Grant access to specified user
			targetUser = userService.getUserById(request.getUserId());
		} else {
			// Fall back to current user for backward compatibility
			targetUser = SyncariContext.getUser();
		}

		// Get the specific instance requested
		Instance instance = organization.getInstance(request.getSyncariId())
				.orElseThrow(() -> new SyncariValidationException("Instance " + request.getSyncariId() + " not found"));

		Instant expiry = Instant.now();
		expiry = expiry.plus(validDuration.getOrDefault(request.getDuration(), 8L), ChronoUnit.HOURS);
		GhostAccessAudit req = new GhostAccessAudit().setRequestedAt(Instant.now()).setReason(request.getReason())
				.setAccessDetails(request.getAccessDetails())
				.setRequesterId(targetUser.getId()).setRequesterEmail(targetUser.getEmail())
				.setRoleName(role.getName()).setSyncariId(instance.getSyncariId()).setApproverId(systemUser.getId())
				.setApproverEmail(systemUser.getEmail()).setStatus(Status.ACTIVE).setExpireAt(expiry)
				.setAuditTrail(new StringBuilder()
						.append(format(i18n("ghost_user_provided"), "API",
								Instant.now())).toString());
		subscriptionService.grantAccess(req, organization);

		// Send email notification to the user who was granted access
		try {
			String emailSubject = "Ghost Access Granted - " + instance.getSyncariId();
			String emailBody = String.format(
					"Hello %s,\n\n" +
					"You have been granted temporary ghost access with the following details:\n\n" +
					"Instance: %s\n" +
					"Role: %s\n" +
					"Duration: %s\n" +
					"Expires At: %s\n" +
					"Reason: %s\n" +
					"Access Details: %s\n" +
					"Granted By: %s\n\n" +
					"This access will automatically expire at the time specified above.\n\n" +
					"Best regards,\n" +
					"Syncari Team",
					targetUser.getFirstName() + " " + targetUser.getLastName(),
					instance.getSyncariId(),
					role.getName(),
					request.getDuration(),
					expiry.toString(),
					request.getReason(),
					request.getAccessDetails(),
					grantingUser.getEmail()
			);
			emailService.sendText(List.of(targetUser.getEmail()), emailSubject, emailBody);
			log.info("Ghost access email sent to: {}", targetUser.getEmail());
		} catch (Exception e) {
			log.error("Failed to send ghost access email to: {}", targetUser.getEmail(), e);
		}
		return new KeyValue("status","success");
	}

	@Secured(LIST_ORG)
	@RequestMapping(method = RequestMethod.POST, value = "/revokeGhost")
	public KeyValue revokeAccess(@RequestBody GhostAccessRequest request) {
		// Determine the target user for revoking ghost access
		String userId = StringUtils.isNotBlank(request.getUserId())
			? request.getUserId()
			: SyncariContext.getUser().getId();
		subscriptionService.revokeAccess(userId, request.getSyncariId());
		return new KeyValue("status","success");
	}

	@RequestMapping(method = RequestMethod.GET, value = "/ghostAccess")
	public List<GhostAccessAudit> getGhostAccessAudit(
			@RequestParam(required = false) String status,
			@RequestParam(required = false) String userId) {
		User currentUser = SyncariContext.getUser();
		boolean isSuperAdmin = currentUser.isSuperAdmin();
		boolean isGhostUser = currentUser.isGhostUser();

		// Only superadmins and ghost users can access this endpoint
		if (!isSuperAdmin && !isGhostUser) {
			return new ArrayList<>();
		}

		// Superadmins can see all records
		if (isSuperAdmin) {
			if (StringUtils.isNotBlank(status)) {
				return ghostAccessAuditRepo.findByStatus(status);
			}
			return ghostAccessAuditRepo.findAll();
		}

		// Ghost users can only see their own records
		if (StringUtils.isNotBlank(status)) {
			return ghostAccessAuditRepo.findByRequesterIdAndStatus(currentUser.getId(), status);
		}
		return ghostAccessAuditRepo.findByRequesterId(currentUser.getId());
	}

	@Secured(LIST_ORG)
	@RequestMapping(method = RequestMethod.GET, value = "/syncariDevUsers")
	public List<User> getSyncariDevUsers() {
		return userService.getAllActiveUsers().stream()
				.filter(User::isSyncariDev)
				.collect(Collectors.toList());
	}

	private String getValue(Event e, String key) {
		if(e != null && e.getDetails() != null && e.getDetails().containsKey(key) && e.getDetails().get(key) != null) {
			return e.getDetails().get(key).toString();
		}
		return null;
	}

}

@Data
@Accessors(chain = true)
class CopyRequest {
	List<String> emailRecipients = new ArrayList<>();
}
