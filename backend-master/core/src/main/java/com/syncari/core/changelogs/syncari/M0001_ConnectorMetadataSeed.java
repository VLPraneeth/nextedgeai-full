package com.syncari.core.changelogs.syncari;

import com.github.mongobee.changeset.ChangeLog;
import com.github.mongobee.changeset.ChangeSet;
import com.mongodb.client.MongoCollection;
import com.mongodb.client.model.UpdateOptions;
import com.syncari.connector.ConnectorType;
import com.syncari.connector.Constants;
import com.syncari.connector.data.AuthType;
import org.bson.Document;
import org.springframework.data.mongodb.core.MongoTemplate;

import java.util.List;

@ChangeLog(order = "0001")
public class M0001_ConnectorMetadataSeed {

	@ChangeSet(order = "001", id = "addConnectorMetadataSeed", author = "varsha")
	public void addConnectorMetadataSeed(MongoTemplate template) {
		MongoCollection<Document> meta = template.getCollection("connectorMetadata");
		Document uname = new Document("name", "User Name").append("dataType", "text");
		Document pwd = new Document("name", "Password").append("dataType", "password");
		Document usrPwd = new Document("authType", AuthType.UserPassword.name()).append("fields", List.of(uname, pwd));
		
		meta.insertOne(new Document("name", Constants.SYNCARI)
				.append("type", ConnectorType.Synapse.name())
				.append("supportedAuthTypes", List.of()));
		
		meta.insertOne(new Document("name", Constants.SALESFORCE)
				.append("defaultApiLimit", 1000)
				.append("watermarkFieldName", "SystemModstamp"));
		
		meta.insertOne(new Document("name", Constants.HUBSPOT)
				.append("defaultApiLimit", 1000)
				.append("watermarkFieldName", "hs_lastmodifieddate")
				.append("updatedAtFieldName", "hs_lastmodifieddate"));

		meta.insertOne(new Document("name", Constants.ZENDESK)
				.append("defaultApiLimit", 1000)
				.append("watermarkCustomizable", true)
				.append("watermarkFieldName", "updated_at")
				.append("updatedAtFieldName", "updated_at"));

		meta.insertOne(new Document("name", Constants.MARKETO)
				.append("defaultApiLimit", 1000)
				.append("iconUri", "/icons/marketo.jpg"));

		meta.insertOne(new Document("name", Constants.GAINSIGHTCS)
				.append("defaultApiLimit", 1000)
				.append("iconUri", "/icons/gainsightcs.jpg"));

		meta.insertOne(new Document("name", Constants.REDSHIFT)
				.append("defaultApiLimit", 1000)
				.append("iconUri", "/icons/redshift.jpg"));
		
		meta.insertOne(new Document("name", Constants.ZUORA)
		        .append("defaultApiLimit", 1000)
		        .append("iconUri", "/icons/zuora.jpg"));
		
		meta.insertOne(new Document("name", Constants.SLACK)
		        .append("displayName", "Slack")
		        .append("type", ConnectorType.Service.name())
		        .append("supportedAuthTypes", List.of(usrPwd))
		        .append("defaultApiLimit", 1000)
		        .append("iconUri", "/icons/slack.jpg"));
	}

    @ChangeSet(order = "002", id = "snowflakeConnectorMetadataSeed", author = "varsha")
    public void snowflakeConnectorMetadataSeed(MongoTemplate template) {
        MongoCollection<Document> meta = template.getCollection("connectorMetadata");
        meta.insertOne(new Document("name", Constants.SNOWFLAKE));
    }

    @ChangeSet(order = "003", id = "airtableConnectorMetadataSeed", author = "varsha")
    public void airtableConnectorMetadataSeed(MongoTemplate template) {
        MongoCollection<Document> meta = template.getCollection("connectorMetadata");
        meta.insertOne(new Document("name", Constants.AIRTABLE));
    }
    
    @ChangeSet(order = "004", id = "datastoreConnectorMetadataSeed", author = "varsha")
    public void datastoreConnectorMetadataSeed(MongoTemplate template) {
        MongoCollection<Document> meta = template.getCollection("connectorMetadata");
        meta.insertOne(new Document("name", Constants.DATASTORE)
                .append("type", ConnectorType.Datastore.name()));
    }
    
