//
// Copyright (c) 2019-Present Syncari - All rights reserved.
//

import { Icon, Tooltip } from 'antd';

import { useI18nContext, withI18n } from 'components/I18nProvider';
import CenterLayout from 'components/layout/CenterLayout';
import { isNotNullOrUndefined } from 'utils/TypeUtils';

import { VizerProps } from '../types';
import { useMetricVizer } from '../utils/useMericVizer';
import { displayFormatter } from '../utils/VizerDisplayFormatter';
import { VizerGraphColors } from '../utils/VizerGraphColors';
import './VizerMetric.scss';

const VizerMetric = withI18n(({ configuration, data, height }: VizerProps) => {
  const { tn } = useI18nContext();
  const { value, displayFormat: formatter, range, defaultColor } = useMetricVizer({ configuration, data, height });

  if (isNotNullOrUndefined(value) && displayFormatter[formatter]) {
    const tooltipDisplayValue = displayFormatter[formatter](value);
    // Replace this with metadata when we add authoring
    const displayValue =
      formatter === 'currency' ? displayFormatter[formatter](value, { notation: 'compact' }) : tooltipDisplayValue;

    const color = range?.color ? VizerGraphColors.getDefaultColor(range.color).color : defaultColor;

    return (
      <CenterLayout className="vizer-metric">
        <div>
          <Tooltip mouseEnterDelay={1} title={tooltipDisplayValue !== displayValue ? tooltipDisplayValue : ''}>
            <div
              className="vizer-metric__display-value"
              style={{
                color,
              }}>
              {displayValue}
            </div>
          </Tooltip>

          {range?.name && (
            <div
              className="vizer-metric__range-value"
              style={{
                color,
              }}>
              {range.name}
              {!range.isSystemGenerated && (
                <Tooltip title={tn('Tooltips.metric_range', { min: range.minimumValue, max: range.maximumValue })}>
                  <span className="synri-tooltip">
                    <Icon type={'question-circle'} theme="filled" />
                  </span>
                </Tooltip>
              )}
            </div>
          )}
        </div>
      </CenterLayout>
    );
  } else {
    return <span>{tn('Vizer.no_data_title')}</span>;
  }
}, 'InsightsStudio');

export { VizerMetric };
