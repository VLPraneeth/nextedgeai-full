# Import Export Pipeline Fragments
## TOC
### 1.) [Create Fragment](#create-fragment)
### 2.) [Get Fragment](#get-fragment)
### 3.) [Update Fragment](#update-fragment)
### 4.) [Delete Fragment](#delete-fragment)
### 5.) [Hide Fragment](#hide-fragment)
### 6.) [Show Fragment](#show-fragment)
### 7.) [Share Fragment](#share-fragment)
### 8.) [Get Fragment Shares](#get-fragment-shares)
### 9.) [List Fragments](#list-fragments)
### 10.) [List User Instances](#list-user-instances)
### 11.) [Validate Update](#validate)

# Create Fragment
## [POST] /api/v1/fragment/entityPipeline
## [POST] /api/v1/fragment/fieldPipeline

#### Note: Raw values of functions will be saved and the backend will do its best to resolve it when used. See updated validation response below for unreconciled nodes and edges.

### Payload:
```JSON
  {
    "displayName": "My Fragment",
    "description": "My Fragment Description",
    "tags": ["tag1", "tag2"],
    "sharedToInstance": [],
    "fragment": {
      "nodes": [
        {
          "templateId": "5f21ccc3701e06184ca7b6b7",
          "name": "Camel Case",
          "label": "Camel Case",
          "subLabel": "",
          "inputPorts": [
            {
              "portType": "INPUT",
              "datatype": "string",
              "maxConnections": 1
            }
          ],
          "outputPorts": [
            {
              "portType": "OUTPUT",
              "datatype": "string",
              "maxConnections": 1
            }
          ],
          "configuration":
            {
              "configId": "5f21bc21f5bfd27a897f2012",
              "definition": "5f21bc21f5bfd27a897f2012"
            },
          "nodeType": "FUNCTION",
          "location":
            {
              "x": 371,
              "y": 270
            },
        },
        {
          "templateId": "5f21ccc3701e06184ca7b6b8",
          "name": "Capitalize",
          "label": "Capitalize",
          "subLabel": "",
          "inputPorts": [
            {
              "portType": "INPUT",
              "datatype": "string",
              "maxConnections": 1
            }
          ],
          "outputPorts": [
            {
              "portType": "OUTPUT",
              "datatype": "string",
              "maxConnections": 1
            }
          ],
          "configuration": {
            "configId": "5f21bc21f5bfd27a897f2057",
            "definition":"5f21bc21f5bfd27a897f2057"
          },
          "nodeType": "FUNCTION",
          "location": {
            "x": 515,
            "y": 177
          },
        },
      ],
      "edges": [
        {
          "templateId": "ca6c5618",
          "source": {
            "nodeTemplateId": "5f21ccc3701e06184ca7b6b7",
            "port": {
              "portType": "OUTPUT",
              "datatype": "string",
              "maxConnections": 1
            },
            "anchor": "0",
          },
          "destination": {
            "nodeTemplateId": "5f21ccc3701e06184ca7b6b8",
            "port": {
              "portType": "INPUT",
              "datatype": "string",
              "maxConnections": 1
            },
            "anchor": "3",
          },
        },
      ],
    }
  }
```