    @ChangeSet(order = "005", id = "amplitudeMetadataSeed", author = "varsha")
    public void amplitudeMetadataSeed(MongoTemplate template) {
        MongoCollection<Document> meta = template.getCollection("connectorMetadata");
        meta.insertOne(new Document("name", Constants.AMPLITUDE));
    }

	@ChangeSet(order = "006", id = "xeroConnectorMetadataSeed", author = "Avinash")
	public void xeroConnectorMetadataSeed(MongoTemplate template) {
		MongoCollection<Document> meta = template.getCollection("connectorMetadata");
		meta.insertOne(new Document("name", Constants.XERO));
	}
	
	@ChangeSet(order= "007", id = "salesloftConnectorMetadataSeed", author = "Kunle")
	public void salesloftConnectorMetadataSeed(MongoTemplate template) {
			MongoCollection<Document> meta = template.getCollection("connectorMetadata");
			meta.insertOne(new Document("name", Constants.SALESLOFT));
	}

	@ChangeSet(order= "008", id = "msdynamicsConnectorMetadataSeed", author = "varsha")
    public void msdynamicsConnectorMetadataSeed(MongoTemplate template) {
        MongoCollection<Document> meta = template.getCollection("connectorMetadata");
        meta.insertOne(new Document("name", Constants.MSDYNAMICS));
    }

	@ChangeSet(order = "009", id = "kinesisConnectorMetadataSeed", author = "mike")
	public void kinesisConnectorMetadataSeed(MongoTemplate template) {
		MongoCollection<Document> meta = template.getCollection("connectorMetadata");

		meta.insertOne(new Document("name", Constants.KINESIS)
				.append("displayName", "Kinesis"));
	}

	@ChangeSet(order = "010", id = "driftConnectorMetadataSeed", author = "mike")
	public void driftConnectorMetadataSeed(MongoTemplate template) {
		MongoCollection<Document> meta = template.getCollection("connectorMetadata");

		meta.insertOne(new Document("name", Constants.DRIFT)
				.append("displayName", "Drift"));
	}

	@ChangeSet(order = "011", id = "pendoConnectorMetadataSeed", author = "mike")
	public void pendoConnectorMetadataSeed(MongoTemplate template) {
		MongoCollection<Document> meta = template.getCollection("connectorMetadata");

		meta.insertOne(new Document("name", Constants.PENDO)
				.append("displayName", "Pendo"));
	}

	@ChangeSet(order = "012", id = "eloquaConnectorMetadataSeed", author = "mike")
	public void eloquaConnectorMetadataSeed(MongoTemplate template) {
		MongoCollection<Document> meta = template.getCollection("connectorMetadata");

		meta.insertOne(new Document("name", Constants.ELOQUA)
				.append("displayName", "Eloqua"));
	}

	@ChangeSet(order = "013", id = "intacctConnectorMetadataSeed", author = "mike")
	public void intacctConnectorMetadataSeed(MongoTemplate template) {
		MongoCollection<Document> meta = template.getCollection("connectorMetadata");

		meta.insertOne(new Document("name", Constants.INTACCT)
				.append("displayName", "Intacct"));
	}

	@ChangeSet(order = "014", id = "intercomConnectorMetadataSeed", author = "mike")
	public void intercomConnectorMetadataSeed(MongoTemplate template) {
		MongoCollection<Document> meta = template.getCollection("connectorMetadata");

		meta.insertOne(new Document("name", Constants.INTERCOM)
				.append("displayName", "Intercom"));
	}

	@ChangeSet(order = "015", id = "jiraConnectorMetadataSeed", author = "mike")
	public void jiraConnectorMetadataSeed(MongoTemplate template) {
		MongoCollection<Document> meta = template.getCollection("connectorMetadata");

		meta.insertOne(new Document("name", Constants.JIRA)
				.append("displayName", "Jira"));
	}

	@ChangeSet(order = "016", id = "mixpanelConnectorMetadataSeed", author = "mike")
	public void mixpanelConnectorMetadataSeed(MongoTemplate template) {
		MongoCollection<Document> meta = template.getCollection("connectorMetadata");

		meta.insertOne(new Document("name", Constants.MIXPANEL)
				.append("displayName", "Mixpanel"));
	}

