//
// Copyright (c) 2019-Present Syncari All rights reserved.
//

import { getByLabelText } from '@testing-library/react';

import { Instance } from 'store/instances/slice';
import { render, screen, userEvent, waitFor } from 'tests/helpers';

import RequestGhostAccessModal from './RequestGhostAccessModal';

// Need to start at a certain point in the screen
// eslint-disable-next-line testing-library/prefer-screen-queries
const getPicklistArrowByTestId = (testId: string) => getByLabelText(screen.getByTestId(testId), 'icon: down');

describe('Request Ghost Access Modal', () => {
  it('should render the modal without any issues', () => {
    render(<RequestGhostAccessModal visible setVisible={(visible) => {}} instance={null} />, {
      testState: {
        user: {},
      },
    });
    expect(screen.queryByText('Request Ghost Access')).toBeVisible();
  });

  it('should should close the modal on cancel', async () => {
    const cancelClickSpy = jest.fn();
    render(<RequestGhostAccessModal visible setVisible={cancelClickSpy} instance={null} />, {
      testState: {
        user: {},
      },
    });

    await userEvent.click(screen.getByText('Cancel'));
    expect(cancelClickSpy).toHaveBeenCalledWith(false);
  });

  it('should fill up the form and submit successfully', async () => {
    const closeClickSpy = jest.fn();
    render(
      <RequestGhostAccessModal
        visible
        setVisible={closeClickSpy}
        instance={
          {
            syncariId: 'syncari_admin',
            name: 'Syncari Admin',
            displayName: 'Syncari Admin',
            features: ['sd'],
            orgId: 'acme',
            orgName: 'Acme',
            planName: 'sandbox',
            planId: '1234',
            status: 'ACTIVE',
            type: 'production',
            active: true,
            trial: false,
            quota: [
              {
                connectorId: null,
                createdAt: null,
                createdBy: null,
                id: '1234',
                updatedAt: null,
                updatedBy: null,
                type: 'TRIAL_DAYS_LIMIT',
                value: 'value',
              },
            ],
          } as Instance
        }
      />,
      {
        testState: {
          user: {
            allRoles: [
              {
                id: '6210512b3c21d15d2e096f52',
                name: 'Viewer',
              },
            ],
          },
        },
      }
    );

    await userEvent.click(getPicklistArrowByTestId('roleId'));
    await userEvent.click(screen.getByText('Viewer'));

    await userEvent.click(getPicklistArrowByTestId('accessReason'));
    await userEvent.click(screen.getByText('Data Related Activities'));

    await userEvent.click(screen.getByText('Request'));
    await waitFor(() => expect(closeClickSpy).toHaveBeenCalledWith(false));
  });
});
