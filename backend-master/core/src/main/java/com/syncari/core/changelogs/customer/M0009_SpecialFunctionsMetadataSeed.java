package com.syncari.core.changelogs.customer;

import static com.mongodb.client.model.Filters.and;
import static com.mongodb.client.model.Filters.eq;

import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.stream.Collectors;

import com.syncari.core.functions.AidentifiedFunctionsSeed;
import com.syncari.core.functions.ApexAnalytixFunctionsSeed;
import com.syncari.core.functions.FunctionConstants;
import org.bson.Document;
import org.springframework.data.mongodb.core.MongoTemplate;

import com.github.mongobee.changeset.ChangeLog;
import com.github.mongobee.changeset.ChangeSet;
import com.mongodb.client.MongoCollection;
import com.syncari.core.datatype.Datatype;
import com.syncari.core.model.util.Scope;

@ChangeLog(order = "0009")
public class M0009_SpecialFunctionsMetadataSeed {

	@ChangeSet(order = "001", id = "addFilterFunctionMetadataSeed", author = "varsha")
	public void addFunctionsMetadataSeed(MongoTemplate template) {
		MongoCollection<Document> functions = template.getCollection("functionDefinition");

		Document valueConfig = getConfig("value", "text", "Value", "", Map.of("fieldSet", "conditionFields"));
		valueConfig.append("type","literal");
		functions.insertOne(new Document("name", "filter")
				.append("seeded", true)
				.append("scope", Scope.ENTITY.name()));

	}
	
	@ChangeSet(order = "002", id = "addLookUpRefDataFunctionMetadataSeed", author = "varsha")
	public void addLookUpRefDataFunctionMetadataSeed(MongoTemplate template) {
		MongoCollection<Document> functions = template.getCollection("functionDefinition");

		functions.insertOne(new Document("name", "lookUpRefData")
				.append("seeded", true)
				.append("scope", Scope.ATTRIBUTE.name()));
	}

	@ChangeSet(order = "003", id = "addClearbitLookupLeadFunctionMetadataSeed", author = "varsha")
	public void addClearbitLookupLeadFunctionMetadataSeed(MongoTemplate template) {
		MongoCollection<Document> functions = template.getCollection("functionDefinition");
		
		functions.insertOne(new Document("name", "enrichPerson")
				.append("seeded", true)
				.append("scope", Scope.ATTRIBUTE.name()));
	}

	@ChangeSet(order = "004", id = "addClearbitLookupCompanyFunctionMetadataSeed", author = "varsha")
	public void addClearbitLookupCompanyFunctionMetadataSeed(MongoTemplate template) {
		MongoCollection<Document> functions = template.getCollection("functionDefinition");

		functions.insertOne(new Document("name", "enrichCompany")
				.append("seeded", true)
				.append("scope", Scope.ATTRIBUTE.name()));
	}

	@ChangeSet(order = "005", id = "addSimilarWebTrafficData", author = "neelesh")
	public void addSimilarWebTrafficData(MongoTemplate template) {
		MongoCollection<Document> functions = template.getCollection("functionDefinition");

		functions.insertOne(new Document("name", "similarWebTrafficData")
				.append("seeded", true)
				.append("scope", Scope.ATTRIBUTE.name()));
	}

	@ChangeSet(order = "006", id = "addSalesIntelPersonEnrich", author = "rohit")
	public void addSalesIntelPersonEnrich(MongoTemplate template) {
		MongoCollection<Document> functions = template.getCollection("functionDefinition");

		functions.insertOne(new Document("name", "salesIntelPersonEnrich")
				.append("seeded", true)
				.append("scope", Scope.ATTRIBUTE.name()));
	}

	@ChangeSet(order = "007", id = "addSalesIntelCompanyEnrich", author = "rohit")
	public void addSalesIntelCompanyEnrich(MongoTemplate template) {
		MongoCollection<Document> functions = template.getCollection("functionDefinition");

		functions.insertOne(new Document("name", "salesIntelCompanyEnrich")
				.append("seeded", true)
				.append("scope", Scope.ATTRIBUTE.name()));
	}