	@ChangeSet(order = "017", id = "exactTargetConnectorMetadataSeed", author = "mike")
	public void exactTargetConnectorMetadataSeed(MongoTemplate template) {
		MongoCollection<Document> meta = template.getCollection("connectorMetadata");

		meta.insertOne(new Document("name", "exacttarget"));
	}

	@ChangeSet(order = "018", id = "pardotConnectorMetadataSeed", author = "mike")
	public void pardotConnectorMetadataSeed(MongoTemplate template) {
		MongoCollection<Document> meta = template.getCollection("connectorMetadata");

		meta.insertOne(new Document("name", Constants.PARDOT)
				.append("displayName", "Pardot"));
	}

	@ChangeSet(order = "019", id = "sfMarketingCloudConnectorMetadataSeed", author = "mike")
	public void sfMarketingCloudConnectorMetadataSeed(MongoTemplate template) {
		MongoCollection<Document> meta = template.getCollection("connectorMetadata");

		meta.deleteOne(new Document("name", Constants.SF_MARKETING_CLOUD));
		meta.insertOne(new Document("name", Constants.SF_MARKETING_CLOUD));
	}

   @ChangeSet(order = "020", id = "s3ConnectorMetadataSeed", author = "varsha")
    public void s3ConnectorMetadataSeed(MongoTemplate template) {
        MongoCollection<Document> meta = template.getCollection("connectorMetadata");

        meta.insertOne(new Document("name", Constants.S3)
                .append("displayName", "Amazon S3"));
    }
   
   @ChangeSet(order = "021", id = "jiraServiceDeskConnectorMetadataSeed", author = "varsha")
   public void jiraServiceDeskConnectorMetadataSeed(MongoTemplate template) {
       MongoCollection<Document> meta = template.getCollection("connectorMetadata");

       meta.insertOne(new Document("name", Constants.JIRA_SERVICE_DESK)
               .append("displayName", "Jira Service Desk"));
   }

	@ChangeSet(order = "022", id = "updateIntacctMetadata", author = "neelesh")
	public void updateIntacctMetadata(MongoTemplate template) {
		MongoCollection<Document> meta = template.getCollection("connectorMetadata");
		meta.updateOne(new Document("name", Constants.INTACCT),
				new Document("$set",new Document("watermarkFieldName", "WHENMODIFIED")),new UpdateOptions().upsert(false));
	}
	
	@ChangeSet(order = "023", id = "freshsales", author = "varsha")
	public void freshsales(MongoTemplate template) {
	    MongoCollection<Document> meta = template.getCollection("connectorMetadata");
	    meta.insertOne(new Document("name", Constants.FRESHSALES));
	}
	
	@ChangeSet(order = "024", id = "postgresql", author = "varsha")
	public void postgresql(MongoTemplate template) {
	    MongoCollection<Document> meta = template.getCollection("connectorMetadata");
	    meta.insertOne(new Document("name", Constants.POSTGRESQL));
	}
	
    @ChangeSet(order = "025", id = "zoominfo", author = "varsha")
    public void zoominfo(MongoTemplate template) {
        MongoCollection<Document> meta = template.getCollection("connectorMetadata");
        meta.insertOne(new Document("name", Constants.ZOOMINFO_SYNAPSE));
    }
    
    @ChangeSet(order = "026", id = "bigquery", author = "varsha")
    public void bigquery(MongoTemplate template) {
        MongoCollection<Document> meta = template.getCollection("connectorMetadata");
        meta.insertOne(new Document("name", Constants.BIGQUERY));
    }

	@ChangeSet(order = "027", id = "testsynapse", author = "abhinav")
	public void testSynapse(MongoTemplate template) {
		//No-op
	}
	
	@ChangeSet(order = "028", id = "mysql", author = "varsha")
	public void mysql(MongoTemplate template) {
	    MongoCollection<Document> meta = template.getCollection("connectorMetadata");
	    meta.insertOne(new Document("name", Constants.MYSQL));
	}

    @ChangeSet(order = "029", id = "zoho", author = "sudee")
    public void zoho(MongoTemplate template) {
        MongoCollection<Document> meta = template.getCollection("connectorMetadata");
        meta.insertOne(new Document("name", Constants.ZOHO));
    }

    @ChangeSet(order = "030", id = "impartner", author = "sudee")
    public void impartner(MongoTemplate template) {
        MongoCollection<Document> meta = template.getCollection("connectorMetadata");
        meta.insertOne(new Document("name", Constants.IMPARTNER));
    }

