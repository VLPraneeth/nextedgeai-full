import { UserRole } from 'store/access-control/types';

export const rbacTableFixture = [
  {
    id: '34824267yriuw',
    name: 'IT Admin',
    description: 'IT Admin team',
    status: 'Active',
    privileges: [
      {
        resourceId: 'global', //Ignore this for now. This will be used in future
        privilegeId: 'READ_DATA_STUDIO',
        displayName: 'Can view data studio',
      },
      {
        resourceId: 'global',
        privilegeId: 'WRITE_INSIGHTS',
        displayName: 'Can edit insights',
      },
    ],
    users: [
      {
        id: '34u44898yr',
        firstName: 'Bob',
        lastName: 'Ross',
        email: 'Bob@syncari.com',
      },
      {
        id: '34u44898ywqr',
        firstName: 'Adam',
        lastName: 'Smith',
        email: 'adam@syncari.com',
      },
    ],
    system: false,
    tags: ['tag1', 'tag2', 'tag3'],
  },
  {
    id: '34824267yriuw',
    name: 'IT Admin 2',
    description: 'IT Admin team 2',
    status: 'Active',
    privileges: [
      {
        resourceId: 'global', //Ignore this for now. This will be used in future
        privilegeId: 'READ_DATA_STUDIO',
        displayName: 'Can view data studio',
      },
      {
        resourceId: 'global',
        privilegeId: 'WRITE_INSIGHTS',
        displayName: 'Can edit insights',
      },
    ],
    users: [
      {
        id: '34u44898yr',
        firstName: 'adam',
        lastName: 'smith',
        email: 'adam@syncari.com',
      },
      {
        id: '34u44898ywqr',
        firstName: 'Bob',
        lastName: 'Ross',
        email: 'Bob@syncari.com',
      },
    ],
    system: false,
    tags: ['tag1', 'tag2', 'tag3'],
  },
];

export const rbacRoleDetailFixture: UserRole = {
  id: '5345',
  name: 'IT Admin',
  description: 'IT Admin team',
  active: true,
  privileges: [{ resourceId: 'string', privilegeId: 'string', displayName: 'string' }],
  users: [
    {
      clientId: 'null',
      clientSecret: 'null',
      createdAt: 'null',
      createdBy: 'null',
      currentInstanceName: 'null',
      currentInstanceNextEdgeId: 'null',
      currentInstanceType: 'sandbox',
      email: 'com',
      firstName: '',
      id: '62d1b97590d76513885e9bcf',
      isApiUser: false,
      isGhostUser: false,
      isSuperAdmin: false,
      syncariDev: false,
      lastName: '',
      orgAdmin: false,
      orgId: 'null',
      orgLogo: 'null',
      orgName: 'null',
      orgType: 'standard',
      passwordExpired: false,
      status: 'null',
      timeZone: 'null',
      updatedAt: 'null',
      updatedBy: 'null',
      userRoles: {},
      privileges: ['', ''],
      userType: 'STANDARD',
    },
  ],
  system: false,
  tags: ['tag1', 'tag2', 'tag3'],
};

export const allInstancesAllRoles = {
  ABCDEF: [rbacRoleDetailFixture],
};
