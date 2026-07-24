package com.syncari.api.rest.controllers;

import com.syncari.connector.service.def.SynapseInfoService;
import com.syncari.core.DataTransformer;
import com.syncari.core.abac.AbacService;
import com.syncari.core.datatype.Datatype;
import com.syncari.core.datatype.IntegerType;
import com.syncari.core.datatype.ObjectType;
import com.syncari.core.datatype.StringType;
import com.syncari.core.draft.DraftStatus;
import com.syncari.core.enrich.ClearbitService;
import com.syncari.core.enrich.similarweb.SimilarWebAPICategory;
import com.syncari.core.model.*;
import com.syncari.core.model.insights.Projection;
import com.syncari.core.model.insights.dataset.Dataset;
import com.syncari.core.model.insights.dataset.DatasetConfig;
import com.syncari.core.model.util.MappingNodeType;
import com.syncari.core.pipeline.NodeInfoContext;
import com.syncari.core.pipeline.NodeInfoFactory;
import com.syncari.core.quickstart.dedupe.DedupeQuickStartSeed;
import com.syncari.core.repositories.customer.EntityRepo;
import com.syncari.core.schema.Schema;
import com.syncari.core.service.*;
import com.syncari.core.token.TokenHelper;
import com.syncari.utils.KeyValue;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.BooleanUtils;
import org.apache.commons.lang3.StringUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Pageable;
import org.springframework.security.access.annotation.Secured;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.io.UnsupportedEncodingException;
import java.net.URLDecoder;
import java.util.*;
import java.util.Map.Entry;
import java.util.stream.Collectors;

import static com.syncari.core.security.Permissions.READ_STUDIO;
import static com.syncari.utils.I18n.i18n;

@Slf4j
@RestController
@RequestMapping(value = "/api/v1/picklist")
public class PicklistController {
    @Autowired
    private ReferenceDataService service;

	@Autowired
	private ConnectorService connectorService;
	@Autowired
	private SchemaService schemaService;
	@Autowired
	private EntityRepo entityRepo;
	@Autowired
	private MappingGraphService graphService;
	private static Map<String, String> TYPE_EQUIVALENTS = new HashMap<>();
	@Autowired
	DataServiceFactory dataServiceFactory;
	@Autowired
	DataTransformer transformer;
	@Autowired
    ClearbitService clearbitService;
	@Autowired
    DataServiceFactory factory;
	@Autowired
	NodeInfoFactory nodeInfoFactory;

	@Autowired
	FeatureService featureService;
	@Autowired
	TokenHelper tokenHelper;

	@Autowired
	DatasetService datasetService;
	
	@Autowired
	AbacService abacService;

	static {
		TYPE_EQUIVALENTS.put("double", "number");
		TYPE_EQUIVALENTS.put("number", "number");
		TYPE_EQUIVALENTS.put("text", "text");
		TYPE_EQUIVALENTS.put("integer", "number");
		TYPE_EQUIVALENTS.put("timestamp", "number");
		TYPE_EQUIVALENTS.put("int", "number");
		TYPE_EQUIVALENTS.put("textarea", "text");
		TYPE_EQUIVALENTS.put("string", "text");
		TYPE_EQUIVALENTS.put("id", "id");
		TYPE_EQUIVALENTS.put("date", "date");
		TYPE_EQUIVALENTS.put("datetime", "date");
		TYPE_EQUIVALENTS.put("boolean", "boolean");
		TYPE_EQUIVALENTS.put("checkbox", "boolean");
		TYPE_EQUIVALENTS.put("bool", "boolean");
		TYPE_EQUIVALENTS.put("url", "text");
		TYPE_EQUIVALENTS.put("currency", "number");
		TYPE_EQUIVALENTS.put("email", "text");
		TYPE_EQUIVALENTS.put("object", "object");
	}
	private static Map<String, List<KeyValue>> operatorMap1 = Map.of(
			"index", List.of(createOperator("eq","Equals")
					,createOperator("gt","Greater Than")
					,createOperator("lt","Less Than")
					,createOperator("lte","Less Than Or Equal To")
					,createOperator("gte","Greater Than Or Equal To")
			));

	private static List<KeyValue> dfiOperatorList = List.of(
			createOperator("eq","Equals"),
			createOperator("ne","Not Equals"),
			createOperator("email","Is Email").set("unary",true),
			createOperator("phone","Is PhoneNumber").set("unary",true),
			createOperator("matches","Matches")
	);

