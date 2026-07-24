package com.syncari.connector.intacct;

import java.util.Map;
import java.util.Set;

public class IntacctSeed {

    private static final String RECORDNO = "RECORDNO";

    private static final Set<String> UNSUPPORTED_ENTITIES = Set.of(
            "SODOCUMENTSUBTOTALS",
            "GENINVOICEPREBILLLINE",
            "GENINVOICEPREVIEWLINE",
            "BANKACCTTXNFEED",

            // Sales Tax, VAT, and GST
            "TAXGROUP"
    );

    public static boolean IS_ENTITY_UNSUPPORTED(String schemaName){
        return schemaName.endsWith("DOCUMENT")
                || schemaName.endsWith("DOCUMENTENTRY")
                || IntacctSeed.UNSUPPORTED_ENTITIES.contains(schemaName);
    }

    public static boolean IS_WRITE_ENABLED(String schemaName){
        return READ_WRITE_ENTITIES.contains(schemaName);
    }

    public static final Set<String> READ_WRITE_ENTITIES = Set.of(
            // Company & Console
            "CLASS", "CONTACT", "DEPARTMENT", "LOCATION",

            // GENERAL LEDGER
            "GLACCTALLOCATIONGRP", "GLACCTALLOCATION", "GLACCTALLOCATIONRUN", "GLACCTGRPPURPOSE", "GLACCTGRP",
            "GLACCOUNT", "GLBUDGETHEADER", "GLBATCH", "RECURGLACCTALLOCATION", "REPORTINGPERIOD", "STATACCOUNT",
            "ALLOCATION",

            // CASH MANAGEMENT
            "ACHBANK", "BANKACCTTXNFEED", "BANKACCTRECON",

            // ACCOUNTS PAYABLE
            "APACCOUNTLABEL", "APADJUSTMENT", "APPYMT", "APBILL", "PROVIDERBANKACCOUNT", "PROVIDERVENDOR", "VENDOR",

            // ACCOUNTS RECEIVABLE
            "ARACCOUNTLABEL", "ARADJUSTMENT", "ARADVANCE", "ARPYMT", "CUSTOMER", "DUNNINGDEFINITION", "ARINVOICE",

            // EMPLOYEE EXPENSES
            "EMPLOYEE", "EXPENSEADJUSTMENTS", "EEXPENSES", "EEACCOUNTLABEL",

            // PURCHASING
            "PODOCUMENTPARAMS",

            // ORDER ENTRY
            "SODOCUMENT", "SOPRICELIST",

            // INVENTORY CONTROL
            "BINFACE", "BINSIZE", "BIN", "COGSCLOSEDJE", "ICCYCLECOUNT", "ICCYCLECOUNTENTRY", "INVDOCUMENT", "InventoryWQDetail",
            "InventoryWQOrder", "ITEMCROSSREF", "ITEM", "ICTRANSFERITEM", "WAREHOUSE", "ZONE",

            // PROJECT & RESOURCE MANAGEMENT
            "OBSPCTCOMPLETED", "PROJECT", "TASK","TIMESHEET",

            // CONSOLIDATIONS
            "GCBOOK", "GCBOOKELIMACCOUNT", "GCBOOKENTITY", "GCBOOKADJJOURNAL", "GCBOOKACCTRATETYPE",

            // CONSTRUCTION
            "ACCUMULATIONTYPE", "APRETAINAGERELEASE", "ARRETAINAGERELEASE", "CHANGEREQUESTSTATUS", "CHANGEREQUESTTYPE",
            "CHANGEREQUEST", "COSTTYPE", "EMPLOYEEPOSITION", "LABORCLASS", "LABORSHIFT", "LABORUNION", "PROJECTCHANGEORDER",
            "PROJECTCONTRACTTYPE", "PROJECTCONTRACT", "PROJECTCONTRACTLINE", "PROJECTCONTRACTLINEENTRY","PJESTIMATETYPE",
            "PJESTIMATE", "PJESTIMATEENTRY","RATETABLE", "STANDARDCOSTTYPE", "STANDARDTASK",

            // CONTRACTS & REVENUE MANAGEMENT
            "CONTRACTPRICELIST", "CONTRACTITEMPRICELIST", "CONTRACTBILLINGTEMPLATE", "CONTRACTEXPENSETEMPLATE", "CONTRACTEXPENSE",
            "GENINVOICEPOLICY", "GENINVOICEPREVIEW", "GENINVOICERUN", "CONTRACTDETAIL", "CONTRACTREVENUETEMPLATE",
            "CONTRACTTYPE", "CONTRACT", "CONTRACTMEABUNDLE", "CONTRACTUSAGE",


            // Sales Tax, VAT, and GST
            "TAXRECORD"
    );

