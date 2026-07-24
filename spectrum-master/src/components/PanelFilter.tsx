//
// Copyright (c) 2019-Present Syncari - All rights reserved.
//
import { Input } from 'antd';
import { SearchProps } from 'antd/lib/input';

import AppConstants from 'utils/AppConstants';
import { tNamespaced } from 'utils/i18nUtil';

import './PanelFilter.less';

const tc = tNamespaced('Common');

const { Search } = Input;

const PanelFilter = (props: SearchProps) => (
  <Search
    className="synri-panel-filter"
    placeholder={tc('filter')}
    autoComplete={AppConstants.INPUT_AUTOCOMPLETE_OPTIONS.OFF}
    {...props}
  />
);

export default PanelFilter;
