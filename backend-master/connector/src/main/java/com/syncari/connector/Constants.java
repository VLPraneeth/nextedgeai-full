package com.syncari.connector;

public class Constants {
	public static final String SYNCARI_FABRICATED_WATERMARKFIELD = "SyncariFabricatedWaterMark";
	public static final String SYNCARI_SRC_PREDICATE = "syncari_src_predicate";
	public static final String LEAD_MERGE_IN_CRM = "lead_merge_in_crm";
	public static final String SYNCARI = "syncari";
	public static final String SALESFORCE = "salesforce";
	public static final String HUBSPOT = "hubspot";
	public static final String ZENDESK = "zendesk";
	public static final String MARKETO = "marketo";
	public static final String ORACLESALESCRM = "oraclesalescrm";
	public static final String GAINSIGHTCS = "gainsightcs";
	public static final String MSDYNAMICS = "msdynamics";
	public static final String REDSHIFT = "redshift";
	public static final String BIGQUERY = "bigquery";
	public static final String SNOWFLAKE = "snowflake";
	public static final String NETSUITE = "netsuite";
	public static final String NETSUITE_SUITEQL = "netsuite_suiteql";
	public static final String OUTREACH = "outreach";
	public static final String GOOGLESHEETS = "googlesheets";
	public static final String AIRTABLE = "airtable";
	public static final String FRESHSALES = "freshsales";
	public static final String AMPLITUDE = "amplitude";
	public static final String DATASTORE = "datastore";
	public static final String ZUORA = "zuora";
	public static final String SLACK = "slack";
	public static final String SLACK_SYNAPSE = "slacksynapse";
	public static final String ZOOMINFO = "zoominfo";
	public static final String APEX_ANALYTIX = "apexanalytix";
	public static final String AIDENTIFIED = "aidentified";
	public static final String SALESINTEL = "salesintel";
	public static final String ZOOMINFO_SYNAPSE = "zoominfosynapse";
	public static final String DATABRICKS = "databricks";
	public static final String XERO = "xero";
	public static final String SALESLOFT = "salesloft";
	public static final String KINESIS = "kinesis";
	public static final String DRIFT = "drift";
	public static final String PENDO = "pendo";
	public static final String PENDO_FEEDBACK = "pendofeedback";
	public static final String ELOQUA = "eloqua";
    public static final String INSIDEVIEW = "insideview";
	public static final String INTACCT = "intacct";
	public static final String INTERCOM = "intercom";
	public static final String JIRA = "jira";
	public static final String JIRA_SERVICE_DESK = "jiraservicedesk";
	public static final String POSTGRESQL = "postgresql";
	public static final String MYSQL = "mysql";
	public static final String DYNAMODB = "dynamodb";
	public static final String MONGODB = "mongodb";
	public static final String MIXPANEL = "mixpanel";
	public static final String OKTA = "okta";
	public static final String SF_MARKETING_CLOUD = "sfconnector";
	public static final String PARDOT = "pardot";
    public static final String IMPARTNER = "impartner";
	public static final String S3 = "s3";
	public static final String SFTP = "sftp";
	public static final String SIMILAR_WEB = "SimilarWeb";
	public static final String TEST_SYNAPSE = "TestSynapse";
	public static final String ZOHO = "zoho";
	public static final String CUSTOM = "custom";
	public static final String STRIPE = "stripe";
	public static final String CHARGEBEE = "chargebee";
	public static final String FILE_DATA = "filedata";
	public static final String SAP = "sap";
	public static final String IMPORTED_FILES = "Imported Files";
	public static final String AZURE_SQL = "azuresql";
	public static final String DATASETS = "datasets";
	public static final String DATASETS_DISPLAY_NAME = "Datasets";
	public static final String HTTP_SOURCES = "httpsources";
	public static final String WEBHOOK_RECEIVER = "webhookreceiver";
	public static final String KAFKA = "kafka";

	public static final String MS_TEAMS = "msteams";
	public static final String MS_AZURE_BLOB_STORE = "azureblobstore";
	public static final String ORACLE_PIM = "oraclepim";
	public static final String ORACLE_ERP_SALES = "oracleerpsales";
	public static final String ORACLE_ERP_RECEIVABLES = "oracleerpreceivables";
	public static final String ORACLE_ERP_PROCUREMENT = "oracleerprocurement";

