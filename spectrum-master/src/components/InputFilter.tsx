//
// Copyright (c) 2019-Present Syncari - All rights reserved.
//
import Icon from 'antd/lib/icon';
import Input, { InputProps } from 'antd/lib/input';
import cx from 'classnames';
import * as React from 'react';
import { useEffect, useState } from 'react';
import { useTransition } from 'react-spring';

import AppConstants from 'utils/AppConstants';

import { useI18nContext, withI18n } from './I18nProvider';
import { Stack } from './layout';
import { SelectionInfoWithAction } from './SelectionInfoWithAction';

import './InputFilter.less';

export interface InputFilterProps extends InputProps {
  filterChildren?: React.ReactChildren | React.ReactChild;
  containerClassName?: string;
  filterCount?: number;
  clearFilters?: () => void;
}

const InputFilter = ({
  className,
  filterChildren,
  containerClassName,
  filterCount,
  clearFilters,
  ...rest
}: InputFilterProps) => {
  const [expanded, setExpanded] = useState(false);
  const { tn } = useI18nContext();

  const transitions = useTransition(filterCount && !expanded, {
    from: { maxHeight: 0, opacity: 0 },
    enter: { maxHeight: 50, opacity: 1 },
    leave: { maxHeight: 0, opacity: 0 },
  });

  const triggerDelayedResize = () => {
    setTimeout(() => {
      window.dispatchEvent(new Event('resize'));
      // wait 300ms for filter animation to finish
    }, 300);
  };

  const handleClearFilters = () => {
    clearFilters?.();
    // Trigger resize to adjust column heights after SelectionInfoWithAction disappears
    triggerDelayedResize();
  };

  useEffect(() => {
    // resize whenever the filter container is opened/closed
    triggerDelayedResize();
  }, [expanded]);

  return (
    <Stack spacing="z" className="synri-iput-filter-container-stack">
      <div
        className={cx('synri-iput-filter-container', containerClassName, {
          'synri-input-filters-expanded': expanded,
        })}>
        <Input
          className={cx('synri-input-filter', className)}
          autoComplete={AppConstants.INPUT_AUTOCOMPLETE_OPTIONS.OFF}
          {...rest}
          prefix={<Icon type="search" style={{ height: '14px', width: '14px' }} />}
          addonAfter={
            filterChildren && (
              <div
                data-testid="filter-collapse-button"
                className="synri-filter-collapse-toggle"
                onClick={() => setExpanded(!expanded)}>
                <Icon type="filter" theme="filled" />
              </div>
            )
          }
        />
        <div className={cx('synri-input-filter-filters-container')}>{filterChildren as any}</div>
      </div>
      {transitions((props, item) =>
        item ? (
          <SelectionInfoWithAction
            selectionText={tn('filters_applied', { count: filterCount })}
            action={handleClearFilters}
            actionText={tn('clear_all')}
            style={props}
          />
        ) : null
      )}
    </Stack>
  );
};

export default withI18n(InputFilter, 'InputFilter');
