# Test Pipeline v2
## TOC
### 1.) [Create Test](#create-test)
### 2.) [Input Expected Result Picklist](#input-expected-result-picklist)
### 3.) [Update Test](#update-test)
### 4.) [Get Test](#get-test)
### 5.) [List Tests](#list-tests)
### 6.) [Run Tests](#run-tests)
### 7.) [List Test Runs](#list-test-runs)
### 8.) [Get Test Run](#get-test-run)

# Create Test
## [POST] /api/v1/test/entityPipeline/:entityPipelineId
## [POST] /api/v1/test/fieldPipeline/:fieldPipelineId

### Payload:
```JSON
  {
    "displayName": "Phone Number format test",
    "description": "Test the phone number fields with some random format",
    "tags": ["tag1", "tag2"],
    "testData": {
      "input": [{
        "nodeId": "9452ac2d-bea8-45bd-bd1d-3c1d66b032e5",
        "apiName": "phoneNumber",
        "value": "555-555-5555"
      },{
        "nodeId": "f3b02afc-5466-4ca3-ab37-1f663580024a",
        "apiName": "phone_number",
        "value": "abc5555"
      }],
      "expectedResult": [{
        "apiName": "phoneNumber",
        "nodeId": "d1e13b33-abe7-4bdd-98dc-f2d28370779c",
        "value": "(555) 555-5555"
      },{
        "nodeId": "f3b02afc-5466-4ca3-ab37-1f663580024a",
        "apiName": "phone_number",
        "value": "abc5555"
      }]
    }
  }
```

### Response:
```JSON
  {
    "id": "9f99e344-371d-4959-a8ba-bd93b6e3012a",
    "displayName": "Phone Number format test",
    "description": "Test the phone number fields with some random format",
    "tags": ["tag1", "tag2"],
    "createdAt": "12/12/12 16:01:01",
    "updatedAt": "12/12/12 16:01:01",
    "ownerFirstName": "Sharon",
    "ownerLastName": "Rivers",
    "ownerEmail": "sharon.rivers@acme.com",
    "testData": {
      "input": [{
        "nodeId": "9452ac2d-bea8-45bd-bd1d-3c1d66b032e5",
        "apiName": "phoneNumber",
        "displayName": "Phone Number",
        "value": "555-555-5555"
      },{
        "nodeId": "f3b02afc-5466-4ca3-ab37-1f663580024a",
        "apiName": "phone_number",
        "displayName": "Phone Number",
        "value": "abc5555"
      }],
      "expectedResult": [{
        "nodeId": "d1e13b33-abe7-4bdd-98dc-f2d28370779c",
        "apiName": "phoneNumber",
        "displayName": "Phone Number",
        "value": "(555) 555-5555"
      },{
        "nodeId": "f3b02afc-5466-4ca3-ab37-1f663580024a",
        "apiName": "phone_number",
        "displayName": "Phone Number",
        "value": "abc5555"
      }]
    }
  }
```

# Input Expected Result Picklist
## [PICKLIST] /api/v1/test/entityPipeline/:entityPipelineId/nodeId/:nodeId/fields/picklistValues
## [PICKLIST] /api/v1/test/fieldPipeline/:fieldPipelineId/nodeId/:nodeId/fields/picklistValues

### Response:
```JSON
[{
  "datatype": "picklist",
  "renderType": "autocomplete", // optional. if you wanted to use a different input type that was default mapped to datatype
  "label": "Account Source",
  "value": "5f98703ad13cb43e9bfcfa16"
}];
```

# Update Test
## [PUT] /api/v1/test/entityPipeline/:entityPipelineId/testId/:testId
## [PUT] /api/v1/test/fieldPipeline/:fieldPipelineId/testId/:testId

