import { Tooltip } from 'antd';
import cx from 'classnames';
import * as React from 'react';

import { HStack } from 'components/layout';
import { Text } from 'components/typography';
import { TextProps } from 'components/typography/Text';

export type TextTagColorOptions = 'orange' | 'green' | 'blue' | 'gray' | 'purple' | 'red' | 'dark-gray';

export interface TextTagProps {
  text: string | [string, string];
  color: TextTagColorOptions;
  size?: TextProps['size'];
  tooltipText?: React.ReactNode;
  className?: string;
}

const TextTag = ({ color, text, size = 'xs', tooltipText, className }: TextTagProps) => {
  return (
    <div className={cx(`synri-text-tag-container synri-text-tag-${color}`, className)}>
      <Tooltip title={tooltipText} placement="bottom">
        {Array.isArray(text) ? (
          <HStack spacing="xs">
            <Text size={size} lineHeight="snug">
              {text[0]}
            </Text>
            <Text size={size} className={`synri-text-tag-${color}-faded`} lineHeight="snug">
              |
            </Text>
            <Text size={size} lineHeight="snug">
              {text[1]}
            </Text>
          </HStack>
        ) : (
          <Text size={size} lineHeight="snug" className="synri-text-tag-label">
            {text}
          </Text>
        )}
      </Tooltip>
    </div>
  );
};

export default TextTag;
