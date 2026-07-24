import {
  allInstancesAllRoles,
  rbacRoleDetailFixture,
  rbacTableFixture,
} from 'mocks/fixtures/settings/roleBasedAccessControl';
import { rest } from 'msw';

import DataUrlConstants from 'utils/DataUrlConstants';

const handlers = [
  rest.get(DataUrlConstants.SETTINGS_RBAC_ALL_ROLES, (req, res, ctx) => {
    return res(ctx.status(200), ctx.json(rbacTableFixture));
  }),
  rest.get(DataUrlConstants.SETTINGS_RBAC_ROLE_DETAILS, (req, res, ctx) => {
    return res(ctx.status(200), ctx.json(rbacRoleDetailFixture));
  }),
  rest.get(DataUrlConstants.GET_ALL_ROLES_ALL_INSTANCES, (req, res, ctx) => {
    return res(ctx.status(200), ctx.json(allInstancesAllRoles));
  }),
];

export default handlers;