### Payload:
```JSON
  {
    "displayName": "Phone Number format test",
    "description": "Test the phone number fields with some random format",
    "tags": ["tag1", "tag2"],
    "testData": {
      "input": [{
        "nodeId": "9452ac2d-bea8-45bd-bd1d-3c1d66b032e5",
        "apiName": "phoneNumber",
        "dataType": "string",
        "value": "555-555-5555"
      },{
        "nodeId": "f3b02afc-5466-4ca3-ab37-1f663580024a",
        "apiName": "phone_number",
        "dataType": "string",
        "value": "abc5555"
      }],
      "expectedResult": [{
        "nodeId": "d1e13b33-abe7-4bdd-98dc-f2d28370779c",
        "apiName": "phoneNumber",
        "dataType": "string",
        "value": "(555) 555-5555"
      },{
        "nodeId": "f3b02afc-5466-4ca3-ab37-1f663580024a",
        "apiName": "phone_number",
        "dataType": "string",
        "value": "abc5555"
      }]
    }
  }
```

### Response:
```JSON
  {
    "id": "2abe40c2c9e58186d7405e3e",
    "displayName": "Phone Number format test",
    "description": "Test the phone number fields with some random format",
    "tags": ["tag1", "tag2"],
    "createdAt": "12/12/12 16:01:01",
    "updatedAt": "12/12/12 17:11:21",
    "ownerFirstName": "Sharon",
    "ownerLastName": "Rivers",
    "ownerEmail": "sharon.rivers@acme.com",
    "testData": {
      "input": [{
        "nodeId": "9452ac2d-bea8-45bd-bd1d-3c1d66b032e5",
        "apiName": "phoneNumber",
        "displayName": "Phone Number",
        "dataType": "string",
        "value": "555-555-5555"
      },{
        "nodeId": "f3b02afc-5466-4ca3-ab37-1f663580024a",
        "apiName": "phone_number",
        "displayName": "Phone Number",
        "dataType": "string",
        "value": "abc5555"
      }],
      "expectedResult": [{
        "nodeId": "d1e13b33-abe7-4bdd-98dc-f2d28370779c",
        "apiName": "phoneNumber",
        "displayName": "Phone Number",
        "dataType": "string",
        "value": "(555) 555-5555"
      },{
        "nodeId": "f3b02afc-5466-4ca3-ab37-1f663580024a",
        "apiName": "phone_number",
        "displayName": "Phone Number",
        "dataType": "string",
        "value": "abc5555"
      }]
    }
  }
```

# Get Test
## [GET] /api/v1/test/entityPipeline/:entityPipelineId/testId/:testId
## [GET] /api/v1/test/fieldPipeline/:fieldPipelineId/testId/:testId

### Response:
```JSON
  {
    "id": "2abe40c2c9e58186d7405e3e",
    "displayName": "Phone Number format test",
    "description": "Test the phone number fields with some random format",
    "tags": ["tag1", "tag2"],
    "createdAt": "12/12/12 16:01:01",
    "updatedAt": "12/12/12 16:01:01",
    "ownerFirstName": "Sharon",
    "ownerLastName": "Rivers",
    "ownerEmail": "sharon.rivers@acme.com",
    "testData": {
      "input": [{
        "nodeId": "9452ac2d-bea8-45bd-bd1d-3c1d66b032e5",
        "apiName": "phoneNumber",
        "displayName": "Phone Number",
        "dataType": "string",
        "value": "555-555-5555"
      },{
        "nodeId": "f3b02afc-5466-4ca3-ab37-1f663580024a",
        "apiName": "phone_number",
        "displayName": "Phone Number",
        "dataType": "string",
        "value": "abc5555"
      }],
      "expectedResult": [{
        "nodeId": "d1e13b33-abe7-4bdd-98dc-f2d28370779c",
        "apiName": "phoneNumber",
        "displayName": "Phone Number",
        "dataType": "string",
        "value": "(555) 555-5555"
      },{
        "nodeId": "f3b02afc-5466-4ca3-ab37-1f663580024a",
        "apiName": "phone_number",
        "displayName": "Phone Number",
        "dataType": "string",
        "value": "abc5555"
      }]
    }
  }
```

### List Tests
## [GET] /api/v1/test/entityPipeline/:entityPipelineId
## [GET] /api/v1/test/fieldPipeline/:fieldPipelineId