	@ChangeSet(order = "008", id = "addApexAnalytixEnrich", author = "varsha")
	public void addApexAnalytixEnrich(MongoTemplate template) {
		MongoCollection<Document> functions = template.getCollection("functionDefinition");

		functions.insertOne(new Document("name", ApexAnalytixFunctionsSeed.APEX_ANALYTIX_COMPANY_ENRICH)
				.append("seeded", true)
				.append("scope", Scope.ATTRIBUTE.name()));
	}

	@ChangeSet(order = "009", id = "addAidentifiedEnrich", author = "varsha")
	public void addAidentifiedEnrich(MongoTemplate template) {
		MongoCollection<Document> functions = template.getCollection("functionDefinition");

		functions.insertOne(new Document("name", AidentifiedFunctionsSeed.AIDENTIFIED_PEOPLE_ENRICH)
				.append("seeded", true)
				.append("scope", Scope.ATTRIBUTE.name()));
	}

	@ChangeSet(order = "010", id = "addLookUpRefDataOnEntity", author = "varsha")
	public void addLookUpRefDataOnEntity(MongoTemplate template) {
		MongoCollection<Document> functions = template.getCollection("functionDefinition");

		functions.insertOne(new Document("name", FunctionConstants.LOOKUP_REF_DATA_ON_ENTITY)
				.append("seeded", true)
				.append("scope", Scope.ENTITY.name()));
	}

	private Document getConfig(String name, String datatype,String label, Object defaultValue, Map<String, Object> additionalProps) {
		return new Document("name", name).append("datatype", datatype)
				.append("defaultValue", defaultValue)
				.append("label", label)
				.append("additionalProperties", additionalProps);
	}

	private Document getParameterDoc(String name, Datatype datatype) {
		return new Document("name", name)
				.append("datatype", datatype.getName())
				.append("vararg", false);
	}

	private List<Document> getLeadFields() {
		Map<String, String> fields = new TreeMap<>();
		fields.put("person.id", "Id");
		fields.put("person.name.fullName", "Full Name");
		fields.put("person.name.givenName", "Given Name");
		fields.put("person.name.familyName", "Family Name");
		fields.put("person.email", "Email");
		fields.put("person.gender", "Gender");
		fields.put("person.location", "Location");
		fields.put("person.timeZone", "TimeZone");
		fields.put("person.utcOffset", "Utc Offset");
		fields.put("person.geo.city", "City");
		fields.put("person.geo.state", "State");
		fields.put("person.geo.stateCode", "State Code");
		fields.put("person.geo.country", "Country");
		fields.put("person.geo.countryCode", "Country Code");
		fields.put("person.geo.lat", "Latitude");
		fields.put("person.geo.lng", "Longitude");
		fields.put("person.bio", "Bio");
		fields.put("person.site", "Site");
		fields.put("person.avatar", "Avatar");
		fields.put("person.employment.name", "Employment Name");
		fields.put("person.employment.title", "Employment Title");
		fields.put("person.employment.domain", "Employment Domain");
		fields.put("person.employment.role", "Employment Role");
		fields.put("person.employment.seniority", "Employment Seniority");
		fields.put("person.facebook.handle", "Facebook Handle");
		fields.put("person.github.handle", "Github Handle");
		fields.put("person.github.id", "Github Id");
		fields.put("person.github.avatar", "Github Avatar");
		fields.put("person.github.company", "Github Company");
		fields.put("person.github.blog", "Github Blog");
		fields.put("person.github.followers", "Github Followers");
		fields.put("person.github.following", "Github Following");
		fields.put("person.twitter.handle", "Twitter Handle");
		fields.put("person.twitter.id", "Twitter Id");
		fields.put("person.twitter.bio", "Twitter Bio");
		fields.put("person.twitter.followers", "Twitter Followers");
		fields.put("person.twitter.following", "Twitter Following");
		fields.put("person.twitter.statuses", "Twitter Statuses");
		fields.put("person.twitter.favorites", "Twitter Favorites");
		fields.put("person.twitter.location", "Twitter Location");
		fields.put("person.twitter.site", "Twitter Site");
		fields.put("person.twitter.avatar", "Twitter Avatar");
		fields.put("person.linkedin.handle", "Linkedin Handle");
		fields.put("person.aboutme", "Aboutme");
		fields.put("company.id", "Company Id");
		fields.put("company.name", "Company Name");
		fields.put("company.legalName", "Company Legal Name");
		fields.put("company.domain", "Company Domain");
		fields.put("company.domainAliases", "Company Domain Aliases");
		fields.put("company.logo", "Company Logo");
		fields.put("company.site.phoneNumbers", "Company Phone Numbers");
		fields.put("company.site.emailAddresses", "Company Email Addresses");
		fields.put("company.category.sector", "Company Sector");
		fields.put("company.category.industryGroup", "Company Industry Group");
		fields.put("company.category.industry", "Company Industry");
		fields.put("company.category.subIndustry", "Company Sub Industry");
		fields.put("company.description", "Company Description");
		fields.put("company.foundedYear", "Company Founded Year");
		fields.put("company.location", "Company Location");
		fields.put("company.timeZone", "Company TimeZone");
		fields.put("company.geo.postalCode", "Company Postal Code");
		fields.put("company.geo.streetNumber", "Company Street Number");
		fields.put("company.geo.streetName", "Company Street Name");
		fields.put("company.geo.city", "Company City");
		fields.put("company.geo.state", "Company State");
		fields.put("company.geo.stateCode", "Company State Code");
		fields.put("company.geo.country", "Company Country");
		fields.put("company.geo.countryCode", "Company Country Code");
		return fields.entrySet().stream().map(e -> new Document("value", e.getKey()).append("label",e.getValue())).collect(Collectors.toList());
	}

