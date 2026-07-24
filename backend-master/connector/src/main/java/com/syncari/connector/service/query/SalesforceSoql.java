package com.syncari.connector.service.query;

public class SalesforceSoql {
	public static final String QUERY_BY_IDS = "select %s from %s where Id in (%s) %s";
	public static final String QUERY_BY_WATERMARK = "SELECT %s FROM %s WHERE "
			+ "%s > %s AND %s <= %s %s ORDER BY %s";
	public static final String QUERY_BY_WATERMARK_NO_END = "SELECT %s FROM %s WHERE %s > %s %s ORDER BY %s";
	public static final String QUERY_BY_WATERMARK_CONTENT_DOCUMENT = "SELECT %s FROM %s USING SCOPE everything WHERE "
			+ "%s > %s AND %s <= %s %s ORDER BY %s";
	public static final String QUERY_BY_WATERMARK_NO_END_CONTENT_DOCUMENT = "SELECT %s FROM %s USING SCOPE everything WHERE %s > %s %s ORDER BY %s";
	public static final String QUERY_FIRST_RECORD = "SELECT %s from %s ORDER BY %s limit 1";
	public static final String QUERY_CAMPAIGN_MEMBERSHIP = "SELECT Id, ContactId, LeadId, CampaignId FROM CampaignMember WHERE %s IN (%s)";
	public static final String QUERY_PROFILE_WITH_OBJECT_PERMISSIONS = "SELECT SObjectType, PermissionsModifyAllRecords, PermissionsCreate  "
			+ " FROM ObjectPermissions WHERE ParentId IN ("
			+ "SELECT Id from PermissionSet WHERE Profile.Id = '%s') AND SObjectType IN (%s)";
    public static final String QUERY_CONTENT_DOCUMENT_LINK = "SELECT Id, ContentDocumentId, LinkedEntityId FROM ContentDocumentLink WHERE %s IN (%s)";
    public static final String QUERY_ACCOUNT_CONTACT_RELATION = "SELECT Id, AccountId, ContactId, EndDate, IsActive, IsDirect, StartDate, Roles FROM AccountContactRelation WHERE %s IN (%s)";
}