	@ChangeSet(order = "031", id = "dynamodb", author = "rohit")
	public void dynamodb(MongoTemplate template) {
		MongoCollection<Document> meta = template.getCollection("connectorMetadata");
		meta.insertOne(new Document("name", Constants.DYNAMODB));
	}

	@ChangeSet(order = "032", id = "slacksynapse", author = "blesson")
	public void slacksynapse(MongoTemplate template) {
		MongoCollection<Document> meta = template.getCollection("connectorMetadata");
		meta.insertOne(new Document("name", Constants.SLACK_SYNAPSE));
	}

	@ChangeSet(order = "035", id = "oraclesalescrm", author = "rohit")
	public void oraclesalescrm(MongoTemplate template) {
		MongoCollection<Document> meta = template.getCollection("connectorMetadata");
		meta.insertOne(new Document("name", Constants.ORACLESALESCRM));
	}

	@ChangeSet(order = "036", id = "stripe", author = "blesson")
	public void stripe(MongoTemplate template) {
		MongoCollection<Document> meta = template.getCollection("connectorMetadata");
		meta.insertOne(new Document("name", Constants.STRIPE));
	}

	@ChangeSet(order = "036", id = "chargebee", author = "blesson")
	public void chargebee(MongoTemplate template) {
		MongoCollection<Document> meta = template.getCollection("connectorMetadata");
		meta.insertOne(new Document("name", Constants.CHARGEBEE));
	}
	
	@ChangeSet(order = "037", id = "filedata", author = "sibin")
	public void filedata(MongoTemplate template) {
		MongoCollection<Document> meta = template.getCollection("connectorMetadata");
		meta.insertOne(new Document("name", Constants.FILE_DATA));
	}

	@ChangeSet(order = "038", id = "sftp", author = "varsha")
	public void sftp(MongoTemplate template) {
		MongoCollection<Document> meta = template.getCollection("connectorMetadata");
		meta.insertOne(new Document("name", Constants.SFTP));
	}

	@ChangeSet(order = "039", id = "sap", author = "jason")
	public void sap(MongoTemplate template) {
		MongoCollection<Document> meta = template.getCollection("connectorMetadata");
		meta.insertOne(new Document("name", Constants.SAP));
	}

	@ChangeSet(order = "040", id = "removeUnused", author = "varsha")
	public void removeUnused(MongoTemplate template) {
		MongoCollection<Document> meta = template.getCollection("connectorMetadata");
		meta.deleteOne(new Document("name", Constants.KINESIS));
		meta.deleteOne(new Document("name", Constants.DRIFT));
		meta.deleteOne(new Document("name", Constants.MIXPANEL));
		meta.deleteOne(new Document("name", Constants.PENDO));
		meta.deleteOne(new Document("name", Constants.SF_MARKETING_CLOUD));
	}

	@ChangeSet(order = "041", id = "pendo", author = "durga")
	public void pendo(MongoTemplate template) {
		MongoCollection<Document> meta = template.getCollection("connectorMetadata");
		meta.insertOne(new Document("name", Constants.PENDO)
				.append("displayName", "Pendo"));
	}

	@ChangeSet(order = "042", id = "msdynamicsbizcentral", author = "neelesh")
	public void msdynamicsbizcentral(MongoTemplate template) {
		MongoCollection<Document> meta = template.getCollection("connectorMetadata");
		meta.insertOne(
				new Document("name", "msdynamicsbizcentral")
						.append("type", ConnectorType.Synapse.name())
						.append("displayName", "Dynamics 365 Business Central"));
	}

	@ChangeSet(order = "043", id = "pendofeedback", author = "neelesh")
	public void pendofeedback(MongoTemplate template) {
		MongoCollection<Document> meta = template.getCollection("connectorMetadata");
		meta.insertOne(
				new Document("name", Constants.PENDO_FEEDBACK)
						.append("type", ConnectorType.Synapse.name())
						.append("displayName", "Pendo Feedback"));
	}

    @ChangeSet(order = "044", id = "azuresql", author = "varsha")
    public void azuresql(MongoTemplate template) {
        MongoCollection<Document> meta = template.getCollection("connectorMetadata");
        meta.insertOne(
                new Document("name", Constants.AZURE_SQL)
                        .append("type", ConnectorType.Synapse.name())
                        .append("displayName", "Azure SQL"));
    }