	private List<Document> getCompanyFields() {
		Map<String, String> fields = new TreeMap<>();
		fields.put("id", "Id");
		fields.put("name", "Name");
		fields.put("legalName", "LegalName");
		fields.put("domain", "Domain");
		fields.put("domainAliases", "DomainAliases");
		fields.put("logo", "Logo");
		fields.put("site.title", "Title");
		fields.put("site.phoneNumbers", "Phone Numbers");
		fields.put("site.emailAddresses", "Email Addresses");
		fields.put("tags", "tags");
		fields.put("category.sector", "Sector");
		fields.put("category.industryGroup", "IndustryGroup");
		fields.put("category.industry", "Industry");
		fields.put("category.subIndustry", "SubIndustry");
		fields.put("description", "Description");
		fields.put("foundedYear", "FoundedYear");
		fields.put("location", "Location");
		fields.put("timeZone", "TimeZone");
		fields.put("utcOffset", "UtcOffset");
		fields.put("geo.streetNumber", "StreetNumber");
		fields.put("geo.streetName", "StreetName");
		fields.put("geo.city", "City");
		fields.put("geo.state", "State");
		fields.put("geo.stateCode", "StateCode");
		fields.put("geo.postalCode", "PostalCode");
		fields.put("geo.country", "Country");
		fields.put("geo.countryCode", "CountryCode");
		fields.put("metrics.alexaUsRank", "Alexa US Rank");
		fields.put("metrics.alexaGlobalRank", "Alexa Global Rank");
		fields.put("metrics.employees", "Employees");
		fields.put("metrics.marketCap", "MarketCap");
		fields.put("metrics.raised", "Raised");
		fields.put("metrics.annualRevenue", "Annual Revenue");
		fields.put("metrics.fiscalYearEnd", "Fiscal Year End");
		fields.put("metrics.estimatedAnnualRevenue", "Estimated Annual Revenue");
		fields.put("facebook.handle", "Facebook Handle");
		fields.put("linkedin.handle", "Linkedin handle");
		fields.put("twitter.handle", "Twitter handle");
		fields.put("twitter.id", "Twitter Id");
		fields.put("twitter.bio", "Twitter Bio");
		fields.put("twitter.followers", "Twitter Followers");
		fields.put("twitter.following", "Twitter Following");
		fields.put("twitter.location", "Twitter Location");
		fields.put("twitter.site", "Twitter Site");
		fields.put("twitter.avatar", "Twitter Avatar");
		fields.put("crunchbase.handle", "Crunchbase Handle");
		fields.put("emailProvider", "Email Provider");
		fields.put("type", "Type");
		fields.put("ticker", "Ticker");
		fields.put("phone", "Phone");
		fields.put("indexedAt", "Indexed At");
		fields.put("tech", "Tech");
		fields.put("parent.domain", "Parent Domain");
		return fields.entrySet().stream().map(e -> new Document("value", e.getKey()).append("label",e.getValue())).collect(Collectors.toList());
	}
}