	private static Map<String, List<KeyValue>> dfiAdditionalOperatorMap = Map.of(
			"text", List.of(
					createOperator("email","Is Email").set("unary",true),
					createOperator("phone","Is PhoneNumber").set("unary",true),
					createOperator("matches","Matches"),
					createReferenceOperator("is_in_reference_data", "Is in reference data"),
					createReferenceOperator("is_in_reference_data_case_insensitive", "Is in reference data case insensitive")
			),
			"textarea", List.of(
					createOperator("email","Is Email").set("unary",true),
					createOperator("phone","Is PhoneNumber").set("unary",true),
					createOperator("matches","Matches"),
					createReferenceOperator("is_in_reference_data", "Is in reference data"),
					createReferenceOperator("is_in_reference_data_case_insensitive", "Is in reference data case insensitive")
			),
			"string", List.of(
					createOperator("email","Is Email").set("unary",true),
					createOperator("phone","Is PhoneNumber").set("unary",true),
					createOperator("matches","Matches"),
					createReferenceOperator("is_in_reference_data", "Is in reference data"),
					createReferenceOperator("is_in_reference_data_case_insensitive", "Is in reference data case insensitive")
			),
			"number", List.of(
					createOperator("phone","Is PhoneNumber").set("unary",true),
					createOperator("matches","Matches"),
					createReferenceOperator("is_in_reference_data", "Is in reference data"),
					createReferenceOperator("is_in_reference_data_case_insensitive", "Is in reference data case insensitive")
			)
	);

	private static Map<String, List<KeyValue>> operatorMap = Map.of(
			"text", List.of(createOperator("eq","Equals")
			        ,createOperator("ieq","Equals Ignore Case")
					,createOperator("starts_with","Starts With")
					,createOperator("empty","Is Empty")
					,createOperator("not_empty","Is Not Empty")
					,createOperator("ne","Not Equals")
					,createOperator("contains","Contains")
					,createMultivaluedOperator("in","In")
					,createMultivaluedOperator("not_in","Not In")
					),
			"boolean", List.of(createOperator("eq","Equals")
					,createOperator("ne","Not Equals")
			),
			"id", List.of(createOperator("eq","Equals")
			        ),
			"textarea", List.of(createOperator("eq","Equals")
			        ,createOperator("ieq","Equals Ignore Case")
					,createOperator("starts_with","Starts With")
					,createOperator("empty","Is Empty")
					,createOperator("not_empty","Is Not Empty")
					,createOperator("ne","Not Equals")
					,createOperator("contains","Contains")
			),
			"string", List.of(createOperatorStringDataType("eq","Equals")
			        ,createOperatorStringDataType("ieq","Equals Ignore Case")
					,createOperatorStringDataType("starts_with","Starts With")
					,createOperatorStringDataType("empty","Is Empty")
					,createOperatorStringDataType("not_empty","Is Not Empty")
					,createOperatorStringDataType("ne","Not Equals")
					,createOperatorStringDataType("contains","Contains")
					,createMultivaluedOperator("in","In")
					,createMultivaluedOperator("not_in","Not In")
			),
			"date", List.of(createOperator("eq","Equals")
					,createOperator("gt","Is After")
					,createOperator("lt","Is Before")
					,createOperator("lte","On Or Before")
					,createOperator("gte","On Or After")
					,createOperator("empty","Is Empty")
					,createOperator("not_empty","Is Not Empty")
					,createOperator("ne","Not Equals")
			),
			"datetime", List.of(createOperator("eq","Equals")
					,createOperator("gt","Is After")
					,createOperator("lt","Is Before")
					,createOperator("lte","On Or Before")
					,createOperator("gte","On Or After")
					,createOperator("empty","Is Empty")
					,createOperator("not_empty","Is Not Empty")
					,createOperator("ne","Not Equals")
			),
			"number", List.of(createOperator("eq","Equals")
					,createOperator("gt","Greater Than")
					,createOperator("lt","Less Than")
					,createOperator("lte","Less Than Or Equal To")
					,createOperator("gte","Greater Than Or Equal To")
					,createOperator("empty","Is Empty")
					,createOperator("not_empty","Is Not Empty")
					,createOperator("ne","Not Equals")
			),"exact_match", List.of(createOperator("eq","Is")
					,createOperator("ne","Is Not")
			),
			"object", List.of(createOperator("empty","Is Empty",true)
					,createOperator("not_empty","Is Not Empty",true)
			)
	);
	
