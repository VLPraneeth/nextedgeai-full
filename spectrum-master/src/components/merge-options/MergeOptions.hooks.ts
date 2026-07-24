import { useMemo } from 'react';

import { useI18nContext } from 'components/I18nProvider';

import { MergeOptionValues } from './MergeOptions.types';

export const useMergeOptionValues = () => {
  const { tn } = useI18nContext();

  return useMemo(() => {
    const sourceMergeOptions = [
      {
        label: tn(MergeOptionValues.REPLACE),
        value: MergeOptionValues.REPLACE,
      },
      {
        label: tn(MergeOptionValues.AFTER_SOURCE),
        value: MergeOptionValues.AFTER_SOURCE,
      },
      {
        label: tn(MergeOptionValues.BEFORE_CORE),
        value: MergeOptionValues.BEFORE_CORE,
      },
      {
        label: tn(MergeOptionValues.COPY),
        value: MergeOptionValues.COPY,
      },
    ];

    const destinationMergeOptions = [
      {
        label: tn(MergeOptionValues.REPLACE),
        value: MergeOptionValues.REPLACE,
      },
      {
        label: tn(MergeOptionValues.AFTER_CORE),
        value: MergeOptionValues.AFTER_CORE,
      },
      {
        label: tn(MergeOptionValues.BEFORE_SINK),
        value: MergeOptionValues.BEFORE_SINK,
      },
      {
        label: tn(MergeOptionValues.COPY),
        value: MergeOptionValues.COPY,
      },
    ];

    const simpleOptions = [
      {
        label: tn(MergeOptionValues.REPLACE),
        value: MergeOptionValues.REPLACE,
      },
      {
        label: tn(MergeOptionValues.MERGE),
        value: MergeOptionValues.MERGE,
      },
    ];

    return [sourceMergeOptions, destinationMergeOptions, simpleOptions];
  }, [tn]);
};