    @ChangeSet(order = "045", id = "dataset", author = "shivam")
    public void dataset(MongoTemplate template) {
        MongoCollection<Document> meta = template.getCollection("connectorMetadata");
        meta.insertOne(new Document("name", Constants.DATASETS));
    }

	@ChangeSet(order = "044", id = "msteams", author = "blesson")
	public void msteams(MongoTemplate template) {
		MongoCollection<Document> meta = template.getCollection("connectorMetadata");
		meta.insertOne(
				new Document("name", Constants.MS_TEAMS)
						.append("type", ConnectorType.Service.name())
						.append("displayName", "MS Teams"));
	}

    @ChangeSet(order = "046", id = "azureblobstore", author = "sathish")
    public void azureblobstore(MongoTemplate template) {
        MongoCollection<Document> meta = template.getCollection("connectorMetadata");
        meta.insertOne(
                new Document("name", Constants.MS_AZURE_BLOB_STORE)
                        .append("type", ConnectorType.Service.name())
                        .append("displayName", "Microsoft Azure Blob Store"));
    }

    @ChangeSet(order = "047", id = "oraclepim", author = "sathish")
    public void oraclepim(MongoTemplate template) {
        MongoCollection<Document> meta = template.getCollection("connectorMetadata");
        meta.insertOne(
                new Document("name", Constants.ORACLE_PIM)
                        .append("type", ConnectorType.Service.name())
                        .append("displayName", "Oracle PIM"));
    }

	@ChangeSet(order = "048", id = "oracleerpsales", author = "sathish")
	public void oracleerpsales(MongoTemplate template) {
		MongoCollection<Document> meta = template.getCollection("connectorMetadata");
		meta.insertOne(
				new Document("name", Constants.ORACLE_ERP_SALES)
						.append("type", ConnectorType.Service.name())
						.append("displayName", "Oracle ERP Sales"));
	}

	@ChangeSet(order = "049", id = "kafka", author = "varsha")
	public void kafka(MongoTemplate template) {
		MongoCollection<Document> meta = template.getCollection("connectorMetadata");
		meta.insertOne(
				new Document("name", Constants.KAFKA)
						.append("type", ConnectorType.Service.name())
						.append("displayName", "Kafka"));
	}

	@ChangeSet(order = "050", id = "databricks", author = "rohit")
	public void databricks(MongoTemplate template) {
		MongoCollection<Document> meta = template.getCollection("connectorMetadata");
		meta.insertOne(new Document("name", Constants.DATABRICKS));
	}

	@ChangeSet(order = "051", id = "oracleerpreceivables", author = "sathish")
	public void oracleerpreceivables(MongoTemplate template) {
		MongoCollection<Document> meta = template.getCollection("connectorMetadata");
		meta.insertOne(
				new Document("name", Constants.ORACLE_ERP_RECEIVABLES)
						.append("type", ConnectorType.Service.name())
						.append("displayName", "Oracle ERP Receivables"));
	}

	@ChangeSet(order = "052", id = "addNetsuiteSuiteQLSeed", author = "richard")
	public void addNetsuiteSuiteQLSeed(MongoTemplate template) {
		MongoCollection<Document> connectorMetadata = template.getCollection("connectorMetadata");

		connectorMetadata.insertOne(new Document("name", Constants.NETSUITE_SUITEQL)
				.append("defaultApiLimit", 1000)
				.append("watermarkFieldName", "lastModifiedDate"));
	}

	@ChangeSet(order = "053", id = "oracleerprocurement", author = "sathish")
	public void oracleerprocurement(MongoTemplate template) {
		MongoCollection<Document> meta = template.getCollection("connectorMetadata");
		meta.insertOne(
				new Document("name", Constants.ORACLE_ERP_PROCUREMENT)
						.append("type", ConnectorType.Service.name())
						.append("displayName", "Oracle ERP Procurement"));
	}

	@ChangeSet(order = "054", id = "mongodb", author = "varsha")
	public void mongodb(MongoTemplate template) {
		MongoCollection<Document> meta = template.getCollection("connectorMetadata");
		meta.insertOne(
				new Document("name", Constants.MONGODB)
						.append("type", ConnectorType.Synapse.name())
						.append("displayName", "MongoDB"));
	}
}