### Response:
```JSON
  {
    "id": "2abe40c2c9e58186d7405e3e",
    "displayName": "My Fragment",
    "description": "My Fragment Description",
    "ownerFirstName": "Sharon",
    "ownerLastName": "Rivers",
    "ownerEmail": "sharon.rivers@acme.com",
    "tags": ["tag1", "tag2"],
    "sharedToInstance": [],
    "iconPath": "/assets/icons/fragment.svg",
    "originalInstanceId": null,
    "originalFragmentId": null,
    "hidden": false,
    "fragment": {
      "nodes": [
        {
          "templateId": "5f21ccc3701e06184ca7b6b7",
          "name": "Camel Case",
          "label": "Camel Case",
          "subLabel": "",
          "inputPorts": [
            {
              "portType": "INPUT",
              "datatype": "string",
              "maxConnections": 1
            }
          ],
          "outputPorts": [
            {
              "portType": "OUTPUT",
              "datatype": "string",
              "maxConnections": 1
            }
          ],
          "configuration":
            {
              "configId": "5f21bc21f5bfd27a897f2012",
              "definition": "5f21bc21f5bfd27a897f2012"
            },
          "nodeType": "FUNCTION",
          "location":
            {
              "x": 371,
              "y": 270
            },
        },
        {
          "templateId": "5f21ccc3701e06184ca7b6b8",
          "name": "Capitalize",
          "label": "Capitalize",
          "subLabel": "",
          "inputPorts": [
            {
              "portType": "INPUT",
              "datatype": "string",
              "maxConnections": 1
            }
          ],
          "outputPorts": [
            {
              "portType": "OUTPUT",
              "datatype": "string",
              "maxConnections": 1
            }
          ],
          "configuration": {
            "configId": "5f21bc21f5bfd27a897f2057",
            "definition":"5f21bc21f5bfd27a897f2057"
          },
          "nodeType": "FUNCTION",
          "location": {
            "x": 515,
            "y": 177
          },
        },
      ],
      "edges": [
        {
          "templateId": "ca6c5618",
          "source": {
            "nodeTemplateId": "5f21ccc3701e06184ca7b6b7",
            "port": {
              "portType": "OUTPUT",
              "datatype": "string",
              "maxConnections": 1
            },
            "anchor": "0",
          },
          "destination": {
            "nodeTemplateId": "5f21ccc3701e06184ca7b6b8",
            "port": {
              "portType": "INPUT",
              "datatype": "string",
              "maxConnections": 1
            },
            "anchor": "3",
          },
        },
      ],
    }
  }
```
# Get Fragment
## [GET] /api/v1/fragment/entityPipeline/:fragmentId
## [GET] /api/v1/fragment/fieldPipeline/:fragmentId

### Response:
```JSON
  {
    "displayName": "My Fragment",
    "description": "My Fragment Description",
    "tags": ["tag1", "tag2"],
    "sharedToInstance": [],
    "iconPath": "/assets/icons/fragment.svg",
    "ownerFirstName": "Sharon",
    "ownerLastName": "Rivers",
    "ownerEmail": "sharon.rivers@acme.com",
    "originalInstanceId": null,
    "originalFragmentId": null,
    "fragment": {
      "nodes": [
        {
          "templateId": "5f21ccc3701e06184ca7b6b7",
          "name": "Camel Case",
          "label": "Camel Case",
          "subLabel": "",
          "inputPorts": [
            {
              "portType": "INPUT",
              "datatype": "string",
              "maxConnections": 1
            }
          ],
          "outputPorts": [
            {
              "portType": "OUTPUT",
              "datatype": "string",
              "maxConnections": 1
            }
          ],
          "configuration":
            {
              "configId": "5f21bc21f5bfd27a897f2012",
              "definition": "5f21bc21f5bfd27a897f2012"
            },
          "nodeType": "FUNCTION",
          "location":
            {
              "x": 371,
              "y": 270
            },
        },
        {
          "templateId": "5f21ccc3701e06184ca7b6b8",
          "name": "Capitalize",
          "label": "Capitalize",
          "subLabel": "",
          "inputPorts": [
            {
              "portType": "INPUT",
              "datatype": "string",
              "maxConnections": 1
            }
          ],
          "outputPorts": [
            {
              "portType": "OUTPUT",
              "datatype": "string",
              "maxConnections": 1
            }
          ],
          "configuration": {
            "configId": "5f21bc21f5bfd27a897f2057",
            "definition":"5f21bc21f5bfd27a897f2057"
          },
          "nodeType": "FUNCTION",
          "location": {
            "x": 515,
            "y": 177
          },
        },
      ],
      "edges": [
        {
          "templateId": "ca6c5618",
          "source": {
            "nodeTemplateId": "5f21ccc3701e06184ca7b6b7",
            "port": {
              "portType": "OUTPUT",
              "datatype": "string",
              "maxConnections": 1
            },
            "anchor": "0",
          },
          "destination": {
            "nodeTemplateId": "5f21ccc3701e06184ca7b6b8",
            "port": {
              "portType": "INPUT",
              "datatype": "string",
              "maxConnections": 1
            },
            "anchor": "3",
          },
        },
      ],
    }
  }
```

# Update Fragment
## [PUT] /api/v1/fragment/entityPipeline/:fragmentId
## [PUT] /api/v1/fragment/fieldPipeline/:fragmentId

