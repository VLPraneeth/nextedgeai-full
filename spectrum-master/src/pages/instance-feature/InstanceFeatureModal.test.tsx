//
// Copyright (c) 2019-Present Syncari All rights reserved.
//

import { getFeatures } from 'pages/instance-feature/InstanceFeature.fixtures';
import * as hooks from 'store/instance-feature/api';
import { render, screen, userEvent, waitFor } from 'tests/helpers';

import InstanceFeatureModal from './InstanceFeatureModal';

const mockedUseGetFeaturesQuery = {
  isLoading: false,
  isFetching: false,
  data: getFeatures(),
  refetch: () => {},
};

const mockedSuccess = () => {
  return [
    () => ({
      unwrap: jest.fn().mockResolvedValue(true),
    }),
  ];
};

describe('Instance Modal', () => {
  it('should render the modal without any issues', async () => {
    jest.spyOn(hooks, 'useGetFeaturesQuery').mockImplementation(() => mockedUseGetFeaturesQuery);

    render(<InstanceFeatureModal visible show={() => {}} />);
    expect(screen.queryByText('Features')).toBeVisible();
    expect(await screen.findByText('Insights')).toBeInTheDocument();
    expect(await screen.findByText('Data Store')).toBeInTheDocument();
    expect(await screen.findByText('InsightsAdvanceDataset')).toBeInTheDocument();
  });

  it('should should close the modal on cancel', async () => {
    const cancelSpy = jest.fn();
    jest.spyOn(hooks, 'useGetFeaturesQuery').mockImplementation(() => mockedUseGetFeaturesQuery);

    render(<InstanceFeatureModal visible show={cancelSpy} />);

    await userEvent.click(screen.getByText('Cancel'));
    expect(cancelSpy).toHaveBeenCalledWith(false);
  });

  it('should should show empty message', async () => {
    const cancelSpy = jest.fn();
    jest.spyOn(hooks, 'useGetFeaturesQuery').mockImplementation(() => ({ ...mockedUseGetFeaturesQuery, data: [] }));

    render(<InstanceFeatureModal visible show={cancelSpy} />);
    expect(await screen.findByText('No available features')).toBeInTheDocument();

    await userEvent.click(screen.getByText('Close'));
    expect(cancelSpy).toHaveBeenCalledWith(false);
  });

  it('should should show empty message with a hidden feature', async () => {
    const cancelSpy = jest.fn();
    jest.spyOn(hooks, 'useGetFeaturesQuery').mockImplementation(() => ({
      ...mockedUseGetFeaturesQuery,
      data: [
        {
          name: 'Datastore',
          displayName: 'Data Store',
          description: 'Makes a PostgreSQL data warehouse available',
          stage: 'GA',
          status: 'active',
          params: null,
          hidden: true,
          enabled: true,
        },
      ],
    }));

    render(<InstanceFeatureModal visible show={cancelSpy} />);
    expect(await screen.findByText('No available features')).toBeInTheDocument();

    await userEvent.click(screen.getByText('Close'));
    expect(cancelSpy).toHaveBeenCalledWith(false);
  });

  it('should enable insights advance dataset feature and submit successfully', async () => {
    const cancelSpy = jest.fn();
    jest.spyOn(hooks, 'useGetFeaturesQuery').mockImplementation(() => mockedUseGetFeaturesQuery);
    jest.spyOn(hooks, 'useEnableFeatureMutation').mockImplementation(mockedSuccess as any);
    jest.spyOn(hooks, 'useDisableFeatureMutation').mockImplementation(mockedSuccess as any);

    render(<InstanceFeatureModal visible show={cancelSpy} />);

    expect(await screen.findByText('Features')).toBeInTheDocument();

    const insightsCheckbox = screen.getByTestId<HTMLInputElement>('instanceFeatureInsightsAdvanceDataset');
    await userEvent.click(insightsCheckbox);
    expect(insightsCheckbox.checked).toEqual(true);
    await userEvent.click(screen.getByText('Apply'));
    await waitFor(() => expect(cancelSpy).toHaveBeenCalledWith(false));
  });

  it('should disable insights advance dataset feature and submit successfully', async () => {
    const cancelSpy = jest.fn();
    jest.spyOn(hooks, 'useGetFeaturesQuery').mockImplementation(() => mockedUseGetFeaturesQuery);

    render(<InstanceFeatureModal visible show={cancelSpy} />);

    const insightsCheckbox = screen.getByTestId<HTMLInputElement>('instanceFeatureInsightsAdvanceDataset');
    await userEvent.click(insightsCheckbox);
    expect(insightsCheckbox.checked).toEqual(true);
    await userEvent.click(insightsCheckbox);
    expect(insightsCheckbox.checked).toEqual(false);
    await userEvent.click(screen.getByText('Apply'));
    await waitFor(() => expect(cancelSpy).toHaveBeenCalledWith(false));
  });
});