### Response:
```JSON
  [{
    "id": "2abe40c2c9e58186d7405e3e",
    "displayName": "Phone Number format test",
    "description": "Test the phone number fields with some random format",
    "tags": ["tag1", "tag2"],
    "createdAt": "12/12/12 16:01:01",
    "updatedAt": "12/12/12 16:01:01",
    "ownerFirstName": "Sharon",
    "ownerLastName": "Rivers",
    "ownerEmail": "sharon.rivers@acme.com",
  },{
    "id": "2abe40c2c9e58186d7405e4d",
    "nodeId": "2abe40c2c9e58186d7405e4d",
    "displayName": "Phone Number format test",
    "description": "Test the phone number fields with some random format",
    "tags": ["tag1", "tag2"],
    "createdAt": "12/12/12 16:01:01",
    "updatedAt": "12/12/12 16:01:01",
    "ownerFirstName": "Sharon",
    "ownerLastName": "Rivers",
    "ownerEmail": "sharon.rivers@acme.com",
  }]
```

# Run Tests
## [POST] /api/v1/test/entityPipeline/:entityPipelineId/run
## [POST] /api/v1/test/fieldPipeline/:fieldPipelineId/run
### Payload:
```JSON
["2abe40c2c9e58186d7405e3e", "2abe40c2c9e58186d7405e4d"]
```

### List Test Runs
## [GET] /api/v1/test/entityPipeline/:testId/run
## [GET] /api/v1/test/fieldPipeline/:testId/run
### Payload:
```JSON
[{
  "id": "2abe40c2c9e58186d7405e3e",
  "runName": "12/12/12 22:12:12",
  "testNames": ["test1", "test2", "test3", "test4"],
}]
```

### Get Test Run
## [GET] /api/v1/test/entityPipeline/:entityPipelineId/run/latest|:runId
## [GET] /api/v1/test/fieldPipeline/:fieldPipelineId/run/latest|:runId
### TODO: We may need to split payload of this api to bare list of tests that were run and get test run result