### Payload:
```JSON
  {
    "displayName": "My Fragment",
    "description": "My Fragment Description",
    "tags": ["tag1", "tag2"],
    "sharedToInstance": ["c952010ad68e11ea87d00242ac130003", "ce414e28d68e11ea87d00242ac130003"],
    "fragment": {
      "nodes": [
        {
          "templateId": "5f21ccc3701e06184ca7b6b7",
          "name": "Camel Case",
          "label": "Camel Case",
          "subLabel": "",
          "inputPorts": [
            {
              "portType": "INPUT",
              "datatype": "string",
              "maxConnections": 1
            }
          ],
          "outputPorts": [
            {
              "portType": "OUTPUT",
              "datatype": "string",
              "maxConnections": 1
            }
          ],
          "configuration":
            {
              "configId": "5f21bc21f5bfd27a897f2012",
              "definition": "5f21bc21f5bfd27a897f2012"
            },
          "nodeType": "FUNCTION",
          "location":
            {
              "x": 371,
              "y": 270
            },
        },
        {
          "templateId": "5f21ccc3701e06184ca7b6b8",
          "name": "Capitalize",
          "label": "Capitalize",
          "subLabel": "",
          "inputPorts": [
            {
              "portType": "INPUT",
              "datatype": "string",
              "maxConnections": 1
            }
          ],
          "outputPorts": [
            {
              "portType": "OUTPUT",
              "datatype": "string",
              "maxConnections": 1
            }
          ],
          "configuration": {
            "configId": "5f21bc21f5bfd27a897f2057",
            "definition":"5f21bc21f5bfd27a897f2057"
          },
          "nodeType": "FUNCTION",
          "location": {
            "x": 515,
            "y": 177
          },
        },
      ],
      "edges": [
        {
          "templateId": "ca6c5618",
          "source": {
            "nodeTemplateId": "5f21ccc3701e06184ca7b6b7",
            "port": {
              "portType": "OUTPUT",
              "datatype": "string",
              "maxConnections": 1
            },
            "anchor": "0",
          },
          "destination": {
            "nodeTemplateId": "5f21ccc3701e06184ca7b6b8",
            "port": {
              "portType": "INPUT",
              "datatype": "string",
              "maxConnections": 1
            },
            "anchor": "3",
          },
        },
      ],
    }
  }
```
### Response:
```JSON
  {
    "displayName": "My Fragment",
    "description": "My Fragment Description",
    "ownerFirstName": "Sharon",
    "ownerLastName": "Rivers",
    "ownerEmail": "sharon.rivers@acme.com",
    "iconPath": "/assets/icons/fragment.svg",
    "tags": ["tag1", "tag2"],
    "sharedToInstance": ["c952010ad68e11ea87d00242ac130003", "ce414e28d68e11ea87d00242ac130003"],
    "originalInstanceId": null,
    "originalFragmentId": null,
    "fragment": {
      "nodes": [
        {
          "templateId": "5f21ccc3701e06184ca7b6b7",
          "name": "Camel Case",
          "label": "Camel Case",
          "subLabel": "",
          "inputPorts": [
            {
              "portType": "INPUT",
              "datatype": "string",
              "maxConnections": 1
            }
          ],
          "outputPorts": [
            {
              "portType": "OUTPUT",
              "datatype": "string",
              "maxConnections": 1
            }
          ],
          "configuration":
            {
              "configId": "5f21bc21f5bfd27a897f2012",
              "definition": "5f21bc21f5bfd27a897f2012"
            },
          "nodeType": "FUNCTION",
          "location":
            {
              "x": 371,
              "y": 270
            },
        },
        {
          "templateId": "5f21ccc3701e06184ca7b6b8",
          "name": "Capitalize",
          "label": "Capitalize",
          "subLabel": "",
          "inputPorts": [
            {
              "portType": "INPUT",
              "datatype": "string",
              "maxConnections": 1
            }
          ],
          "outputPorts": [
            {
              "portType": "OUTPUT",
              "datatype": "string",
              "maxConnections": 1
            }
          ],
          "configuration": {
            "configId": "5f21bc21f5bfd27a897f2057",
            "definition":"5f21bc21f5bfd27a897f2057"
          },
          "nodeType": "FUNCTION",
          "location": {
            "x": 515,
            "y": 177
          },
        },
      ],
      "edges": [
        {
          "templateId": "ca6c5618",
          "source": {
            "nodeTemplateId": "5f21ccc3701e06184ca7b6b7",
            "port": {
              "portType": "OUTPUT",
              "datatype": "string",
              "maxConnections": 1
            },
            "anchor": "0",
          },
          "destination": {
            "nodeTemplateId": "5f21ccc3701e06184ca7b6b8",
            "port": {
              "portType": "INPUT",
              "datatype": "string",
              "maxConnections": 1
            },
            "anchor": "3",
          },
        },
      ],
    }
  }
```

