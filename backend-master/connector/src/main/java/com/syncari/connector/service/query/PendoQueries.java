package com.syncari.connector.service.query;

public class PendoQueries {
    public static final String PENDO_GET_BY_WATERMARK_REQ = "{" +
            "    \"response\": {" +
            "        \"mimeType\": \"application/json\"" +
            "    }," +
            "    \"request\": {" +
            "        \"pipeline\": [" +
            "            {" +
            "                \"source\": {" +
            "                    \"%s\": null" +
            "                }" +
            "            }," +
            "            {" +
            "                \"filter\": \"%s > %s && %s < %s\"" +
            "            }," +
            "            {" +
            "                \"sort\": [" +
            "                    \"%s\"" +
            "                ]" +
            "            }," +
            "            {" +
            "                \"limit\": %s" +
            "            }" +
            "        ]" +
            "    }" +
            "}";

    public static final String PENDO_VISITOR_RAW_GET_BY_WATERMARK_REQ = "{\n" +
            "    \"response\": {\n" +
            "        \"mimeType\": \"application/json\"\n" +
            "    },\n" +
            "    \"request\": {\n" +
            "        \"pipeline\": [\n" +
            "            {\n" +
            "                \"source\": {\n" +
            "                    \"visitors\": {\n" +
            "                        \"since\": \"%s\"\n" +
            "                    }\n" +
            "                }\n" +
            "            },\n" +
            "            {\n" +
            "                \"filter\": \"%s > %s && %s < %s\"\n" +
            "            },\n" +
            "            {\n" +
            "                \"sort\": [\n" +
            "                    \"%s\"\n" +
            "                ]\n" +
            "            }\n" +
            "        ]\n" +
            "    }\n" +
            "}";

    public static final String PENDO_GET_NPS_BY_ID_REQ = "{\n" +
            "    \"response\": {\n" +
            "        \"mimeType\": \"application/json\"\n" +
            "    },\n" +
            "    \"request\": {\n" +
            "        \"name\": \"nps\",\n" +
            "        \"pipeline\": [\n" +
            "            {\n" +
            "                \"source\": {\n" +
            "                    \"pollsSeenEver\": null\n" +
            "                }\n" +
            "            },\n" +
            "            {\n" +
            "                \"filter\": \"time == %s\"\n" +
            "            }\n" +
            "        ],\n" +
            "        \"requestId\": \"nps\"\n" +
            "    }\n" +
            "}";
}
