//
// Copyright (c) 2019-Present Syncari - All rights reserved.
//

// Default entity graph locations for standard entities
const SOURCE_ANCHOR = 'sourceAnchor';
const TARGET_ANCHOR = 'targetAnchor';
const LOCATION_X = 'x';
const LOCATION_Y = 'y';

export const STARTING_DEFAULT_X = 140;
export const STARTING_INC_X = 30;
export const STARTING_DEFAULT_Y = 54;
export const STARTING_INC_Y = 30;

export const DEFAULT_NODE_LOCATION = {
  // This key should name with the entity api name
  ACCOUNT: {
    [LOCATION_X]: 584,
    [LOCATION_Y]: 100,
  },
  LEAD: {
    [LOCATION_X]: 313,
    [LOCATION_Y]: 211,
  },
  ACTIVITY: {
    [LOCATION_X]: 163,
    [LOCATION_Y]: 333,
  },
  CONTACT: {
    [LOCATION_X]: 312,
    [LOCATION_Y]: 465,
  },
  TICKET: {
    [LOCATION_X]: 584,
    [LOCATION_Y]: 575,
  },
  USER: {
    [LOCATION_X]: 902,
    [LOCATION_Y]: 397,
  },
  OPPORTUNITY: {
    [LOCATION_X]: 902,
    [LOCATION_Y]: 211,
  },
};

export const DEFAULT_EDGE_ANCHOR = {
  // The key here should match <SOURCE_OBJECT_APINAME>_<DESTINATION_OBJECT_APINAME>
  LEAD_ACCOUNT: {
    [SOURCE_ANCHOR]: 0,
    [TARGET_ANCHOR]: 3,
  },
  ACTIVITY_CONTACT: {
    [SOURCE_ANCHOR]: 2,
    [TARGET_ANCHOR]: 3,
  },
  TICKET_CONTACT: {
    [SOURCE_ANCHOR]: 3,
    [TARGET_ANCHOR]: 2,
  },
  TICKET_USER: {
    [SOURCE_ANCHOR]: 1,
    [TARGET_ANCHOR]: 2,
  },
  OPPORTUNITY_USER: {
    [SOURCE_ANCHOR]: 2,
    [TARGET_ANCHOR]: 0,
  },
  OPPORTUNITY_ACCOUNT: {
    [SOURCE_ANCHOR]: 0,
    [TARGET_ANCHOR]: 1,
  },
  LEAD_CONTACT: {
    [SOURCE_ANCHOR]: 2,
    [TARGET_ANCHOR]: 0,
  },
  CONTACT_USER: {
    [SOURCE_ANCHOR]: 1,
    [TARGET_ANCHOR]: 2,
  },
  LEAD_OPPORTUNITY: {
    [SOURCE_ANCHOR]: 1,
    [TARGET_ANCHOR]: 3,
  },
  LEAD_USER: {
    [SOURCE_ANCHOR]: 1,
    [TARGET_ANCHOR]: 3,
  },
  ACTIVITY_ACCOUNT: {
    [SOURCE_ANCHOR]: 1,
    [TARGET_ANCHOR]: 2,
  },
  ACTIVITY_USER: {
    [SOURCE_ANCHOR]: 1,
    [TARGET_ANCHOR]: 3,
  },
  CONTACT_ACCOUNT: {
    [SOURCE_ANCHOR]: 1,
    [TARGET_ANCHOR]: 2,
  },
  ACCOUNT_USER: {
    [SOURCE_ANCHOR]: 2,
    [TARGET_ANCHOR]: 0,
  },
  TICKET_ACCOUNT: {
    [SOURCE_ANCHOR]: 0,
    [TARGET_ANCHOR]: 2,
  },
};
