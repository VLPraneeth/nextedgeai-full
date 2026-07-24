import * as Reach from '@reach/router';
import { dataCard1 } from 'mocks/fixtures/insights';

import { render, screen, userEvent } from 'tests/helpers';
import { AllPermissions } from 'utils/PermissionsConstants';

import TitleBar, { TitleBarProps } from './TitleBar';

jest.spyOn(Reach, 'useMatch').mockImplementation(jest.fn());

const renderComponent = (props?: Partial<TitleBarProps>, permissions?: string[]) =>
  render(<TitleBar name={dataCard1.displayName} description={dataCard1.description} dashboardId="dash1" {...props} />, {
    testState: {
      user: {
        privileges: permissions ?? [AllPermissions.UPDATE_DASHBOARD, AllPermissions.UPDATE_DATACARD],
      },
    },
  });

describe('DataCardTitleBar', () => {
  it('renders the card name and description', async () => {
    renderComponent();

    expect(screen.getByText(dataCard1.displayName)).toBeVisible();
    expect(screen.getByRole('button', { name: 'View full size' })).toBeVisible();
    expect(screen.queryByRole('button', { name: 'Data card options' })).not.toBeInTheDocument();

    await userEvent.hover(screen.getByTestId('description-tooltip-icon'));
    expect(await screen.findByText(dataCard1.description)).toBeVisible();
  });

  it('shows a config option when specified in props', async () => {
    renderComponent({ showConfigButton: true });

    await userEvent.click(screen.getByRole('button', { name: 'Data card options' }));

    expect(screen.getByRole('menuitem', { name: 'Configure variables' })).toBeVisible();
  });

  it('shows a remove from dashboard option when remove function supplied', async () => {
    const removeSpy = jest.fn();
    renderComponent({ removeFromDashboard: removeSpy, showEditControls: true });

    await userEvent.click(screen.getByRole('button', { name: 'Data card options' }));
    await userEvent.click(screen.getByRole('menuitem', { name: 'Remove from dashboard' }));

    expect(removeSpy).toHaveBeenCalledTimes(1);
  });

  it('Shows edit data card option', async () => {
    renderComponent({ showEditControls: true, dataCard: dataCard1 });

    await userEvent.click(screen.getByRole('button', { name: 'Data card options' }));
    expect(screen.getByRole('menuitem', { name: 'Edit card' })).toBeVisible();
  });

  it("doesn't show remove or edit control if user doesn't have required permission", async () => {
    renderComponent(
      { showConfigButton: true, showEditControls: true, dataCard: dataCard1, removeFromDashboard: jest.fn() },
      []
    );

    await userEvent.click(screen.getByRole('button', { name: 'Data card options' }));
    expect(screen.queryByRole('menuitem', { name: 'Remove from dashboard' })).not.toBeInTheDocument();
    expect(screen.queryByRole('menuitem', { name: 'Edit card' })).not.toBeInTheDocument();
  });
});