# Delete Fragment
## [DELETE] /api/v1/fragment/entityPipeline/:fragmentId
## [DELETE] /api/v1/fragment/fieldPipeline/:fragmentId
### Response: The deleted fragment, same as [Get Fragment](#get-fragment)

# Hide Fragment
## [POST] /api/v1/fragment/entityPipeline/:fragmentId/hide
## [POST] /api/v1/fragment/fieldPipeline/:fragmentId/hide
### Response: Same as [Get Fragment](#get-fragment)

# Show Fragment
## [POST] /api/v1/fragment/entityPipeline/:fragmentId/show
## [POST] /api/v1/fragment/fieldPipeline/:fragmentId/show
### Response: Same as [Get Fragment](#get-fragment)

# Share Fragment
## [PUT] /api/v1/fragment/entityPipeline:fragmentId/share
## [PUT] /api/v1/fragment/fieldPipeline:fragmentId/share

### Payload:
```JSON
["7e0b55d6d1e611ea87d00242ac130003"]
```

### Response:
```JSON
["7e0b55d6d1e611ea87d00242ac130003"]
```

# Get Fragment Shares
## [GET] /api/v1/fragment/entityPipeline:fragmentId/share
## [GET] /api/v1/fragment/fieldPipeline:fragmentId/share

### Response:
```JSON
["7e0b55d6d1e611ea87d00242ac130003"]
```

# List Fragments
## [GET] /api/v1/fragment/entitypipeline
## [GET] /api/v1/fragment/fieldpipeline

### Fragments list response:
``` JSON
[
  {
    "id": "2abe40c2c9e58186d7405e3e",
    "displayName": "My Fragment",
    "description": "My Fragment Description",
    "ownerUserId": "3e1f40c2c9e58186d7405b6c",
    "ownerFirstName": "Sharon",
    "ownerLastName": "Rivers",
    "ownerEmail": "sharon.rivers@acme.com",
    "iconPath": "/assets/icons/fragment.svg",
    "tags": ["tag1", "tag2"],
    "originalInstanceId": null,
    "originalFragmentId": null,
    "sharedWithInstance": ["c952010ad68e11ea87d00242ac130003", "ce414e28d68e11ea87d00242ac130003"],
    "nodeType": "FRAGMENT",                               // New Node Type FRAGMENT
    "fragment": {
      "nodes": [
        {
          "templateId": "5f21ccc3701e06184ca7b6b7",
          "name": "Camel Case",
          "label": "Camel Case",
          "subLabel": "",
          "inputPorts": [
            {
              "portType": "INPUT",
              "datatype": "string",
              "maxConnections": 1
            }
          ],
          "outputPorts": [
            {
              "portType": "OUTPUT",
              "datatype": "string",
              "maxConnections": 1
            }
          ],
          "configuration":
            {
              "configId": "5f21bc21f5bfd27a897f2012",
              "definition": "5f21bc21f5bfd27a897f2012"
            },
          "nodeType": "FUNCTION",
          "location":
            {
              "x": 371,
              "y": 270
            },
        },
        {
          "templateId": "5f21ccc3701e06184ca7b6b8",
          "name": "Capitalize",
          "label": "Capitalize",
          "subLabel": "",
          "inputPorts": [
            {
              "portType": "INPUT",
              "datatype": "string",
              "maxConnections": 1
            }
          ],
          "outputPorts": [
            {
              "portType": "OUTPUT",
              "datatype": "string",
              "maxConnections": 1
            }
          ],
          "configuration": {
            "configId": "5f21bc21f5bfd27a897f2057",
            "definition":"5f21bc21f5bfd27a897f2057"
          },
          "nodeType": "FUNCTION",
          "location": {
            "x": 515,
            "y": 177
          },
        },
      ],
      "edges": [
        {
          "templateId": "ca6c5618",
          "source": {
            "nodeTemplateId": "5f21ccc3701e06184ca7b6b7",
            "port": {
              "portType": "OUTPUT",
              "datatype": "string",
              "maxConnections": 1
            },
            "anchor": "0",
          },
          "destination": {
            "nodeTemplateId": "5f21ccc3701e06184ca7b6b8",
            "port": {
              "portType": "INPUT",
              "datatype": "string",
              "maxConnections": 1
            },
            "anchor": "3",
          },
        },
      ],
    }
  },
  {
    "id": "5f1e40c2c9e58186d7405a9a",
    "displayName": "Shared Fragment",
    "description": "Shared Fragment Description",
    "ownerUserId": "5a3d10c2c9e58186d7405b3b",
    "iconPath": "/assets/icons/shared-fragment.svg",
    "ownerFirstName": "John",
    "ownerLastName": "Doe",
    "ownerEmail": "john.doe@acme.com",
    "tags": ["tag3", "tag4"],
    "originalInstanceId": "c952010ad68e11ea87d00242ac130003",
    "originalFragmentId": "aca45acad68f11ea87d00242ac130003",
    "sharedToInstance": ["c952010ad68e11ea87d00242ac130003", "ce414e28d68e11ea87d00242ac130003"],
    "nodeType": "FRAGMENT",
    "fragment": {
      "nodes": [
        {
          "templateId": "5f21ccc3701e06184ca7b6b7",
          "name": "Camel Case",
          "label": "Camel Case",
          "subLabel": "",
          "inputPorts": [
            {
              "portType": "INPUT",
              "datatype": "string",
              "maxConnections": 1
            }
          ],
          "outputPorts": [
            {
              "portType": "OUTPUT",
              "datatype": "string",
              "maxConnections": 1
            }
          ],
          "configuration": {
            "configId": "5f21bc21f5bfd27a897f2012",
            "definition": "5f21bc21f5bfd27a897f2012"
          },
          "nodeType": "FUNCTION",
          "location": {
            "x": 371,
            "y": 270
          },
        },
        {
          "templateId": "5f21ccc3701e06184ca7b6b8",
          "name": "Capitalize",
          "label": "Capitalize",
          "subLabel": "",
          "inputPorts": [
            {
              "portType": "INPUT",
              "datatype": "string",
              "maxConnections": 1
            }
          ],
          "outputPorts": [
            {
              "portType": "OUTPUT",
              "datatype": "string",
              "maxConnections": 1
            }
          ],
          "configuration": {
            "configId": "5f21bc21f5bfd27a897f2057",
            "definition": "5f21bc21f5bfd27a897f2057"
          },
          "nodeType": "FUNCTION",
          "location": {
            "x": 515,
            "y": 177
          },
        },
      ],
      "edges": [
        {
          "templateId": "ca6c5618",
          "source": {
            "nodeTemplateId": "5f21ccc3701e06184ca7b6b7",
            "port": {
              "portType": "OUTPUT",
              "datatype": "string",
              "maxConnections": 1
            },
            "anchor": "0",
          },
          "destination": {
            "nodeTemplateId": "5f21ccc3701e06184ca7b6b8",
            "port": {
              "portType": "INPUT",
              "datatype": "string",
              "maxConnections": 1
            },
            "anchor": "3",
          },
        },
      ],
    }
  },
];
```
# List User Instances
## See user listinstances API `/api/v1/user/instances`