	private static Map<String, List<KeyValue>> abacOperatorMap = Map.of(
        "text", List.of(createOperator("eq","Equals")
                ,createOperator("ne","Not Equals")
                ,createOperator("contains","Contains")
                ,createOperator("empty","Is Empty")
                ,createOperator("not_empty","Is Not Empty")
                ),
        "boolean", List.of(createOperator("eq","Equals")
                ,createOperator("ne","Not Equals")
        ),
        "date", List.of(createOperator("eq","Equals")
                ,createOperator("gt","Is After")
                ,createOperator("lt","Is Before")
                ,createOperator("lte","On Or Before")
                ,createOperator("gte","On Or After")
                ,createOperator("ne","Not Equals")
        ),
        "datetime", List.of(createOperator("eq","Equals")
                ,createOperator("gt","Is After")
                ,createOperator("lt","Is Before")
                ,createOperator("lte","On Or Before")
                ,createOperator("gte","On Or After")
                ,createOperator("ne","Not Equals")
        ),
        "number", List.of(createOperator("eq","Equals")
                ,createOperator("gt","Greater Than")
                ,createOperator("lt","Less Than")
                ,createOperator("lte","Less Than Or Equal To")
                ,createOperator("gte","Greater Than Or Equal To")
                ,createOperator("ne","Not Equals")
        )
);

	private static final  List<KeyValue> multivaluedFieldOperations =  List.of(
			new KeyValue("value", "replace").set("label", i18n("operation_label_replace")),
			new KeyValue("value", "prepend").set("label", i18n("operation_label_prepend")),
			new KeyValue("value", "append").set("label", i18n("operation_label_append")),
			new KeyValue("value", "remove").set("label", i18n("operation_label_remove"))
	);
	private static Map<String, List<KeyValue>> operationMap = Map.of(
			"text", List.of(
					new KeyValue("value", "replace").set("label", i18n("operation_label_replace")),
					new KeyValue("value", "prefix").set("label", i18n("operation_label_prefix")),
					new KeyValue("value", "suffix").set("label", i18n("operation_label_suffix"))
			),
			"boolean", List.of(
					new KeyValue("value", "replace").set("label", i18n("operation_label_replace"))
			),
			"id", List.of(
					new KeyValue("value", "replace").set("label", i18n("operation_label_replace"))
			),
			"textarea", List.of(
					new KeyValue("value", "replace").set("label", i18n("operation_label_replace")),
					new KeyValue("value", "prefix").set("label", i18n("operation_label_prefix")),
					new KeyValue("value", "suffix").set("label", i18n("operation_label_suffix"))
			),
			"string", List.of(
					new KeyValue("value", "replace").set("label", i18n("operation_label_replace")),
					new KeyValue("value", "prefix").set("label", i18n("operation_label_prefix")),
					new KeyValue("value", "suffix").set("label", i18n("operation_label_suffix"))

			),
			"date", List.of(
					new KeyValue("value", "replace").set("label", i18n("operation_label_replace"))
			),
			"datetime", List.of(
					new KeyValue("value", "replace").set("label", i18n("operation_label_replace"))
			),
			"number", List.of(
					new KeyValue("value", "replace").set("label", i18n("operation_label_replace"))
			),
			"object", List.of(
					new KeyValue("value", "replace").set("label", i18n("operation_label_replace"))
			)

	);
	private static KeyValue createOperator(String value, String label) {
		return new KeyValue("value", value).set("label", label).set("unary", Arrays.stream(new String[]{"empty", "not_empty"}).anyMatch(value::equals));
	}

	private static KeyValue createReferenceOperator(String value, String label) {
		return new KeyValue("value", value).set("label", label).set("unary", false).set("datatype", "reference_data");
	}

	private static KeyValue createOperatorStringDataType(String value, String label) {
		return new KeyValue("value", value).set("label", label).set("datatype", "string")
				.set("unary", Arrays.stream(new String[]{"empty", "not_empty"}).anyMatch(value::equals));
	}

	private static KeyValue createMultivaluedOperator(String value, String label) {
		return new KeyValue("value", value).set("label", label).set("datatype", "multivaluetext");
	}

	private static KeyValue createMultivaluedOperatorWithRenderType(String value, String label) {
		return new KeyValue("value", value).set("label", label).set("renderType", "fieldmergepolicyretainfield").set("configuration",List.of(getFieldMergePolicRetainFieldKeyVal(),KeyValue.of("value", value,"datatype", "multivaluetext","name","multivaluetext")));
	}

	private static KeyValue createMultivaluedWithPicklistOperator(String value, String label) {
		return new KeyValue("value", value).set("label", label).set("renderType", "fieldmergepolicyretainfield")
				.set("configuration", List.of(getFieldMergePolicRetainFieldKeyVal(),KeyValue.of("value", value,"datatype", "multivaluetext","name","multivaluetext"),
						KeyValue.of("datatype", "picklist","name", "sortField","dependantType",
						"fieldMergePicklistSortField","dependantId", "leftValue"),
						KeyValue.of("datatype", "picklist","name", "sortDirection","dependantType", "fieldMergePicklistSortDirection","dependantId", "leftValue"
								)));
	}

