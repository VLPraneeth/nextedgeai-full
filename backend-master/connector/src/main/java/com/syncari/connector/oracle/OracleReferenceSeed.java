package com.syncari.connector.oracle;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.experimental.Accessors;

import java.util.HashMap;
import java.util.Map;

public class OracleReferenceSeed {

    /*
    Map of given entity fieldName -> (EntityName, TargetFieldName)
     */
    public static Map<String, OracleRefObject> getReferenceMappings(String entityApiName) {
        switch (entityApiName.toLowerCase()) {
            case "contacts":
                return getContactReferenceFields();
            case "partnercontacts":
                return getPartnerContactReferenceFields();
            case "leads":
                return getLeadReferenceFields();
            default:
                break;
        }
        return Map.of();
    }

    public static Map<String, OracleRefObject> getContactReferenceFields(){
        Map<String, OracleRefObject> referenceFieldsMap = new HashMap<>();
        referenceFieldsMap.put("AccountPartyNumber", new OracleRefObject("accounts", "PartyNumber"));
        return referenceFieldsMap;
    }

    public static Map<String, OracleRefObject> getPartnerContactReferenceFields(){
        Map<String, OracleRefObject> referenceFieldsMap = new HashMap<>();
        referenceFieldsMap.put("PartnerCompanyNumber", new OracleRefObject("partners", "CompanyNumber"));
        return referenceFieldsMap;
    }

    public static Map<String, OracleRefObject> getLeadReferenceFields(){
        Map<String, OracleRefObject> referenceFieldsMap = new HashMap<>();
        referenceFieldsMap.put("PrimaryContactId", new OracleRefObject("partnerContacts", "PartyNumber"));
        referenceFieldsMap.put("PartnerCompanyNumber", new OracleRefObject("partners", "CompanyNumber"));
        return referenceFieldsMap;
    }
}

@Data
@Accessors(chain = true)
@AllArgsConstructor
class OracleRefObject{
    private String entityName;
    private String targetFieldId;
}
