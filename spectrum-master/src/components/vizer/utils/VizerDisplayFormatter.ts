//
// Copyright (c) 2019-Present Syncari - All rights reserved.
//

import { FieldConfig, VizType } from 'store/insights-studio/types';
import { Locale, LocaleStyle, numberFormat, tc } from 'utils/i18nUtil';
import { isNotNullOrUndefined } from 'utils/TypeUtils';

export const displayFormatter = {
  currency: (value: string, options?: Record<string, string | number>) => {
    if (!isNotNullOrUndefined(value)) {
      value = String(0);
    }
    return new Intl.NumberFormat(Locale.EN_US, {
      style: LocaleStyle.CURRENCY,
      currency: 'USD', // Only support USD in MVP
      maximumFractionDigits: 2,
      minimumFractionDigits: 0,
      ...(options || {}),
      // Round down currency values.
      // Make this metadata driven next version.
    }).format(Math.floor(Number(value)));
  },
  number: (value: string) => numberFormat(value, Locale.EN_US, LocaleStyle.DECIMAL),
  percent: (value: string) => numberFormat(Number(value) / 100, Locale.EN_US, LocaleStyle.PERCENT, 2),
  text: (value: string) => value?.toString(),
  numberPercent: (value: string, percentageValue?: string) => {
    const numberValue = displayFormatter.number(value);
    return percentageValue
      ? numberValue + ' - ' + displayFormatter.percent(percentageValue)
      : numberValue + ' - ' + displayFormatter.percent(value);
  },
} as const;

export function getDisplayFormatOptions(vizType: VizType | undefined) {
  const options = [
    { value: 'currency', label: tc('currency') },
    { value: 'number', label: tc('number') },
    { value: 'percent', label: tc('percent') },
    { value: 'text', label: tc('text') },
  ];

  if (vizType === 'PIE' || vizType === 'FUNNEL') {
    options.push({ value: 'numberPercent', label: tc('number_percent') });
  }

  return options;
}

export function formatValue(displayFormat: FieldConfig['displayFormat'], value: number, percentageValue?: number) {
  if (displayFormat && value && displayFormatter[displayFormat]) {
    if (displayFormat === 'currency') {
      return displayFormatter[displayFormat](String(value), { notation: 'compact' });
    }
    if (displayFormat === 'numberPercent' && percentageValue) {
      return displayFormatter[displayFormat](String(value), String(percentageValue));
    }
    return displayFormatter[displayFormat](String(value));
  }
  return value;
}
