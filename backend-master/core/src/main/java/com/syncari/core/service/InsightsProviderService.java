package com.syncari.core.service;

import com.syncari.core.model.Organization;
import com.syncari.core.model.insights.dataset.Dataset;
import com.syncari.core.model.insights.provider.InsightsProviderConnection;
import com.syncari.core.model.insights.provider.InsightsProviderGroup;
import com.syncari.core.model.insights.provider.InsightsProviderUser;
import com.syncari.core.model.insights.provider.ts.*;
import org.springframework.http.HttpHeaders;

import java.util.List;
import java.util.Map;
import java.util.Optional;

public interface InsightsProviderService {

    // Instance is an org in Insights provider, in dev there is only one instance and multiple connections are created
    public String createOrganization(Organization organization,HttpHeaders headers);
    public void deleteOrganization(Organization organization,HttpHeaders headers);
    public String createGroup(InsightsProviderGroup group,Optional<String> tsUsername,HttpHeaders headers);
    public void deleteGroup(String tsGroupId,Optional<String> tsUsername,HttpHeaders headers);
    public TSUserResponse createUser(InsightsProviderUser user, Optional<String> tsUsername,HttpHeaders headers);
    public void deleteUser(String tsUserId,Optional<String> tsUsername,HttpHeaders headers);
    public Optional<TSUserResponse> searchUser(InsightsProviderUser user,Optional<String> tsUsername, boolean isPrimaryOrg,HttpHeaders headers);
    public Map<String, String> createOrUpdateDataset(Dataset dataset, String connectionName, Optional<String> tsUsername, boolean isCreate,HttpHeaders headers);
    public void deleteDataset(Dataset dataset,Optional<String> tsUsername,HttpHeaders headers);
    public String createConnection(InsightsProviderConnection connection,Optional<String> tsUsername,HttpHeaders headers);
    public Optional<String> searchConnection(InsightsProviderConnection connection,Optional<String> tsUsername,HttpHeaders headers);
    public Optional<TSConnResponse> searchConnectionV2(InsightsProviderConnection connection,Optional<String> tsUsername,HttpHeaders headers);
    public void deleteConnection(InsightsProviderConnection connection,Optional<String> tsUsername,HttpHeaders headers);
    public void updateConnection(InsightsProviderConnection connection,Optional<String> tsUsername,HttpHeaders headers);
    public void updateUser(InsightsProviderUser user,Optional<String> tsUsername, boolean isHeaderPrimaryOrg,HttpHeaders headers);
    public void shareMetadata(TSMetadataShareRequest request,Optional<String> tsUsername,HttpHeaders headers);
    public void changeOwnerMetadata(TSChangeOwnerRequest ownerReq,Optional<String> tsUsername,HttpHeaders headers);
    public void deleteMetadata(String metadataId,HttpHeaders headers);


        public Optional<TSGrpResponse> searchGroup(String groupName, Optional<String> tsUsername,HttpHeaders headers);
    public List<TSGrpResponse> searchLocalGroupsForAUser(String insightsProviderUserId, Optional<String> tsUsername,HttpHeaders headers);
    public List<TSGrpResponse> searchAllLocalGroups(Optional<String> tsUsername,HttpHeaders headers);


    public boolean addOrRemoveUserToGroup(String groupName,List<String> tsUserIds, Optional<String> tsUsername,HttpHeaders headers,GroupOperation groupOperation);

    public List<TSMetadataSearchResponse> searchMetadata(TSMetadataSearchReq metadataReq, Optional<String> tsUsername,HttpHeaders headers);
    public TSToken getBearerToken(String userName, String orgId, Long tokenTimeOut);
    public boolean validateToken(String token);

    public HttpHeaders getHeaders(Optional<String> insightsProviderUserName, Long tokenTimeOut);

    }
