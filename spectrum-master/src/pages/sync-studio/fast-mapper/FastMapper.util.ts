//
// Copyright (c) 2019-Present Syncari - All rights reserved.
//
import { some } from 'lodash';

import { EMPTY_ARRAY } from 'store/constants';
import { Mapping } from 'store/fast-mapper/types';

import { DirectionId } from '../types';

export const isMappingEmpty = (values: Mapping[]) => {
  return !values.some((val) => {
    const { id, ...restValues } = val;
    return some(restValues);
  });
};

export const convertToDirections = (directionId: string) =>
  directionId === DirectionId.BIDIRECTIONAL ? [DirectionId.SYNC_FROM, DirectionId.SYNC_TO] : [directionId];

export const DefaultColDef = { flex: 1, sortable: true };

export enum FieldType {
  Boolean = 'boolean',
  String = 'string',
  Integer = 'integer',
  Double = 'double',
  Datetime = 'datetime',
  Timestamp = 'timestamp',
  ExternalId = 'externalId',
  Date = 'date',
  PolymorphicReference = 'polymorphicreference',
  Filelink = 'filelink',
  Id = 'id',
  List = 'list',
  Object = 'object',
  Complex = 'complex',
  Picklist = 'picklist',
  Reference = 'reference',
  Textarea = 'textarea',
  Url = 'url',
}

// List taken from https://syncari.atlassian.net/wiki/spaces/ENGINEERIN/pages/1551663115/Datatypes+Compatibility
const TYPE_COMPATABILITY_LOOKUP_MAP: Record<FieldType, FieldType[]> = {
  // boolean
  [FieldType.Boolean]: [FieldType.Boolean, FieldType.String],

  // string
  [FieldType.String]: [
    FieldType.Boolean,
    FieldType.String,
    FieldType.Integer,
    FieldType.Double,
    FieldType.Datetime,
    FieldType.Timestamp,
    FieldType.Date,
    FieldType.PolymorphicReference,
    FieldType.Filelink,
    FieldType.Id,
    FieldType.List,
    FieldType.Object,
    FieldType.Complex,
    FieldType.Picklist,
    FieldType.Reference,
    FieldType.Textarea,
    FieldType.Url,
    FieldType.ExternalId,
  ],

  // integer
  [FieldType.Integer]: [
    FieldType.Boolean,
    FieldType.String,
    FieldType.Integer,
    FieldType.Double,
    FieldType.Object,
    FieldType.Complex,
    FieldType.ExternalId,
  ],

  // double
  [FieldType.Double]: [FieldType.Boolean, FieldType.String, FieldType.Integer, FieldType.Double],

  // External Id - ALERT! The mappings for ExternalId are backwards. They need
  // to be swapped once we incorporate the "Sync Type" in the call to
  // getCompatibleDestinationTypes and getCompatibleSourceTypes.
  [FieldType.ExternalId]: [],

  // datetime - same as timestamp
  [FieldType.Datetime]: [FieldType.String, FieldType.Double, FieldType.Datetime, FieldType.Timestamp, FieldType.Date],

  // timestamp - same as datetime
  [FieldType.Timestamp]: [FieldType.String, FieldType.Double, FieldType.Datetime, FieldType.Timestamp, FieldType.Date],

  // date
  [FieldType.Date]: [FieldType.String, FieldType.Double, FieldType.Datetime, FieldType.Timestamp, FieldType.Date],

  // polymorphicreference
  [FieldType.PolymorphicReference]: [FieldType.String, FieldType.PolymorphicReference],

  // filelink
  [FieldType.Filelink]: [FieldType.String, FieldType.Filelink],

  // id
  [FieldType.Id]: [FieldType.String, FieldType.Id],

  // list
  [FieldType.List]: [FieldType.String, FieldType.List, FieldType.Object, FieldType.Complex],

  // object - same as complex
  [FieldType.Object]: [
    FieldType.String,
    FieldType.Integer,
    FieldType.Double,
    FieldType.Datetime,
    FieldType.Timestamp,
    FieldType.Date,
    FieldType.PolymorphicReference,
    FieldType.Filelink,
    FieldType.Id,
    FieldType.List,
    FieldType.Object,
    FieldType.Complex,
    FieldType.Picklist,
    FieldType.Reference,
    FieldType.Textarea,
    FieldType.Url,
  ],

  // complex - same as object
  [FieldType.Complex]: [
    FieldType.String,
    FieldType.Integer,
    FieldType.Double,
    FieldType.Datetime,
    FieldType.Timestamp,
    FieldType.Date,
    FieldType.PolymorphicReference,
    FieldType.Filelink,
    FieldType.Id,
    FieldType.List,
    FieldType.Object,
    FieldType.Complex,
    FieldType.Picklist,
    FieldType.Reference,
    FieldType.Textarea,
    FieldType.Url,
  ],

  // picklist
  [FieldType.Picklist]: [FieldType.String, FieldType.List, FieldType.Object, FieldType.Complex, FieldType.Picklist],

  // reference
  [FieldType.Reference]: [
    FieldType.String,
    FieldType.Object,
    FieldType.Complex,
    FieldType.Reference,
    FieldType.ExternalId,
  ],

  // textarea
  [FieldType.Textarea]: [FieldType.String, FieldType.Object, FieldType.Complex, FieldType.Textarea],

  // url
  [FieldType.Url]: [FieldType.String, FieldType.Object, FieldType.Complex, FieldType.Url],
};

// Compares two types to see if they are compatable as source to destination
export const areTypesCompatable = (sourceType: FieldType, destinationType: FieldType) => {
  return !!TYPE_COMPATABILITY_LOOKUP_MAP[sourceType]?.includes(destinationType);
};

// Gets all valid destination types given a source type
export const getCompatibleDestinationTypes = (sourceType: FieldType | undefined) => {
  if (!sourceType) {
    return EMPTY_ARRAY;
  }

  return TYPE_COMPATABILITY_LOOKUP_MAP[sourceType] ?? EMPTY_ARRAY;
};

// Gets all valid source types given a destination type
export const getCompatibleSourceTypes = (destinationType: FieldType | undefined) => {
  if (!destinationType) {
    return EMPTY_ARRAY;
  }

  let sourceTypes: FieldType[] = [];

  Object.keys(TYPE_COMPATABILITY_LOOKUP_MAP).forEach((sourceType) => {
    if (TYPE_COMPATABILITY_LOOKUP_MAP[sourceType as FieldType].includes(destinationType)) {
      sourceTypes.push(sourceType as FieldType);
    }
  });

  return sourceTypes;
};