	// Datastore
	public static final String POSTGRESQL_DATASTORE = "postgresql_datastore";
	public static final String SNOWFLAKE_DATASTORE = "snowflake_datastore";

	// Generic credentials
	public static final String GENERIC_NONE = "genericNone";
	public static final String GENERIC_API_KEY = "genericApiKey";
	public static final String GENERIC_SIMPLE_OAUTH = "genericSimpleOAuth";
	public static final String GENERIC_BEARER_TOKEN = "genericBearerToken";

	public static final String ACCOUNT = "Account";
	public static final String CONTACT = "Contact";
    public static final String DOCUMENT = "Document";
	public static final String LEAD = "Lead";
	public static final String USER = "User";
	public static final String OPPORTUNITY = "Opportunity";
	public static final String CASE = "Case";
	public static final String TASK = "Task";
	public static final String ACTIVITYHISTORY = "ActivityHistory";
	public static final String ACTIVITY = "activity";
	public static final String FORM = "form";
	public static final String FORM_SUBMISSION = "formsubmission";
	public static final String EMAIL_EVENT = "emailevent";
	public static final String COMPANY = "company";
	
	public static final String ORGANIZATION = "organization";
    public static final String TICKET = "ticket";
    public static final String COMMENT = "comment";
    public static final String EVENT = "event";
	public static final String OWNER = "owner";
	public static final String DEAL = "deal";
	public static final String PROGRAM = "program";
	public static final String LENGTH = "length";
	public static final String SCALE = "scale";
	public static final String PRECISION = "precision";
	public static final String REFERENCE = "reference";
	public static final String REFERENCE_TO = "referenceto";
	public static final String REFERENCE_TARGET_FIELD = "referenceTargetField";
	public static final String POLYMORPHIC = "polymorphic";
	public static final String SYNCARI_ID = "syncariid";
	
	public static final String SERVER_NAME = "serverName";
	public static final String CLUSTER_NAME = "clusterName";
    public static final String SCHEMA_NAME = "schemaName";
    public static final String DATABASE_NAME = "dbName";
    public static final String SKIP_CLOCK_SKEW = "SKIP_CLOCK_SKEW";

	public static final int FIVE_MIN_IN_MILLI = 5 * 60 * 1000;
	public static final int TWO_MIN_IN_MILLI = 2 * 60 * 1000;
	public static final String REJECT_EMPTY = "rejectEmpty";
	public static final String ENTITY_DEFINITION_ID = "entityDefinitionId";

	public static final String BQ_INSERT_OPTION = "insertOption";
	public static final String BQ_INSERT_AND_UPDATE_OPTION = "bcInsertAndUpdate";
	public static final String BQ_FULL_RECORD_TO_INSERT_OPTION = "fullRecordToInsert";
	public static final String BQ_PARTIAL_RECORD_TO_INSERT_OPTION = "partialRecordToInsert";
	public static final String BQ_CHANGESET_FIELD = "bcChangesetField";
	public static final String MARKETO_ACTION = "action";
	public static final String MARKETO_CREATE_OR_UPDATE = "createOrUpdate";
	public static final String MARKETO_CREATE_ONLY = "createOnly";
	public static final String MARKETO_UPDATE_ONLY = "updateOnly";
	public static final String MARKETO_CREATE_DUPLICATE = "createDuplicate";
	public static final String MARKETO_LOOK_UP_FIELD = "lookupField";
	public static final String PIPELINE_BATCH_SIZE = "pipeLineBatchSize";
	public static final String ORACLE = "oracle";
	public static final String PORT = "port";

	public static String AZURE_BLOB_STORE_CONTAINER_NAME = "containerName";
	public static String AZURE_BLOB_STORE_CONNECTION_STRING = "connectionString";
	public static String AZURE_BLOB_STORE_STORAGE_ACCOUNT_NAME = "storageAccountName";
	public static String AZURE_BLOB_STORE_DIRECTORY_NAME = "directoryName";
	public static String AZURE_BLOB_STORE_AUTH_TYPE_OAUTH_DISPLAY_NAME = "Oauth";
	public static String AZURE_BLOB_STORE_AUTH_TYPE_SAS_DISPLAY_NAME = "SAS Token";

	public static String AUTH_TYPE = "authType";
	public static String WITH_TRIM = "withTrim";

	public enum REJECT_EMPTY_ENUM {
		ALWAYS,
		NEVER,
		ON_CREATE,
		ON_UPDATE
	}
}