	private static KeyValue createOperator(String value, String label, boolean isUnary) {
		return new KeyValue("value", value).set("label", label).set("unary", isUnary);
	}

	private static KeyValue createOperatorWithRetain(String value, String label, boolean isUnary, String renderType) {
		return new KeyValue("value", value).set("label", label).set("unary", isUnary).set("renderType", renderType).set("configuration",List.of(getFieldMergePolicRetainFieldKeyVal()));
	}

	private static KeyValue getFieldMergePolicRetainFieldKeyVal(){
		return KeyValue.of("renderType","fieldmergepolicyretainfield","dependantId", "leftValue","dependantType","fieldMergePicklistSortField","name","retainfields", "defaultValue", List.of());
	}

	//@Secured(READ_STUDIO)
	@PreAuthorize("hasAnyAuthority('READ_STUDIO', 'READ_DATA_STUDIO')")
    @RequestMapping(method = RequestMethod.GET, value = "/values")
	public List<KeyValue> getValues(@RequestParam("dependantType") String dependantType,
			@RequestParam("dependantId") String dependantId,
			@RequestParam(value = "sendPicklistGroup", required = false) Boolean sendPicklistGroup,
			@RequestParam Map<String, String> params) {
		if(StringUtils.isBlank(dependantType)) throw new RuntimeException("Dependant Type is required");
    	if(StringUtils.isBlank(dependantId)) throw new RuntimeException("Dependant Id is required");
    	List<KeyValue> results = new ArrayList<>();
    	switch (dependantType) {
		case "ReferenceData":
			ReferenceDataMeta referenceData = service.getReferenceData(dependantId);
			Map<String, Datatype> fields = referenceData.getFields();
			for (Entry<String, Datatype> entry : fields.entrySet()) {
				if(!StringUtils.isBlank(entry.getKey())) {
					results.add(new KeyValue("value", entry.getKey()).set("label", entry.getKey()));
				}
			}
			break;
			case "AttributeList":
			    results.add(new KeyValue("value","_id").set("type", "variable").set("datatype", StringType.NAME).set("label", "Syncari Id (syncariId)" ));
			    results.addAll(schemaService.findEntity(dependantId).map(e-> e.getActiveAttributes().stream()
						.map(a->new KeyValue("value",a.getId()).set("type", "variable").set("datatype", a.getDataType().getName()).set("label", a.getDisplayName() + " (" + a.getApiName() + ")" ))
				.collect(Collectors.toList())).orElse(List.of()));
				// Add picklistGroup property if sendPicklistGroup parameter is true
				if (BooleanUtils.isTrue(sendPicklistGroup)) {
					results.forEach(kv -> kv.set("picklistGroup", "Fields"));
				}
				break;
			case "SynapseEntity":
				results = schemaService.getEntities(dependantId).stream()
						.map(entity -> new KeyValue("value", entity.getId()).set("type", "variable").set("datatype", "string").set("label", entity.getDisplayName()))
						.collect(Collectors.toList());
				break;
			case "findDupesOperator": {
				String dataType = TYPE_EQUIVALENTS.getOrDefault(getDataType(dependantId),"string");
				results = operatorMap.getOrDefault(dataType, operatorMap.get("text"));
				break;
			}
			case "Operator": {
				String dataType = null;
				String decodedDependentId = dependantId;
				try {
					decodedDependentId = URLDecoder.decode(dependantId, "UTF-8");
				} catch (UnsupportedEncodingException e1) {
					log.error("Error while decoding", e1);
					//If decoding fails due to any reason just use dependantId
				}
				if(tokenHelper.hasTokens(decodedDependentId)) {
					dataType = TYPE_EQUIVALENTS.getOrDefault(params.getOrDefault("datatype", "string"),"string");
				} else {
					dataType = TYPE_EQUIVALENTS.getOrDefault(getDataType(dependantId),"string");
				}
				results = operatorMap.getOrDefault(dataType, operatorMap.get("text"));
				break;
			}
			case "abacOperator": {
			  if(dependantId != null) {
			    var abacAttr = abacService.getAttribute(dependantId);
			    if(abacAttr.isPresent() && abacAttr.get().isMultiValued()) {
			      results = List.of(createMultivaluedOperator("in","Contains")
	                    ,createMultivaluedOperator("not_in","Not Contains"));
			      break;
			    }
			  }
			  String dataType = TYPE_EQUIVALENTS.getOrDefault(params.getOrDefault("datatype", "string"),"text");
              results = abacOperatorMap.getOrDefault(dataType, abacOperatorMap.get("text"));
              break;
          }
			case "dfiOperator": {
				String dataType = null;
				String decodedDependentId = dependantId;
				try {
					decodedDependentId = URLDecoder.decode(dependantId, "UTF-8");
				} catch (UnsupportedEncodingException e1) {
					log.error("Error while decoding", e1);
					//If decoding fails due to any reason just use dependantId
				}
				if(tokenHelper.hasTokens(decodedDependentId)) {
					dataType = TYPE_EQUIVALENTS.getOrDefault(params.getOrDefault("datatype", "string"),"string");
				} else {
					dataType = TYPE_EQUIVALENTS.getOrDefault(getDataType(dependantId),"string");
				}
				results.addAll(operatorMap.getOrDefault(dataType, operatorMap.get("text")));
				results.addAll(dfiAdditionalOperatorMap.getOrDefault(dataType, List.of()));
				if (!TokenHelper.hasTokens(URLDecoder.decode(dependantId))) {
					results.add(createOperator("is_unique", "Is Unique").set("unary", true));
				}
				break;
			}
			case "winnerSelectionOperator": {
				if("record".equals(dependantId)){
					results = Arrays.asList(RecordLevelWinnerSelection.values()).stream().map(v-> createOperator(v.name().toLowerCase(), i18n(v.name().toLowerCase()),true)).collect(Collectors.toList());
				}else {
					String dataType = TYPE_EQUIVALENTS.getOrDefault(getDataType(dependantId), "string");
					results = Arrays.asList(FieldLevelWinnerSelection.values()).stream().map(v-> createOperator(v.name().toLowerCase(), i18n(v.name().toLowerCase()),true)).collect(Collectors.toList());
					results.add(createMultivaluedOperator("firstMatchingValue",i18n("operator_first_matching_value")));
					results.addAll(operatorMap.getOrDefault(dataType, operatorMap.get("text")));
				}
				break;
			}
			case "findDupesValue": {
				AttributeDefinition attribute = schemaService.getAttribute(dependantId);
				results =schemaService.getEntity(attribute.getEntityId()).getActiveAttributes().stream()
						.map(a->new KeyValue("value",a.getId()).set("type", "variable").set("datatype", a.getDataType().getName()).set("label","Incoming " + a.getDisplayName() + " (" + a.getApiName() + ")"))
						.collect(Collectors.toList());
				break;
			}
			case "FilterDupeOperator": {
				if(dependantId.equalsIgnoreCase("dup_count") || dependantId.equalsIgnoreCase("empty_fields")){
					results = operatorMap.get("number");
				}else {
					String dataType = TYPE_EQUIVALENTS.getOrDefault(getDataType(dependantId),"string");
					results = operatorMap.getOrDefault(dataType, operatorMap.get("text"));

				}
				break;
			}
			case "FindInListOperator": {
				if(dependantId.equalsIgnoreCase("syncari_findInList_ValueInList")){
					results = operatorMap.get("text");
				}else {
					// For position show all number operators
					results = operatorMap1.get("index");
				}
				break;
			}
			case "WinnerOperator": {
				if(dependantId.equalsIgnoreCase("record_props")){
					results = List.of(
							new KeyValue("value","latest").set("label","Is Latest").set("isUnary",true),
							new KeyValue("value","earliest").set("label","Is Earliest").set("isUnary",true),
							new KeyValue("value","mostComplete").set("label","Is Most Complete").set("isUnary",true),
							new KeyValue("value","leastComplete").set("label","Is Least Complete").set("isUnary",true)
					);
				}else if(dependantId.equalsIgnoreCase("synapse")){
					results = List.of(
							new KeyValue("value","eq").set("label","Is")
					);
				}else {
					String dataType = TYPE_EQUIVALENTS.getOrDefault(getDataType(dependantId),"string");
					results = new ArrayList<>(operatorMap.getOrDefault(dataType, operatorMap.get("text")));
					results.add(new KeyValue("value","max").set("label","Has Highest Value").set("isUnary",true));
					results.add(new KeyValue("value","min").set("label","Has Lowest Value").set("isUnary",true));
					results.add(new KeyValue("value","firstMatching").set("label","First Matching").set("valueType","tags"));
				}
				break;
			}
			case "WinningValueOperator": {

					results = List.of(
							new KeyValue("value","mostCommon").set("label","Most Common").set("isUnary",true),
							new KeyValue("value","leastCommon").set("label","Least Common").set("isUnary",true),
							new KeyValue("value","minValue").set("label","Smallest Value").set("isUnary",true),
							new KeyValue("value","maxValue").set("label","Largest Value").set("isUnary",true),
							new KeyValue("value","earliest").set("label","Value from Earliest Record").set("isUnary",true),
							new KeyValue("value","latest").set("label","Value from Latest Record").set("isUnary",true),
							new KeyValue("value","explicitValue").set("label","Set New Value")
					);
				break;
			}
			case "Entity.User":
				results = entityRepo.search("user",new SearchCriteria().and("UserType","Standard"), Pageable.unpaged()).map(u ->
								new KeyValue("value",u.getSyncariEntityId()).set("label",String.format("%s %s (%s)",u.getValues().get("FirstName"),u.getValues().get("LastName"),u.getValues().get("Username")))
						).getContent();

				break;
			case "SFDCTaskPriorities":
				results = getPicklistValues(schemaService.getSchemaFor(dependantId), "Priority");

				break;
			case "SFDCTaskStatuses":
				results = getPicklistValues(schemaService.getSchemaFor(dependantId), "Status");
				break;
			case "WinnerStrategy":
			case "DataAuthorityStrategy":
				results =connectorService.list().stream().map(c -> new KeyValue("value",c.getId()).set("label",c.getName()))
						.collect(Collectors.toList());
				break;
			case "Contact.LookupFields":
				results.addAll(getEnrichFields(dependantId, "contact", false));
				break;

			case "Company.LookupFields":
				results.addAll(getEnrichFields(dependantId, "company", false));
				break;
			case "Contact.EnrichUsing":
				results.addAll(getEnrichFields(dependantId, "contact", true));
				break;
			case "Company.EnrichUsing":
				results.addAll(getEnrichFields(dependantId, "company", true));
				break;
			case "DirectionSelectionType":
			    results.addAll(getDirectionPicklistValues(dependantId));
			    break;
			case "SimilarWebMetrics":
				results.addAll(getSimilarWebMetrics(dependantId));
				break;
			case "fieldMergeOperator": {
				String dataType = TYPE_EQUIVALENTS.getOrDefault(getDataType(dependantId), "string");
				results = Arrays.asList(WinnerValueSelectionPolicy.values()).stream().filter(v -> !((v.name().equalsIgnoreCase("least_frequent"))|| (v.name().equalsIgnoreCase("most_frequent"))))
						.map(v-> createOperatorWithRetain(v.name().toLowerCase(), i18n("merge_operator_"+v.name().toLowerCase()),false,"fieldmergepolicyretainfield")).collect(Collectors.toList());
				results.add(createMultivaluedOperatorWithRenderType("firstMatchingValue",i18n("merge_operator_first_matching_value")));
				results.add(createMultivaluedWithPicklistOperator("firstNotMatchingValue",i18n("first_not_matching")));
				results.add(createMultivaluedOperatorWithRenderType("firstMatchingValueIgnoreCase",i18n("first_matching_value_ignore_case")));
				results.add(createMultivaluedWithPicklistOperator("firstNotMatchingValueIgnoreCase",i18n("first_not_matching_value_ignore_case")));
				results.add(createOperator("sum",i18n("merge_operator_sum"),true));
				results.add(createOperator("concat",i18n("merge_operator_concat")));
				results.add(createOperator("setValue",i18n("merge_operator_setvalue")));
				results.addAll(Arrays.stream(WinnerValueSelectionPolicy.values()).filter(v -> ((v.name().equalsIgnoreCase("least_frequent"))|| (v.name().equalsIgnoreCase("most_frequent"))))
						.map(v-> createOperator(v.name().toLowerCase(), i18n("merge_operator_"+v.name().toLowerCase()),true)).collect(Collectors.toList()));
				results.add(createOperatorWithRetain("oldest_created_with_value",i18n("merge_operator_oldest_created"),false,"fieldmergepolicyretainfield"));
				results.add(createOperatorWithRetain("latest_created_with_value",i18n("merge_operator_latest_created"),false,"fieldmergepolicyretainfield"));
				break;
			}
			case "fieldMergePicklistSortField": {
				AttributeDefinition def = schemaService.getAttribute(dependantId);
				String entityId = def.getEntityId();
				results = schemaService.findEntity(entityId).map(e-> e.getActiveAttributes().stream()
						.map(a->new KeyValue("value",a.getId()).set("type", "variable").set("datatype", a.getDataType().getName()).set("label",a.getDisplayName()))
						.collect(Collectors.toList())).orElse(List.of());
				break;
			}
			case "fieldMergePicklistSortDirection": {
				results.add(KeyValue.of("label","Ascending", "value", "ascending"));
				results.add(KeyValue.of("label","Descending", "value", "descending"));
			}
			break;
			case "fieldMergePicklist":
				String dType = TYPE_EQUIVALENTS.getOrDefault(getDataType(dependantId), "string");
				String operator = params.getOrDefault("operator", "firstNotMatchingValue");
				if (operator.equalsIgnoreCase("firstNotMatchingValue")){
					results.add(createOperator("oldest_created_with_value",i18n("merge_operator_oldest_created"),true));
					results.add(createOperator("most_recently_created_with_value",i18n("merge_operator_latest_created"),true));
					results.add(createOperator("oldest_updated_with_value",i18n("merge_operator_earliest_with_value"),true));
					results.add(createOperator("most_recently_updated_with_value",i18n("merge_operator_latest_with_value"),true));
				}
				break;
			case "DedupeQuickStartEntity": {
				results.addAll(getQsDedupEntities(dependantId));
				break;
			}
			//Supported operations on values in Update Record/Create Record functions
			case "Operation": {
				final Optional<AttributeDefinition> attribute = schemaService.getActiveAttribute(dependantId);
				boolean isMultivalued = attribute.map(a->a.isMultiValueField()).orElse(false);
				if (isMultivalued) {
					results = multivaluedFieldOperations;
				}else {
					String dataType = TYPE_EQUIVALENTS.getOrDefault(attribute.map(	a->a.getDataType().getName()).orElse(StringType.NAME),StringType.NAME);
					results = operationMap.getOrDefault(dataType, operationMap.get("text"));
				}
				break;
			}
			case "SourceList": {
				final String graphVersion = params.getOrDefault("graphVersion", DraftStatus.NEW.name()).toUpperCase();
				final String currentEntityDefinition = params.get("entityDefinition");
				final DraftStatus status = "DRAFT".equals(graphVersion) ? DraftStatus.NEW :DraftStatus.valueOf(graphVersion);
				Optional<MappingGraph> graph =  graphService.retrieveEntityGraph(dependantId, status);
				final Map<String, Connector> connectorMap = connectorService.getAllActive().stream().collect(Collectors.toMap(c -> c.getId(), c -> c));
				results = graph.stream().flatMap(g -> g.getSources()
						//extract entitydefinition from nodeconfig
						.map(s -> {
							EntitySourceNodeConfig sourceConfig = s.getTypedConfiguration();
							return sourceConfig.getEntityDefinition();
						})
						//skip currently selected entity as a source, because this is implicit
						.filter(e -> !e.getId().equals(currentEntityDefinition))
						//create label/value pairs for all other sources
						.map(entityDefinition -> {
							final String connetorName = connectorMap.getOrDefault(entityDefinition.getConnectorId(), new Connector("No Connector", "", "")).getName();
							return new KeyValue("label", String.format("%s/%s", connetorName, entityDefinition.getDisplayName()), "value", entityDefinition.getId());
						})).collect(Collectors.toList());
				break;
			}


		default:
			break;
		}
		List<KeyValue> sorted = new ArrayList<>(results);
		sorted.sort(Comparator.comparing(a -> a.get("label").toString()));
    	return sorted;
    }

