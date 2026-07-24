// @ts-nocheck
//
// Copyright (c) 2019-Present Syncari - All rights reserved.
//
import { find, camelCase } from 'lodash';

export function findConnectorMetadata(metadataId, metadata) {
  return find(metadata, (meta) => {
    return meta.configId === metadataId;
  });
}

export function getDatatype(datatype) {}

const INPUT_NAME_MAP = {
  Name: 'name',
  'User Name': 'userName',
  Password: 'password',
  Token: 'token',
  'API Key': 'ApiKey',
};

export function getInputName(name) {
  if (INPUT_NAME_MAP[name]) {
    return INPUT_NAME_MAP[name];
  }
  return camelCase(name);
}

const DISPLAY_NAME_MAP = {
  'Authentication Method': 'Authentication',
  UserPasswordToken: 'Token',
  userName: 'User Name',
  password: 'Password',
  token: 'Token',
  ApiKey: 'API Key',
  Oauth: 'OAuth',
  clientId: 'Client ID',
  clientSecret: 'Client Secret',
  apiKey: 'API Key',
};

export function getDisplayName(name) {
  if (DISPLAY_NAME_MAP[name]) {
    return DISPLAY_NAME_MAP[name];
  }
  return name;
}

export function getFormValidateStatusKey(name) {
  const inputName = getInputName(name);
  return `${inputName}ValidateStatus`;
}

export function getFormErrorKey(name) {
  const inputName = getInputName(name);
  return `${inputName}Message`;
}

export function getAuthInputs(authType, fields) {}
