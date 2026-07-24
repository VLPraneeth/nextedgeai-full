import * as Reach from '@reach/router';
import { dataCard1, dash1 } from 'mocks/fixtures/insights';
import { dataCard2 } from 'mocks/fixtures/insights/dataCard2';
import { Globals } from 'react-spring';

import configureAppStore from 'store/configureStore';
import { render, screen, userEvent } from 'tests/helpers';

import { DataCard, DataCardProps } from './DataCard';

// Skip react-spring animations, animated components will render in their end state.
Globals.assign({ skipAnimation: true });

// Prevent needing to wrap component in router due to useMatch in child components not needed for this test suite
jest.spyOn(Reach, 'useMatch').mockImplementation(jest.fn());

const renderComponent = (overrides?: Partial<DataCardProps>) =>
  render(
    <DataCard
      dashboardId={dash1.id}
      description={dataCard1.description}
      id={dataCard1.id}
      name={dataCard1.displayName}
      layout={{ w: 3, h: 2, x: 0, y: 0 }}
      {...overrides}
    />,
    {
      store: configureAppStore(),
      testState: {
        insightsStudio: { userDataCardConfig: {} },
      },
    }
  );

describe('DataCard', () => {
  it('renders the card name and content', async () => {
    renderComponent();

    expect(await screen.findByText(dataCard1.displayName)).toBeVisible();
    expect(await screen.findByTestId('data-card-contents')).toBeVisible();
    expect(screen.queryByRole('button', { name: 'Configure variables' })).not.toBeInTheDocument();
  });

  it('renders config button when configurationMeta is present', async () => {
    renderComponent({ id: 'testDataCard', dashboardId: 'testDashboard' });
    await userEvent.click(await screen.findByRole('button', { name: 'Data card options' }));
    expect(await screen.findByRole('menuitem', { name: 'Configure variables' })).toBeVisible();
  });

  it('renders an error message with fallback name and description from props when card request fails', async () => {
    renderComponent({ id: 'fakeCard', description: 'Fallback description', name: 'Fallback Name' });

    expect(screen.queryByText(dataCard1.displayName)).not.toBeInTheDocument();
    expect(screen.queryByText(dataCard1.description)).not.toBeInTheDocument();
    expect(screen.getByText('Fallback Name')).toBeVisible();

    await userEvent.hover(screen.getByTestId('description-tooltip-icon'));
    expect(await screen.findByText('Fallback description')).toBeVisible();
    expect(await screen.findByText('Something went wrong')).toBeVisible();
  });

  it('renders a custom error message from contents.data.error', async () => {
    renderComponent({ id: 'dataCard2' });

    expect(await screen.findByText(dataCard2.displayName!)).toBeVisible();
    expect(await screen.findByText(dataCard2.contents?.data?.error?.title!)).toBeVisible();
    expect(await screen.findByText(dataCard2.contents?.data?.error?.body!)).toBeVisible();
  });
});
