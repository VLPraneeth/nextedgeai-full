//
// Copyright (c) 2019-Present Syncari - All rights reserved.
//

import { Connector } from 'reducers/connectorReducer';
import { EntityField } from 'store/entity/types';
import { Mapping } from 'store/fast-mapper/types';

export interface MappingState {
  newSyncariFields?: NewEntityFieldOptions[];
  editedValues?: EditedMapping[];
  values: Mapping[];
}

export interface EditedMapping {
  existing: Mapping;
  updated: Mapping;
}

export interface MappingAction {
  type: string;
  [k: string]: any;
}

export interface EntityFieldOption extends EntityField {
  title?: string;
  createNewSyncariField?: boolean;
}

export interface NewEntityFieldOptions {
  id: EntityFieldOption['id'];
  dataType: EntityFieldOption['dataType'];
  apiName: EntityFieldOption['apiName'];
  title: EntityFieldOption['title'];
  isMultivalued: EntityField['multiValueField'];
  isRequired: EntityField['required'];
  displayName: EntityFieldOption['displayName'];
  fromSynapseFieldId: string;
}

export interface ConnectorOption extends Connector {
  iconTitle: string;
  iconUri: string;
}

export interface DirectionOption {
  id: string;
  displayName: string;
}
