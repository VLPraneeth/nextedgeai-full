import moment from 'moment';

import { render, screen, userEvent } from 'tests/helpers';
import { SHORT_DATE_TIME_TZ_DISPLAY_FORMAT } from 'utils/DateUtil';

import ScheduleInput from './ScheduleInput';

/**
 * Tests for dates and times are flaky by nature. Times chosen for tests should
 * provide the best resiliency, but there is always potential to fail when run at
 * certain times. If you see failures in this test suite, wait a few minutes and
 * re-run to verify if it is just a time-based failure.
 *
 * Failures may happen when tests are run:
 * - when the current minute changes
 * - at the top of the hour
 * - between 11:59 PM and 12:01 AM UTC
 */

const timezone = 'US/Arizona';

const renderComponent = () =>
  render(<ScheduleInput onChange={jest.fn()} />, { testState: { user: { timeZone: timezone } } });

describe('ScheduleInput', () => {
  it('displays next sync correctly for Minutes', async () => {
    renderComponent();

    // Activate input
    await userEvent.click(screen.getByRole('switch'));

    const date = moment().tz(timezone);
    date.seconds(0);
    date.add(1, 'minute');

    expect(screen.getByText('Next Sync (Local): ' + date.format(SHORT_DATE_TIME_TZ_DISPLAY_FORMAT))).toBeVisible();
    expect(screen.getByText('Next Sync (UTC): ' + date.utc().format(SHORT_DATE_TIME_TZ_DISPLAY_FORMAT))).toBeVisible();
  });

  it('displays next sync correctly for Hours', async () => {
    renderComponent();

    // Activate input
    await userEvent.click(screen.getByRole('switch'));

    // Change mode
    await userEvent.click(screen.getByText('Minute'));
    await userEvent.click(screen.getByText('Hour'));

    await userEvent.click(screen.getByTestId('hour-minutes-select'));
    await userEvent.click(screen.getByRole('option', { name: '59' }));

    const now = moment().tz(timezone);
    const date = now.clone().seconds(0).minutes(59);
    if (!date.isAfter(now)) {
      date.add(1, 'hour');
    }

    expect(screen.getByText('Next Sync (Local): ' + date.format(SHORT_DATE_TIME_TZ_DISPLAY_FORMAT))).toBeVisible();
    expect(screen.getByText('Next Sync (UTC): ' + date.utc().format(SHORT_DATE_TIME_TZ_DISPLAY_FORMAT))).toBeVisible();
  });

  it('displays next sync correctly for Days', async () => {
    renderComponent();

    // Activate input
    await userEvent.click(screen.getByRole('switch'));

    // Change mode
    await userEvent.click(screen.getByText('Minute'));
    await userEvent.click(screen.getByText('Day'));

    // Set time to 11:59 PM
    await userEvent.click(screen.getByPlaceholderText('Select time'));
    await userEvent.click(screen.getAllByRole('button', { name: '11' })[0]); // click first, second is for minutes
    await userEvent.click(screen.getByRole('button', { name: '59' }));
    await userEvent.click(screen.getByRole('button', { name: 'PM' }));

    // Set comparison date to 11:59 PM
    const date = moment.utc();
    date.seconds(0);
    date.minutes(59);
    date.hours(23);

    expect(screen.getByText('Next Sync (UTC): ' + date.format(SHORT_DATE_TIME_TZ_DISPLAY_FORMAT))).toBeVisible();
    expect(
      screen.getByText('Next Sync (Local): ' + date.tz(timezone).format(SHORT_DATE_TIME_TZ_DISPLAY_FORMAT))
    ).toBeVisible();
  });

  it('displays next sync correctly for Month', async () => {
    renderComponent();

    // Activate input
    await userEvent.click(screen.getByRole('switch'));

    // Change mode
    await userEvent.click(screen.getByText('Minute'));
    await userEvent.click(screen.getByText('Month'));

    // Set date to the 1st of the month
    await userEvent.click(screen.getByTestId('day-select'));
    await userEvent.click(screen.getByRole('option', { name: '1' }));

    // Set time to 12:01 AM
    await userEvent.click(screen.getByPlaceholderText('Select time'));
    await userEvent.click(screen.getAllByRole('button', { name: '12' })[0]); // click first, second is for minutes
    await userEvent.click(screen.getAllByRole('button', { name: '01' })[1]); // click second, first is for hours
    await userEvent.click(screen.getByRole('button', { name: 'AM' }));

    // Set comparison date to 12:01 AM on the first of _next_ month
    // as 12:01 AM on the 1st of the current month is (very likely) in the past
    const date = moment.utc();
    date.seconds(0);
    date.minutes(1);
    date.hours(0);
    date.date(1);
    date.add(1, 'month');

    expect(screen.getByText('Next Sync (UTC): ' + date.format(SHORT_DATE_TIME_TZ_DISPLAY_FORMAT))).toBeVisible();
    expect(
      screen.getByText('Next Sync (Local): ' + date.tz(timezone).format(SHORT_DATE_TIME_TZ_DISPLAY_FORMAT))
    ).toBeVisible();
  });
});
