import { act } from '@testing-library/react';

// import * as Actions from 'actions/subscriptionActions';
import { render, userEvent, screen, mockedAjaxUtils, waitFor } from 'tests/helpers';
import { fireEvent } from '@testing-library/react';
import CapConstants from 'utils/CapConstants';
import { AllPermissions } from 'utils/PermissionsConstants';

import Branding from '../Branding';

jest.mock('utils/AjaxUtil');
const ajaxMock = mockedAjaxUtils();
ajaxMock.put.mockImplementation(() => Promise.resolve());
// ajaxMock.deleteRequest.mockImplementation(() => Promise.resolve());

const renderBrnading = (options?: any) => {
  return render(<Branding />, {
    testState: {
      user: {
        orgId: '123456',
        userRoles: { asdf: options?.roles ?? [CapConstants.SUPER_ADMIN] },
        currentInstanceNextEdgeId: 'asdf',
        privileges: [AllPermissions.BRANDING_EDIT],
      },
    },
  });
};

describe('Branding', () => {
  it('sets page title', () => {
    renderBrnading();

    expect(window.document.title.includes('Branding')).toBe(true);
  });

  it('should render Company logo (Wide) section', async () => {
    renderBrnading();

    expect(await screen.findByText('Company Logo (Wide)')).toBeInTheDocument();
  });

  it('should render Company logo (Square) section', async () => {
    renderBrnading();

    expect(await screen.findByText('Company Logo (Square)')).toBeInTheDocument();
  });

  it('should render Brand Name section', async () => {
    renderBrnading();

    expect(await screen.findByText('Brand Name')).toBeInTheDocument();
  });

  it('has a username field that changes value on text entry', () => {
    const { container } = renderBrnading();
    fireEvent.change(container.querySelector(`input[name="brandName"]`), {
      target: { value: 'user' },
    });
    expect(container.querySelector(`input[value="user"]`)).toBeInTheDocument();
  });

  it('should render Brand Color section', async () => {
    renderBrnading();

    expect(await screen.findByText('Brand Color')).toBeInTheDocument();
  });

  it('should have Reset button', async () => {
    renderBrnading();

    expect(await screen.findByText('Reset to defaults')).toBeInTheDocument();
  });

  it('should have Save changes button', async () => {
    renderBrnading();

    expect(await screen.findByText('Save changes')).toBeInTheDocument();
  });

  it('Save changes button should get disabled when Brand name field is empty.', async () => {
    const { container } = renderBrnading();
    fireEvent.change(container.querySelector(`input[name="brandName"]`), {
      target: { value: '' },
    });

    const button = screen.getByRole('button', { name: /Save changes/i });
    expect(button).toBeDisabled();
  });

  it('Save changes button click calls the branding udpate PUT API', async () => {
    renderBrnading();
    await userEvent.click(screen.getByText('Save changes'));

    await waitFor(() => {
      expect(ajaxMock.put).toHaveBeenCalledTimes(1);
    });
  });

  it('Reset button click calls the Reset DELETE API', async () => {
    renderBrnading();
    await userEvent.click(screen.getByText('Reset to defaults'));
    await waitFor(() => {
      expect(ajaxMock.deleteRequest).toHaveBeenCalledTimes(0);
    });
  });
});