	private List<KeyValue> getSimilarWebMetrics(String apiCategory) {
		return SimilarWebAPICategory.valueOf(apiCategory).getAvailableMetrics().stream().map(a->new KeyValue("label",i18n(a.getDisplayNameKey())).set("value",a.name())).collect(Collectors.toList());
	}

	private List<KeyValue> getDirectionPicklistValues(String entityId) {
	    EntityDefinition entity = schemaService.getEntity(entityId);
	    Connector connector = connectorService.find(entity.getConnectorId()).get();
	    SynapseInfoService synapseService = factory.getSynapseService(connector.getMetadata());
        List<KeyValue> directionValues = new ArrayList<>();
        if (synapseService.isSource()) {
            directionValues.add(new KeyValue().set("value", MappingNodeType.ENTITY_SOURCE.name())
                    .set("label", "Source").set("icon", "/icons/pull.png"));
        }
        if (synapseService.isSink() && !entity.isReadOnly()) {
            directionValues.add(new KeyValue().set("value", MappingNodeType.ENTITY_SINK.name())
                    .set("label", "Destination").set("icon", "/icons/push.png"));
        }
        return directionValues;
    }

	private String getDataType(@PathVariable String dependantId) {
		if(dependantId.startsWith("output_")){
			if(dependantId.endsWith(".x.lookupResult")){
				return ObjectType.VALUE.getName();
			}else if(dependantId.endsWith(".x.lookupCount")){
				return IntegerType.VALUE.getName();
			}else {
				String nodeId = dependantId.replace("output_", "").replace(".x.typedValue", "");
				var node = graphService.findNode(nodeId);
				if(node.isPresent()){
					var graph = graphService.retrieveWithoutLayout(node.get().getMappingGraphId());
					if(graph.isPresent()) {
						NodeInfoContext context = new NodeInfoContext().setCurrentNode(node.get()).setPipeline(graph.get());
						return node.get().getConfiguration().inferOutputDataType(nodeInfoFactory, context);
					}
				}
				return StringType.VALUE.getName();
			}
		}else if("incoming_change".equals(dependantId)) {
			return "exact_match";
		}else if(!StringUtils.isBlank(dependantId) && dependantId.equalsIgnoreCase("datastudio_isDeleted")) {
		    return "boolean";
		}else if(!StringUtils.isBlank(dependantId) && dependantId.startsWith("datastudio")) {
		    return "id";
		}else if(!StringUtils.isBlank(dependantId) && dependantId.startsWith("rule")) {
            return "integer";
        }else if(dependantId.startsWith("syncari_dataset_")) {
			// "syncari_dataset_<datasetid|fieldqfaliasname>
			String otherPart = dependantId.substring("syncari_dataset_".length());
			if (StringUtils.isNotEmpty(otherPart)){
				String [] splittedArray = otherPart.split("syncari_apiname_");
				Optional<Dataset> ds = datasetService.findDataset(splittedArray[0]);
				return ds.map(d -> {
					String aliasName = splittedArray[1];
					DatasetConfig config = d.getDatasetConfig();
					List<Projection> projections = config.getProjectionsList();
					List<Projection> proj = projections.stream().filter(p -> p.getFunction().getAlias().equalsIgnoreCase(aliasName)).collect(Collectors.toList());
					return proj.stream().findFirst().map(p -> p.getDataType()).orElse(StringType.NAME);
				}).orElse(StringType.NAME);
			}
			return StringType.NAME;
		}else{
			return schemaService.getActiveAttribute(dependantId).map(a->a.getDataType().getName()).orElse(StringType.NAME);
		}
	}

