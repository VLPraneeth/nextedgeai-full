//
// Copyright (c) 2019-Present Syncari - All rights reserved.
//

import { useMemo } from 'react';

import { isNotNullOrUndefined } from 'utils/TypeUtils';

import { VizerDisplayFormat, VizerProps } from '../types';
import { getRange } from './useGaugeVizer';
import { VizerGraphColors } from './VizerGraphColors';

const useMetricVizer = ({ configuration, data }: VizerProps) => {
  const { columns } = configuration;
  let value = '';
  let displayFormat: VizerDisplayFormat = 'text';

  // Metric viz is only expecting one row in the data and a column name
  const firstColumn = columns?.[0];
  const firstRow = data?.rows?.[0];
  if (firstColumn?.name && isNotNullOrUndefined(firstRow?.[firstColumn?.name])) {
    value = String(firstRow[firstColumn.name] ?? '');
    displayFormat = firstColumn?.displayFormat ?? displayFormat;
  }

  const range = useMemo(() => {
    return configuration.ranges?.length ? getRange(configuration.ranges, value) : undefined;
  }, [configuration?.ranges, value]);
  return {
    value,
    displayFormat,
    range,
    defaultColor: VizerGraphColors.metricDefaultColor(firstColumn),
  };
};

export { useMetricVizer };
