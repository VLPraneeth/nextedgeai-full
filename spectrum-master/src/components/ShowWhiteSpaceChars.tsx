import cx from 'classnames';
import { range, trim, trimEnd, trimStart } from 'lodash';
import React, { ReactNode } from 'react';

import { tNamespaced } from 'utils/i18nUtil';
import { SHOW_WHITESPACE_REGEX } from 'utils/RegexUtil';

import Tooltip from './tooltip/Tooltip';

import './ShowWhiteSpaceChars.scss';

const tn = tNamespaced('ShowWhiteSpaceChars');

const WhiteSpaceChar = () => {
  return (
    <Tooltip title={tn('empty_space')} mouseEnterDelay={0.5}>
      <div className="white-space-wrapper--white-space-character">•</div>
    </Tooltip>
  );
};

export interface ShowWhiteSpaceCharsProps {
  children: ReactNode;
  className?: string;
}

const ShowWhiteSpaceChars = ({ children, className }: ShowWhiteSpaceCharsProps) => {
  if (typeof children !== 'string') {
    if (React.isValidElement(children)) {
      return children;
    }
    return null;
  }

  // Find white space characters before or after string
  const startSpacesCount = children.length - trimStart(children).length;
  const endSpacesCount = children.length - trimEnd(children).length;
  const startChars = range(startSpacesCount).map((i) => <WhiteSpaceChar key={i} />);
  const endChars = range(endSpacesCount).map((i) => <WhiteSpaceChar key={i} />);

  // Find extra spaces in the middle of the string (duplicate white space
  // characters and spaces around commas)
  const innerParts = trim(children)
    .split(SHOW_WHITESPACE_REGEX)
    .filter((part) => part !== undefined);
  const childrenWithWhitespace = innerParts.map((str, index) => {
    if (index < innerParts.length - 1) {
      return (
        <React.Fragment key={index}>
          {str}
          <WhiteSpaceChar />
        </React.Fragment>
      );
    }
    return str;
  });

  return (
    <div className={cx('white-space-wrapper', className)}>
      {startChars}
      {childrenWithWhitespace}
      {endChars}
    </div>
  );
};

export default ShowWhiteSpaceChars;
