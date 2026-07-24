import { getDefaultConnectorState, getEmptyConnector } from 'store/connectors';
import { getCurrentGraphFixture } from 'store/pipeline/fixtures';
import { getEmptyTestRun } from 'store/test';
import { TestRunModel } from 'store/test/types';
import { render, screen, userEvent } from 'tests/helpers';
import { tNamespaced } from 'utils/i18nUtil';

import TestResultCriteria from '../TestResultCriteria';

const tn = tNamespaced('TestResultContent');

const renderComponent = (testRunParams: Partial<TestRunModel>) =>
  render(<TestResultCriteria testRun={getEmptyTestRun(testRunParams)} />, {
    testState: {
      // Using Arizona TZ to avoid tests failing due to changes from Daylight Savings Time
      user: { timeZone: 'US/Arizona' },
      connector: getDefaultConnectorState({
        connectors: [getEmptyConnector({ connectorId: '5edfd92cfee0d800011e255d', name: 'Salesforce' })],
      }),
      entityPipeline: {
        entityPipeline: {
          ...getCurrentGraphFixture(),
          draft: {
            nodes: getCurrentGraphFixture().draft.nodes.map((node) => ({
              ...node,
              apiName: 'account',
              name: 'Account',
              subLabel: 'Salesforce',
              configuration: {
                ...node.configuration,
                entityDefinition: '123',
              },
            })),
          },
        },
      },
    },
  });

describe('TestResultCriteria', () => {
  it('should return date range criteria when dates are provided', async () => {
    // match date format to what comes from backend
    const startTime = '04/01/2022 12:00 PM';
    const endTime = '04/08/2022 07:30 PM';

    renderComponent({ startTime, endTime });

    // Displayed dates should be 7 hours behind dates above, converted from UTC
    // to Arizona/Mountain Standard Time
    expect(await screen.findByText(`Start Date: 4/1/2022 5:00:00 AM MST`)).toBeVisible();
    expect(await screen.findByText(`End Date: 4/8/2022 12:30:00 PM MST`)).toBeVisible();
  });

  it('should return list of external ids when provided', async () => {
    renderComponent({
      recordIds: {
        '123': ['0011b00000uyqaDAAQ', '0011b00000vKNMkAAO'],
      },
    });

    expect(screen.getAllByText('Salesforce / Account (account):')[0]).toBeVisible();

    expect(await screen.findByText('0011b00000uyqaDAAQ')).toBeVisible();
  });

  it('should support an expanded list of ids when more than 4 are provided', async () => {
    renderComponent({
      recordIds: {
        '123': [
          '0011b00000uyqaDAAQ',
          '0011b00000vKNMkAAO',
          '0011b00000vKNMpAAO',
          '0011b00000w5zGiAAI',
          '0011b00000w5zGlAAI',
          '0011b00000w5zGoAAI',
          '0011b00000w5zGsAAI',
        ],
      },
    });

    expect(await screen.findByText(tn('more_external_ids', { count: 4 }))).toBeVisible();
  });

  it('should expand the list when more button is pressed', async () => {
    renderComponent({
      recordIds: {
        '123': [
          '0011b00000uyqaDAAQ',
          '0011b00000vKNMkAAO',
          '0011b00000vKNMpAAO',
          '0011b00000w5zGiAAI',
          '0011b00000w5zGlAAI',
          '0011b00000w5zGoAAI',
          'last_id',
        ],
      },
    });

    const moreButton = await screen.findByText(tn('more_external_ids', { count: 4 }));

    await userEvent.click(moreButton);

    expect(screen.queryByText(tn('more_external_ids', { count: 4 }))).toBeNull();

    expect(screen.getAllByText('Salesforce / Account (account):')[0]).toBeVisible();

    expect(await screen.findByText('last_id')).toBeVisible();
  });
});