	private List<KeyValue> getPicklistValues(Schema schemaFor, String fieldName) {
		return schemaFor
				.findEntityByName("Task")
				.flatMap(taskEntity -> taskEntity.getField(fieldName)
						.map(f -> f.getValues()))
				.orElse(Collections.emptyList()).stream().map(picklistValue ->
						new KeyValue("value", picklistValue).set("label", picklistValue)
				).collect(Collectors.toList());
	}

	private List<KeyValue> getEnrichFields(String serviceId, String entity, boolean isInput){
		List<KeyValue> results = new ArrayList<>();
		Map<String, String> map;
		Optional<Connector> connector = connectorService.find(serviceId);
		if(!connector.isEmpty()){
			Connector enrichConnector = connector.get();
			LookupService service = dataServiceFactory.getLookupService(enrichConnector.getMetadata());
			var connectorInfo = transformer.toConnectorInfo(enrichConnector);
			map = isInput ? service.getInputFields(connectorInfo, entity)
					: service.getOutputFields(connectorInfo, entity);
		}else{
			map = isInput ? clearbitService.getInputFields(entity)
					: clearbitService.getOutputFields(entity);
		}
		for (Entry<String, String> entry : map.entrySet()) {
			results.add(new KeyValue("value", entry.getKey()).set("label", entry.getValue()));
		}
		return results;
	}

	private List<KeyValue> getQsDedupEntities(String synapseId) {
		List<KeyValue> entities = new ArrayList<>();
		Connector conn = connectorService.find(synapseId).get();
		var synapseMetadataName = conn.getMetadata().getName();
		var supportedEntities = new DedupeQuickStartSeed().getSupportedEntities();
		if (!supportedEntities.containsKey(synapseMetadataName)) {
			throw new RuntimeException(String.format("%s is not supported", synapseMetadataName));
		}

		((List<String>) supportedEntities.get(synapseMetadataName)).forEach((entityName) -> {
			EntityDefinition synapseEntity = schemaService.getEntityByName(synapseId, entityName).get();
			entities.add(new KeyValue(
					"label", synapseEntity.getDisplayName(),
					"value", synapseEntity.getId()
			));
		});
		return entities;
	}
}
