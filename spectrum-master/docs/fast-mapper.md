# Fast Mapper
## TOC
### 1.) [Synapse List](#synapse-list)
### 2.) [Synapse Unmapped Entity Fields](#synapse-unmapped-entity-fields)
### 3.) [Get the synapse fields](#get-the-synapse-fields)
### 4.) [Get Schema](#get-schema)
### 5.) [Get Mapping](#get-mapping)
### 6.) [Get synapse entities](#get-synapse-entities)
### 7.) [Save Mapping](#save-mapping)

# Synapse List
### Use Existing /api/v1/connector/ and /api/v1/connector/describe/

# Synapse Unmapped Entity Fields
## [GET] /api/v1/studio/schema/unmappedConnectorFields/{connectorId}
### Return unmapped entity fields
### This could be slow if we wanted this to do a describe to the endpoint? Use persisted schema if available?

### Response Payload:
```JSON
[{
  "id": "12345",
  "apiName": "account",
  "displayName": "Account",
  "fields": [{
    "id": "12345",
    "dataType": "string",
    "apiName": "Name",
    "displayName": "Account Name",
    "allowedDirections": ["syncFrom", "syncTo"]
  }]
}]
```

# Get Schema
### Use Existing /api/v1/schema

# Get Mapping
## [GET] /api/v1/pipeline/mapping
## [GET] /api/v1/pipeline/mapping/{entityId}
### Request Payload:
### Phase2
```JSON
[{
  "id": "abcedf",
  "synapseId": "12345",
  "synapseFieldId": "67890",
  "directions": ["syncFrom", "syncTo"],
  "syncariFieldId": "b12345"
}]
```

# Get synapse entities
# Leverage the existing api with additional query parameters
## [GET] /api/v1/schema/synapseEntity/{synapseId}
```JSON
[{
  "id": "abcdef",
  "apiName": "account",
  "displayName": "Account",
}]
```

# Get the synapse fields
# Leverage the existing api with additional query parameters
## [GET] /api/v1/schema/synapseFields/{synapseId}/{entityId}
### Return all the fields that available in the synapse
### Request Payload:
```JSON
[{
  "id": "abcdef",
  "apiName": "accountName",
  "displayName": "Account Name",
  "datatype": "string"
}]
```

# Save Mapping
## [POST] /api/v1/pipeline/mapping
### Request Payload:
```JSON
[{
  "id": "abcedf",
  "synapseId": "12345",
  "synapseFieldId": "67890",
  "directions": ["syncFrom", "syncTo"],
  "syncariFieldId": "b12345",
},{
  "id": "abcedg",
  "synapseId": "12345",
  "synapseFieldId": "67890",
  "directions": ["syncFrom", "syncTo"],
  "createNewSyncariField": true,      //ph3
  "syncariFieldApiName": "firstName", //ph3
},{
  "id": "abcedg",
  "synapseId": "12345",
  "synapseFieldId": "67891",
  "directions": ["syncFrom", "syncTo"],
  "syncariFieldApiName": "firstName", //ph 3
}]
```

### Success Payload:
```JSON
[{
  "id": "abcedf",
  "synapseId": "12345",
  "synapseFieldId": "67890",
  "directions": ["syncFrom", "syncTo"],
  "syncariFieldId": "b12345"
}]
```

### Error Response Payload 1
### Example error for an attribute in the mapping entry
```JSON
{
  "error": [{
    "id": "abcedf",
    "directions": "bidirectional is not supported",
  }]
}
```

### Error Response Payload 2
### Example error for the mapping entry
```JSON
{
  "error": [{
    "id": "abcedf1",
    "errorMessage": "This field is already mapped",
  }]
}
```

# Delete Mappings
## [POST] /api/v1/pipeline/{entityId/}/deleteMapping
### Request Payload:
```JSON
[{
  "id": "67890_b12345",
  "synapseFieldId": "67890",
  "syncariFieldId": "b12345",
},{
  "id": "67890_b6789",
  "synapseFieldId": "67890",
  "syncariFieldId": "b6789",
}]
```

### Success Payload:
```JSON
{
  "success": true,
}
```

### Success Payload:
```JSON
{
  "error": [{
    "id": "67890_b12345",
    "errorMessage": "mapping not found"
  }]
}
```
