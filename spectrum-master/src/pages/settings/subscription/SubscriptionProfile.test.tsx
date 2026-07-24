import { act } from '@testing-library/react';

import * as Actions from 'actions/subscriptionActions';
import { render, userEvent, screen } from 'tests/helpers';
import CapConstants from 'utils/CapConstants';
import { AllPermissions } from 'utils/PermissionsConstants';

import SubscriptionProfile from './SubscriptionProfile';

const renderSubProfile = (options?: any) =>
  render(<SubscriptionProfile />, {
    testState: {
      user: {
        orgId: '123456',
        userRoles: { asdf: options?.roles ?? [CapConstants.SUPER_ADMIN] },
        currentInstanceNextEdgeId: 'asdf',
        privileges: [AllPermissions.SUB_EDIT],
      },
    },
  });

describe('Subscription Profile', () => {
  it('sets page title', () => {
    renderSubProfile();

    expect(window.document.title.includes('Subscription Profile')).toBe(true);
  });

  it('can trigger update of profile data', () => {
    const updateProfile = jest.spyOn(Actions, 'updateProfile');
    renderSubProfile();

    act(async () => {
      await userEvent.type(screen.getByLabelText('Subscription Name'), 'test');
      await userEvent.click(screen.getByText('Save Changes'));

      expect(updateProfile).toHaveBeenCalledWith({ name: 'test', id: '123456', type: '' });
    });
  });
});
