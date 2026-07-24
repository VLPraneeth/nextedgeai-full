//
// Copyright (c) 2019-Present Syncari - All rights reserved.
//

import cx from 'classnames';

import ShowWhiteSpaceChars from 'components/ShowWhiteSpaceChars';
import { tNamespaced } from 'utils/i18nUtil';

import './ConditionReadOnly.less';

const tsf = tNamespaced('SimpleFilter');

interface ConditionReadOnlyProps {
  left?: string;
  operator?: string;
  right?: string | React.ReactNode;
  className?: string;
  /* try to let the backend set our label before we use local translations */
  operatorLabel?: undefined | string;
}

const ConditionReadOnly = ({ left, operator, operatorLabel, right, className }: ConditionReadOnlyProps) => {
  if (!left && !operator && !right) {
    return <span>{tsf('empty_filter')}</span>;
  }
  return (
    <span className={cx('synri-condition-readonly', className)}>
      <span className="synri-left-value">{left}</span>
      <span> {operatorLabel?.toLowerCase() || (operator && tsf(operator).toLowerCase())} </span>
      <span className="synri-right-value">
        <ShowWhiteSpaceChars>{right}</ShowWhiteSpaceChars>
      </span>
    </span>
  );
};

export default ConditionReadOnly;
