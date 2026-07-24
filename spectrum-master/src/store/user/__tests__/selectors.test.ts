// @ts-nocheck
import CapConstants from 'utils/CapConstants';

import { selectCurrentInstance, selectOrg, selectUserName, selectCurrentInstanceUserRoles } from '../selectors';

describe('selectUserName', () => {
  test('returns first name', () => {
    const state = {
      user: {
        firstName: 'Test',
        lastName: 'User',
        email: 'test@syncari.com',
      },
    };

    expect(selectUserName(state)).toBe('Test');
  });

  test('strips any whitespace from first name', () => {
    const state = {
      user: {
        firstName: ' Test',
        lastName: 'User ',
        email: 'test@syncari.com',
      },
    };

    expect(selectUserName(state)).toBe('Test');
  });

  test('returns first name only if last name is null', () => {
    const state = {
      user: {
        firstName: 'Test',
        lastName: null,
        email: 'test@syncari.com',
      },
    };

    expect(selectUserName(state)).toBe('Test');
  });

  test('returns email if first name is empty', () => {
    const state = {
      user: {
        firstName: null,
        lastName: null,
        email: 'test@syncari.com',
      },
    };

    expect(selectUserName(state)).toBe('test@syncari.com');
  });

  test('returns null if first name and email are empty', () => {
    const state = {
      user: {
        firstName: null,
        lastName: null,
        email: null,
      },
    };

    expect(selectUserName(state)).toBe(null);
  });
});

describe('selectOrg', () => {
  test('returns org data', () => {
    const orgId = '124124515212';
    const orgName = 'Test Organization';
    const orgLogo = '/images/assets/orglogo.png';

    const state = {
      user: {
        orgId,
        orgName,
        orgLogo,
      },
    };

    const selectedOrg = selectOrg(state);

    expect(selectedOrg.id).toBe(orgId);
    expect(selectedOrg.name).toBe(orgName);
    expect(selectedOrg.logo).toBe(orgLogo);
  });
});

describe('selectCurrentInstance', () => {
  test('returns current instance', () => {
    const currentInstanceNextEdgeId = '122124124515';
    const currentInstanceName = 'Production';

    const state = {
      user: {
        currentInstanceName,
        currentInstanceNextEdgeId,
      },
    };

    const selectedInstance = selectCurrentInstance(state);

    expect(selectedInstance.id).toBe(currentInstanceNextEdgeId);
    expect(selectedInstance.name).toBe(currentInstanceName);
  });
});

describe('selectCurrentInstanceUserRoles', () => {
  test('empty userRoles', () => {
    const state = {
      user: {
        userRoles: {},
      },
    };

    expect(selectCurrentInstanceUserRoles(state)).toEqual([]);
  });

  test('ADMIN, VIEWER userRoles', () => {
    const state = {
      user: {
        currentInstanceNextEdgeId: '12415asfda',
        userRoles: {
          '12415asfda': [CapConstants.VIEWER, CapConstants.ADMIN],
        },
      },
    };

    expect(selectCurrentInstanceUserRoles(state)).toEqual([CapConstants.VIEWER, CapConstants.ADMIN]);
  });

  test('VIEWER userrole', () => {
    const state = {
      user: {
        currentInstanceNextEdgeId: '12fda415as',
        userRoles: {
          '12fda415as': [CapConstants.VIEWER],
        },
      },
    };

    expect(selectCurrentInstanceUserRoles(state)).toEqual([CapConstants.VIEWER]);
  });
});
