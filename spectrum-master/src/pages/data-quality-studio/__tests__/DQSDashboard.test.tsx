import { RootState } from 'reducers';
import { thunks } from 'store/new-dashboard/slice';
import { makeElementNotFoundError, render, renderWithRouter, screen, userEvent } from 'tests/helpers';
import AppConstants from 'utils/AppConstants';
import { capitalize } from 'utils/Fp';
import RouteConstants from 'utils/RouteConstants';

import DataQualityStudioRoot, { DQS_DASHBOARDS_KEY, DQS_ROOT_DASHBOARD_ID } from '../index';
import { WidgetComponentType } from '../types';
import WidgetCard from '../WidgetCard';

describe('DQS Dashboards', () => {
  test('renders, requests dashboard list', async () => {
    const getDashboardsSpy = jest.spyOn(thunks, 'getDashboards');
    const getDashboardSpy = jest.spyOn(thunks, 'getDashboard');

    renderWithRouter(<DataQualityStudioRoot />, {
      route: RouteConstants.DATA_QUALITY_STUDIO_ROOT,
    });

    expect(getDashboardsSpy).toHaveBeenCalledWith(DQS_DASHBOARDS_KEY);
    expect(getDashboardSpy).toHaveBeenCalledWith(DQS_ROOT_DASHBOARD_ID);
  });

  test('Can select dashboard from list', async () => {
    const getDashboardSpy = jest.spyOn(thunks, 'getDashboard');

    const overviewDashboard = {
      entityApiName: DQS_ROOT_DASHBOARD_ID,
      name: DQS_ROOT_DASHBOARD_ID,
      title: 'DQS Overview',
      widgets: [],
    };

    const accountDashboard = {
      entityApiName: '2',
      name: 'account',
      title: 'Account',
      widgets: [],
    };

    renderWithRouter(<DataQualityStudioRoot />, {
      route: RouteConstants.DATA_QUALITY_STUDIO_ROOT,
      testState: {
        // TODO: need to allow testState to accept partial of partials, I guess?
        // @ts-ignore
        newDashboard: {
          dashboardsByCategory: {
            dqs: [DQS_ROOT_DASHBOARD_ID, accountDashboard.entityApiName],
          },
          dashboardsByCategoryStatus: {
            [DQS_DASHBOARDS_KEY]: AppConstants.FETCH_STATUS.SUCCESS,
          },
          dashboards: {
            [DQS_ROOT_DASHBOARD_ID]: overviewDashboard,
            [accountDashboard.entityApiName]: accountDashboard,
          },
        },
      },
    });

    expect(getDashboardSpy).toHaveBeenCalledWith(overviewDashboard.name);
    const dashboardItem = await screen.findByText(overviewDashboard.title);
    expect(dashboardItem).toBeInTheDocument();
    // open selector
    await userEvent.click(dashboardItem);

    // open new dashboard
    await userEvent.click(await screen.findByText('Account (2)'));
  });

  test('widget doesnt crash without data', async () => {
    const widgetKey = 'DQS:test';

    render(<WidgetCard dashboardName="DQS" name="test" title="Test Widget" />, {
      testState: {
        newDashboard: {
          widgetStatus: {
            [widgetKey]: AppConstants.FETCH_STATUS.SUCCESS,
          },
          widgets: {
            // purposely render invalid widget
            // @ts-ignore
            [widgetKey]: null,
          },
        },
      },
    });

    expect(await screen.findByText('Test Widget')).toBeInTheDocument();
    expect(await screen.findByText('Oops!')).toBeInTheDocument();
    expect(await screen.findByText('There was an issue rendering this widget.')).toBeInTheDocument();
  });

  const makeWidgetDataState = (
    componentType: WidgetComponentType,
    data: any[] = [],
    config: any[] = [],
    contents = undefined,
    dashboardName = btoa((Math.random() * 1000000).toString()),
    widgetName = btoa((Math.random() * 1241258).toString())
  ): [{ dashboardName: string; widgetName: string; title: string }, Partial<RootState['newDashboard']>] => {
    const title = `${capitalize(componentType)} Widget`;
    const widgetKey = `${dashboardName}:${widgetName}`;

    // returns props for WidgetCard and testState
    return [
      {
        dashboardName,
        widgetName,
        title,
      },
      {
        // @ts-ignore
        widgets: {
          [widgetKey]: {
            id: widgetKey,
            name: widgetName,
            title,
            layout: {
              i: widgetKey,
              x: 0,
              y: 0,
              w: 6,
              h: 4,
              resizable: false,
            },
            loadingText: 'Loading widget…',
            contents: [
              {
                name: widgetName,
                component: componentType,
                data,
                config,
              },
            ],
          },
        },
        widgetStatus: {
          [widgetKey]: AppConstants.FETCH_STATUS.SUCCESS,
        },
      },
    ];
  };

  test('DataScoreLineItems Widget', async () => {
    const data = [
      {
        id: 1,
        card: {
          label: 'Account',
          score: 78,
          entityName: 'Account',
          factors: [
            {
              averageScore: 87,
              category: 'bottom',
              description: '',
              entityId: '12244j1k',
              label: 'Rule Label 1',
              fieldName: 'Last Name',
              ruleId: '12345',
              filterCondition: {
                predicateId: '1241412',
                name: 'Predicate',
                left: 'lhs',
                operator: 'eq',
                right: 'rhs',
              },
            },
            {
              averageScore: 43,
              category: 'bottom',
              description: '',
              entityId: '124j1k24',
              label: 'Rule Label 2',
              fieldName: 'First Name',
              ruleId: '1234',
              filterCondition: {
                predicateId: '1241241',
                name: 'Predicate',
                left: 'lhs',
                operator: 'eq',
                right: 'rhs',
              },
            },
          ],
        },
      },
      {
        id: 2,
        card: {
          label: 'Lead',
          entityName: 'Lead',
          score: 41,
          factors: [
            {
              averageScore: 11,
              category: 'bottom',
              description: '',
              entityId: '122449f9f9fj1k',
              label: 'Rule Label 3',
              fieldName: 'Company Name',
              ruleId: '123ijijif45',
              filterCondition: {
                predicateId: '124asd1412',
                name: 'Predicate',
                left: 'lhs',
                operator: 'eq',
                right: 'rhs',
              },
            },
            {
              averageScore: 77,
              category: 'bottom',
              description: '',
              entityId: '124j1k24asdf',
              label: 'Rule Label 4',
              fieldName: 'Address',
              ruleId: '1234789',
              filterCondition: {
                predicateId: 'asdf1241241',
                name: 'Predicate',
                left: 'lhs',
                operator: 'eq',
                right: 'rhs',
              },
            },
          ],
        },
      },
    ];

    const [props, testState] = makeWidgetDataState(WidgetComponentType.DATASCORE_LINE_ITEMS, data);

    // we use the useNavigate hook here, so we need the router context
    renderWithRouter(<WidgetCard dashboardName={props.dashboardName} name={props.widgetName} title={props.title} />, {
      testState: {
        // @ts-ignore
        newDashboard: {
          ...testState,
        },
      },
    });

    // find 2 line items
    expect(await screen.findByText('Account')).toBeInTheDocument();
    expect(await screen.findByText(data[0].card.score)).toBeInTheDocument();
    expect(await screen.findByText('Lead')).toBeInTheDocument();
    expect(await screen.findByText(data[1].card.score)).toBeInTheDocument();

    // check for Account breakdown (it should be expanded)
    expect(await screen.findByText(data[0].card.factors[0].label)).toBeInTheDocument();
    expect(await screen.findByText(data[0].card.factors[0].averageScore)).toBeInTheDocument();
    expect(await screen.findByText(data[0].card.factors[1].label)).toBeInTheDocument();
    expect(await screen.findByText(data[0].card.factors[1].averageScore)).toBeInTheDocument();

    // check that Lead breakdown is not visible (it should be collapsed)
    await expect(screen.findByText(data[1].card.factors[0].label)).rejects.toThrow(
      makeElementNotFoundError(data[1].card.factors[0].label)
    );
    await expect(screen.findByText(data[1].card.factors[0].averageScore)).rejects.toThrow(
      makeElementNotFoundError(data[1].card.factors[0].averageScore.toString())
    );
    await expect(screen.findByText(data[1].card.factors[1].label)).rejects.toThrow(
      makeElementNotFoundError(data[1].card.factors[1].label)
    );
    await expect(screen.findByText(data[1].card.factors[1].averageScore)).rejects.toThrow(
      makeElementNotFoundError(data[1].card.factors[1].averageScore.toString())
    );

    await userEvent.click(await screen.findByRole('button', { name: 'toggle collapsable section: Lead' }));

    // now we should see the Lead data
    expect(await screen.findByText(data[1].card.factors[0].label)).toBeInTheDocument();
    expect(await screen.findByText(data[1].card.factors[0].averageScore)).toBeInTheDocument();
    expect(await screen.findByText(data[1].card.factors[1].label)).toBeInTheDocument();
    expect(await screen.findByText(data[1].card.factors[1].averageScore)).toBeInTheDocument();
  });

  test('Table Widget', async () => {
    const data = [
      {
        id: 1,
        apiName: 'lead',
      },
      {
        id: 2,
        apiName: 'account',
      },
      {
        id: 3,
        apiName: 'contact',
      },
    ];

    const [props, testState] = makeWidgetDataState(WidgetComponentType.TABLE, data, [
      {
        pageInfo: null,
        metadata: {
          columns: ['id', 'apiName'],
          fields: {
            id: {
              dataType: 'string',
              label: 'Id',
            },
            apiName: {
              dataType: 'string',
              label: 'Api Name',
            },
          },
        },
      },
    ]);

    render(<WidgetCard dashboardName={props.dashboardName} name={props.widgetName} title={props.title} />, {
      testState: {
        // @ts-ignore
        newDashboard: {
          ...testState,
        },
      },
    });

    expect(await screen.findByText('Id')).toBeInTheDocument();
    expect(await screen.findByText('Api Name')).toBeInTheDocument();

    // ensure all of our data is rendered
    for (const value of data.flatMap(Object.values)) {
      expect(await screen.findByText(value.toString())).toBeInTheDocument();
    }
  });

  test('Empty State widget', async () => {
    const [props, testState] = makeWidgetDataState(WidgetComponentType.EMPTY_STATE, [
      {
        title: 'Empty',
        description: 'This is an empty state',
      },
    ]);

    render(<WidgetCard dashboardName={props.dashboardName} name={props.widgetName} title={props.title} />, {
      testState: {
        // @ts-ignore
        newDashboard: {
          ...testState,
        },
      },
    });

    expect(await screen.findByText('Empty')).toBeInTheDocument();
    expect(await screen.findByText('This is an empty state')).toBeInTheDocument();
  });

  test('Horizontal Gauge widget', async () => {
    const [props, testState] = makeWidgetDataState(WidgetComponentType.HORIZONTAL_GAUGE, [
      {
        label: 'Excellent',
        value: 92,
      },
    ]);

    render(<WidgetCard dashboardName={props.dashboardName} name={props.widgetName} title={props.title} />, {
      testState: {
        // @ts-ignore
        newDashboard: {
          ...testState,
        },
      },
    });

    expect(await screen.findByText('92')).toBeInTheDocument();
    expect(await screen.findByText('Excellent')).toBeInTheDocument();
  });

  test('Trend Badge widget', async () => {
    const [props, testState] = makeWidgetDataState(WidgetComponentType.TREND_BADGE, [
      {
        trendDirection: 'up',
        value: 'Up 23%',
      },
    ]);

    render(<WidgetCard dashboardName={props.dashboardName} name={props.widgetName} title={props.title} />, {
      testState: {
        // @ts-ignore
        newDashboard: {
          ...testState,
        },
      },
    });

    expect(await screen.findByText('Up 23%')).toBeInTheDocument();
    expect(await screen.findByTestId(`trend-badge-up`)).toBeInTheDocument();
  });

  /*
   * Can't test the rendering of these widgets because JSDom is missing some SVG fns :/
   * We could mock these and assert specific data but it would only serve to test our mock is right, not the component itself
   * - PieChart
   * - Gauge
   * - LineChart
   *
   */
});