# Validate
## [POST] /api/v1/pipeline/entityPipeline/:syncariEntityId/validate
## [POST] /api/v1/pipeline/fieldPipeline/:syncariEntityId/validate
### Payload:
```JSON
... Full pipeline graph JSON
```

### Response:
```JSON
[{
  "componentType": "node",
  "id": "8f0b55d6d1e611ea87d00242ac132341",
  "level": "error",
  "message": "Filter has incorrect value",
  "details": "Filter with value 7e0b55d6d1e611ea87d00242ac130003 is invalid",
},{
  "componentType": "node",
  "id": "8f0b55d6d1e611ea87d00242ac132341",
  "level": "error",
  "message": "Pipeline {name} node has has incorrect value",
  "details": "Filter with value 7e0b55d6d1e611ea87d00242ac130003 is invalid",
  "name": {
    ...
  }
},{
  "componentType": "pipeline",
  "id": "7e0b55d6d1e611ea87d00242ac130003",
  "level": "error",
  "message": "{name} is a required field",
  "details": "{name} with datatype type string is a required field in salesforce",
  "name": {
    "label": "Account Name", // Label is the value used to replace the text
    "route": "FIELD_PIPELINE_GRAPH_VERSION", // This property will trigger the text to be replaced with a link
    "entityId": "0f75f718d2a711ea87d00242ac130003",
    "fieldId": "156fd3c8d2a711ea87d00242ac130003"
  }
},{
  "componentType": "edge",
  "id": "8f0b55d6d1e611ea87d00242ac132341",
  "level": "warning",
  "message": "Incompatible connection",
  "details": "Incompatible datatype between node x to node y"
}]
```