### Response:
```JSON
  {
    "id": "2abe40c2c9e58186d7405e3e",
    "runName": "12/12/12 22:12:12",
    "resultDetails":   [{
      "id": "2abe40c2c9e58186d7405e3e",
      "displayName": "Phone Number format test one",
      "description": "Test the phone number fields with some random format",
      "tags": ["tag1", "tag2"],
      "createdAt": "12/12/12 16:01:01",
      "updatedAt": "12/12/12 16:01:01",
      "ownerFirstName": "Sharon",
      "ownerLastName": "Rivers",
      "ownerEmail": "sharon.rivers@acme.com",
      "result": "PASSED",
      "nodes": [{
        "nodeId": "2abe40c2c9e58186d7405e4d",
        "displayName": "Sync from Account Phone Number",
        "status": "COMPLETED",
        "testData": { // Test Data inside the node is just input and output. No value in output if the result is skipped
          "result" : "PASSED",
          "ranAt" : "12/12/12 16:01:01",
          "input": [{
            "apiName": "phoneNumber",
            "displayName": "Phone Number",
            "dataType": "string",
            "value": "555-555-5555"
          },{
            "apiName": "phone_number",
            "displayName": "Phone Number",
            "dataType": "string",
            "value": "abc5555"
          }],
          "output": [{
            "apiName": "phoneNumber",
            "displayName": "Phone Number",
            "dataType": "string",
            "value": "(555) 555-5555"
          },{
            "apiName": "phone_number",
            "displayName": "Phone Number",
            "dataType": "string",
            "value": "abc5555"
          }]
        }
      },{
        "nodeId": "2abe40c2c9e58186d7405e5f",
        "displayName": "Sync to Account Phone Number",
        "status": "COMPLETED",
        "testData": {
          "result" : "PASSED",
          "ranAt" : "12/12/12 16:01:01",
          "input": [{
            "apiName": "phoneNumber",
            "displayName": "Phone Number",
            "value": "555-555-5555"
          },{
            "apiName": "phone_number",
            "displayName": "Phone Number",
            "value": "abc5555"
          }],
          "output": [{
            "apiName": "phoneNumber",
            "displayName": "Phone Number",
            "dataType": "string",
            "value": "(555) 555-5555"
          },{
            "apiName": "phone_number",
            "displayName": "Phone Number",
            "dataType": "string",
            "value": "abc5555"
          }]
        }
      }],
      "testData": { // This is the overview of the test which has input, expected result and actualy result
        "input": [{
          "apiName": "phoneNumber",
          "displayName": "Phone Number",
          "dataType": "string",
          "value": "555-555-5555"
        }],
        "expectedResult": [{
          "apiName": "phoneNumber",
          "displayName": "Phone Number",
          "dataType": "string",
          "value": "(555) 555-5555"
        }],
        "actualResult": [{
          "apiName": "phoneNumber",
          "displayName": "Phone Number",
          "dataType": "string",
          "value": "(555) 555-5555"
        }]
      }
    },{
      "id": "2abe40c2c9e58186d7405e4d",
      "nodeId": "2abe40c2c9e58186d7405e4d",
      "displayName": "Phone Number format test two",
      "description": "Test the phone number fields with some random format",
      "tags": ["tag1", "tag2"],
      "createdAt": "12/12/12 16:01:01",
      "updatedAt": "12/12/12 16:01:01",
      "ownerFirstName": "Sharon",
      "ownerLastName": "Rivers",
      "ownerEmail": "sharon.rivers@acme.com",
      "nodes": [{
        "nodeId": "2abe40c2c9e58186d7405e4d",
        "displayName": "Sync from Account Phone Number",
        "status": "COMPLETED",
        "testData": {
          "result" : "PASSED",
          "ranAt" : "12/12/12 16:01:01",
          "input": [{
            "apiName": "phoneNumber",
            "displayName": "Phone Number",
            "dataType": "string",
            "value": "555-555-5555"
          },{
            "apiName": "phone_number",
            "displayName": "Phone Number",
            "dataType": "string",
            "value": "abc5555"
          }],
          "output": [{
            "apiName": "phoneNumber",
            "displayName": "Phone Number",
            "dataType": "string",
            "value": "(555) 555-5555"
          },{
            "apiName": "phone_number",
            "displayName": "Phone Number",
            "dataType": "string",
            "value": "abc5555"
          }]
        }
      },{
        "nodeId": "2abe40c2c9e58186d7405e5f",
        "displayName": "Sync to Account Phone Number",
        "status": "COMPLETED",
        "testData": {
          "result" : "PASSED",
          "ranAt" : "12/12/12 16:01:01",
          "input": [{
            "apiName": "phoneNumber",
            "displayName": "Phone Number",
            "dataType": "string",
            "value": "555-555-5555"
          },{
            "apiName": "phone_number",
            "displayName": "Phone Number",
            "dataType": "string",
            "value": "abc5555"
          }],
          "output": [{
            "apiName": "phoneNumber",
            "displayName": "Phone Number",
            "dataType": "string",
            "value": "(555) 555-5555"
          },{
            "apiName": "phone_number",
            "displayName": "Phone Number",
            "dataType": "string",
            "value": "abc5555"
          }]
        }
      }],
      "testData": {
        "input": [{
          "apiName": "phoneNumber",
          "displayName": "Phone Number",
          "dataType": "string",
          "value": "555-555-5555"
        }],
        "expectedResult": [{
          "apiName": "phoneNumber",
          "dataType": "string",
          "displayName": "Phone Number",
          "value": "(555) 555-5555"
        }],
        "actualResult": [{
          "apiName": "phoneNumber",
          "displayName": "Phone Number",
          "dataType": "string",
          "value": "(555) 555-5555"
        }]
      }
    }]
  }
```

# Delete Test
## [DELETE] /api/v1/test/entityPipeline/:testId
## [DELETE] /api/v1/test/fieldPipeline/:testId
### Response: The deleted test, same as [Get Test](#get-test)
