//
// Copyright (c) 2019-Present Syncari All rights reserved.
//

import { getCurrentYearSales } from 'mocks/fixtures/insights/dataCardCurrentYearSales';
import { customerCount, getCustomerCount } from 'mocks/fixtures/insights/dataCardCustomerCount';

import { render, screen } from 'tests/helpers';

import { VizerMetric } from './VizerMetric';

describe('Vizer metric', () => {
  it('should render 0 metric vizer without any issues', () => {
    render(
      <VizerMetric
        configuration={customerCount.contents.configuration}
        data={customerCount.contents.data}
        height={100}
      />
    );
    expect(screen.queryByText('0')).toBeVisible();
  });

  it('should render metric vizer with user friendly thousand value', () => {
    const metricDataCard = getCustomerCount(1000);
    render(
      <VizerMetric
        configuration={metricDataCard.contents.configuration}
        data={metricDataCard.contents.data}
        height={100}
      />
    );
    expect(screen.queryByText('1,000')).toBeVisible();
  });

  test.each([
    [100, '$100'],
    [200.7, '$200'],
    [100000, '$100K'],
    [170212, '$170.21K'],
    [360200, '$360.2K'],
    [230672234, '$230.67M'],
    [630000000, '$630M'],
  ])('%s should render currency metric vizer compact notation %s', async (number, compact) => {
    const sales = getCurrentYearSales(number);
    render(<VizerMetric configuration={sales.contents.configuration} data={sales.contents.data} height={100} />);
    expect(screen.queryByText(compact)).toBeVisible();
  });
});
