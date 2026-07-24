import moment from 'moment';

import * as InstanceSlice from 'store/instances/slice';
import { makeEnhancedInstanceState } from 'store/instances/useCurrentInstanceState';
import { mockTrialInstanceState } from 'store/instances/useCurrentInstanceState/mockInstanceState';
import { mockedAjaxUtils, render, screen, sleep, userEvent } from 'tests/helpers';
import { format, SHORT_DATE_DISPLAY_FORMAT } from 'utils/DateUtil';
import { numberFormat, init } from 'utils/i18nUtil';

import { ManageTrialInstanceModal } from './ManageTrialInstanceModal';
init();
jest.mock('utils/AjaxUtil');
const AjaxUtils = mockedAjaxUtils();

jest.mock('hooks/redux', () => {
  const actual = jest.requireActual('hooks/redux');
  return {
    ...actual,
    useEnhancedDispatch: () => jest.fn(),
  };
});

jest.spyOn(InstanceSlice, 'setManageTrialInstanceId');

const renderModalforInstance = (manageTrialInstanceId: string = '123') =>
  render(<ManageTrialInstanceModal />, {
    testState: { instance: { manageTrialInstanceId } },
  });

describe('ManageTrialInstanceModal', () => {
  beforeEach(() => {
    AjaxUtils.get.mockImplementation(() => Promise.resolve({ data: mockTrialInstanceState }));
  });
  afterEach(() => jest.resetAllMocks());

  it('renders nothing if no instanceId is provided', () => {
    renderModalforInstance('');
    expect(screen.queryByRole('dialog')).not.toBeInTheDocument();
  });

  it('requests instance state for provided instanceId', async () => {
    renderModalforInstance();
    expect(AjaxUtils.get).toHaveBeenCalledWith('/arcade/api/v1/organization/instanceState/123');
    expect(await screen.findByRole('dialog')).toBeVisible();
  });

  it('displays error on failed API calls', async () => {
    const testError = 'Could not retrieve instance state.';
    AjaxUtils.get.mockImplementation(() => Promise.reject());

    renderModalforInstance();
    sleep(2000);
    expect(await screen.findByText(testError)).toBeInTheDocument();
  });

  it('displays trial data from enhanced instance state', async () => {
    renderModalforInstance();
    const enhancedMock = makeEnhancedInstanceState(mockTrialInstanceState);
    expect(await screen.findByText(numberFormat(enhancedMock.numberOfRecordsLeft))).toBeVisible();
    expect(await screen.findByText(numberFormat(enhancedMock.recordLimit))).toBeVisible();
    expect(await screen.findByText(`${enhancedMock.trialDaysLeft}`)).toBeVisible();
    expect(await screen.findByText(format(enhancedMock.expiryDate, SHORT_DATE_DISPLAY_FORMAT))).toBeVisible();
  });

  // Failing due to the mock function. Will need to come back around to take a look at it.
  // eslint-disable-next-line
  it.skip('makes request to extend trial duration by 7 days', async () => {
    AjaxUtils.put.mockImplementation(() => Promise.resolve({ data: true }));

    renderModalforInstance();

    expect((await screen.findByText('Save')).closest('button')).toBeDisabled();

    await userEvent.click(await screen.findByPlaceholderText('Select date'));
    await userEvent.click(await screen.findByTitle('Next month (PageDown)'));
    await userEvent.click(await screen.findByText('15'));
    await userEvent.click(await screen.findByText('Save'));

    // Prevent test from failing over time
    const twoMonthsFromNow = moment(new Date()).add(2, 'M');
    let nextMonth = twoMonthsFromNow.month().toString();
    if (nextMonth.length < 2) {
      nextMonth = '0' + nextMonth;
    }
    let currentYear = twoMonthsFromNow.year();

    await screen.findByText('Trial instance updated');

    expect(AjaxUtils.put).toHaveBeenCalledWith(
      `/arcade/api/v1/organization/extendTrial?extendedDate=${currentYear}-${nextMonth}-15T23%3A59%3A59.000&instanceId=123`
    );
  });

  it('makes request extend trial record limit by 5,000', async () => {
    AjaxUtils.put.mockImplementation(() => Promise.resolve({ data: true }));

    renderModalforInstance();

    expect((await screen.findByText('Save')).closest('button')).toBeDisabled();

    await userEvent.click(await screen.findByRole('combobox'));
    await userEvent.click(await screen.findByText('5,000'));
    await userEvent.click(await screen.findByText('Save'));
    await screen.findByText('Trial instance updated');

    expect(AjaxUtils.put).toHaveBeenCalledWith(
      '/arcade/api/v1/organization/extendTrial?extendedRecordLimit=5000&instanceId=123'
    );
  });

  it('When "Close" is clicked, dispatched event to set selected instance id to empty string', async () => {
    renderModalforInstance();
    await userEvent.click(await screen.findByText('Close'));
    // modal does not close because mocked redux store does not update
    await screen.findAllByRole('dialog');
    expect(InstanceSlice.setManageTrialInstanceId).toHaveBeenCalledWith('');
  });

  it('When "x" is clicked, dispatched event to set selected instance id to empty string', async () => {
    renderModalforInstance();
    await userEvent.click(await screen.findByLabelText('Close')); // Ant Modal's <button aria-label="Close">
    // modal does not close because mocked redux store does not update
    await screen.findAllByRole('dialog');
    expect(InstanceSlice.setManageTrialInstanceId).toHaveBeenCalledWith('');
  });
});
