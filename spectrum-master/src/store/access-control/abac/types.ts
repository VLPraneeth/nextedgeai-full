//
// Copyright (c) 2019-Present Syncari - All rights reserved.
//

export interface AbacAttributeRequestDTO {
  name: string;
  description?: string;
  dataType: string;
  resourceType: string;
}

export interface AbacAttributeResponseDTO {
  allowedValues: string[];
  apiName: string;
  dataType: string;
  id: string;
  multiValued: boolean;
  name: string;
  policies: number;
  resourceId: string;
  resourceName: string;
  resourceTypeId: string;
  resourceTypeName: string;
}

export interface AbacAttributeValueDTO {
  id: string;
  attributeId: string;
  attributeName: string;
  resourceId: string;
  resourceName: string;
  resourceTypeId: string;
  resourceTypeName: string;
  value: string;
}

export interface AbacAttributeRequest {
  dataType: string;
  multiValued: boolean;
  name: string;
  resourceId: string;
  resourceTypeId: string;
  allowedValues?: string[];
}

export interface AbacPolicyRequest {
  name: string;
  description?: string;
  condition: Record<string, any>;
  resourceTypeId: string;
  resourceId: string;
  permissions: string[];
}

export interface AbacPolicyResponseDTO {
  id: string;
  name: string;
  resourceId: string;
  resourceName: string;
  condition: Record<string, any>;
  permissions: string[];
  resourceTypeId: string;
  resourceTypeName: string;
}

export interface AbacResource {
  id: string;
  name: string;
  type: string;
  displayName: string;
}

export interface KeyValue {
  key: string;
  value: string;
}

export interface ResourceType {
  name: string;
  displayName: string;
  permissions: string[];
}
