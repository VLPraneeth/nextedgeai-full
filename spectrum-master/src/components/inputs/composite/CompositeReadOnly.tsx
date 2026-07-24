//
// Copyright (c) 2019-Present Syncari - All rights reserved.
//
import cx from 'classnames';

import { tNamespaced } from 'utils/i18nUtil';

import CompositeGroupReadOnly from './CompositeGroupReadOnly';

import './CompositeReadOnly.less';

interface CompositeReadOnlyProps {
  className?: string;

  layout?: string;

  compositeValues: any[];

  configuration: any[];

  defaultValue: any;

  name: string;
  fetchPicklistValues: any;
  picklistValues: any;

  [key: string]: any;
}

const tn = tNamespaced('CompositeReadOnly');

const CompositeReadOnly = ({
  compositeValues: values,
  layout,
  configuration,
  defaultValue = {},
  name,
  className,
  disabled,
  fetchPicklistValues,
  picklistValues,
  ...rest
}: CompositeReadOnlyProps) => {
  const { compositeValues } = defaultValue;
  return (
    <div className={cx('synri-composite-readonly', className)} key={`synri-composite-${name}`}>
      {compositeValues ? (
        compositeValues.map((value: { repeatId: string }, index: number) => {
          return (
            <CompositeGroupReadOnly
              key={value.repeatId}
              value={value}
              order={index + 1}
              configuration={configuration}
              layout={layout}
              disabled={disabled}
              fetchPicklistValues={fetchPicklistValues}
              picklistValues={picklistValues}
            />
          );
        })
      ) : (
        <div className="synri-composite-empty-readonly">{tn('empty_composite')}</div>
      )}
    </div>
  );
};

export default CompositeReadOnly;