    public static final Set<String> SKIP_PRIMARY_KEY_REFERENCE = Set.of("MAILADDRESS", "TERRITORY", "SHIPMETHOD");

    //References here are mostly not compatible with Syncari data structure
    public static final Map<String, Set<String>> SKIP_RELATED_BY_KEY_REFERENCE = Map.of(
            "CUSTOMER", Set.of(
                    "SHIPTO.CONTACTNAME",
                    "BILLTO.CONTACTNAME",
                    "DISPLAYCONTACT.CONTACTNAME",
                    "CONTACTINFO.CONTACTNAME",
                    "ACCOUNTLABEL",
                    "ARACCOUNT",
                    "TERMNAME",
                    "GLGROUP",
                    "SHIPPINGMETHOD",
                    "OFFSETGLACCOUNTNO",
                    "MEGAENTITYID",
                    "RCLASS",
                    "RDEPARTMENT"
            )
    );

    public static final Map<String, Set<String>> REQUIRED_FIELDS_BY_ENTITY = Map.of(
            "CUSTOMER", Set.of("NAME"),
            "CONTRACT", Set.of(
                    "CUSTOMERID",
                    "NAME",
                    "LOCATIONID",
                    "BEGINDATE",
                    "ENDDATE",
                    "TERMNAME"),
            "ITEM", Set.of(
                    "ITEMID",
                    "NAME"
            )
    );


    public static final Map<String, String> ENTITY_PRIMARY_KEY_MAP = Map.ofEntries(
            // Company & Console
            Map.entry("CLASSGROUP", "NAME"),
            Map.entry("CLASS", "CLASSID"),
            Map.entry("CONTACT", "CONTACTNAME"),
            Map.entry("DEPARTMENT", "DEPARTMENTID"),
            Map.entry("LOCATIONENTITY", "LOCATIONID"),
            Map.entry("LOCATION", "LOCATIONID"),
            Map.entry("USERINFO", "LOGINID"),

            // GENERAL LEDGER
            Map.entry("GLACCOUNT", "ACCOUNTNO"),
            Map.entry("ALLOCATION", "ALLOCATIONID"),
            Map.entry("REPORTINGPERIOD", "NAME"),
            Map.entry("CUSTGLGROUP", "NAME"),
            Map.entry("CUSTTYPE", "NAME"),
            Map.entry("EMPLOYEEPOSITION", "POSITIONID"),

            // CASH MANAGEMENT

            // ACCOUNTS PAYABLE
            Map.entry("APACCOUNTLABEL", "ACCOUNTLABEL"),
            Map.entry("APTERM", "NAME"),
            Map.entry("VENDOR", "VENDORID"),

            // ACCOUNTS RECEIVABLE
            Map.entry("ARACCOUNTLABEL", "ACCOUNTLABEL"),
            Map.entry("ARTERM", "NAME"),
            Map.entry("CUSTOMER", "CUSTOMERID"),

            // EMPLOYEE EXPENSES
            Map.entry("EMPLOYEE", "EMPLOYEEID"),

            // PURCHASING

            // ORDER ENTRY
            Map.entry("SODOCUMENTPARAMS", "DOCID"),


            // INVENTORY CONTROL
            Map.entry("BIN", "BINID"),
            Map.entry("PRODUCTLINE", "PRODUCTLINEID"),

            // PROJECT & RESOURCE MANAGEMENT
            Map.entry("PROJECT", "PROJECTID"),

            // CONSOLIDATIONS

            // CONSTRUCTION

            // CONTRACTS & REVENUE MANAGEMENT
            Map.entry("CONTRACTBILLINGTEMPLATE", "NAME"),
            Map.entry("CONTRACTEXPENSETEMPLATE", "NAME"),
            Map.entry("CONTRACTREVENUETEMPLATE", "NAME")

            // Sales Tax, VAT, and GST
    );

}
