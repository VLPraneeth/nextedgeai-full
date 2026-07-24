import Icon, { IconProps } from 'antd/lib/icon';
import cx from 'classnames';

import { Stack } from 'components/layout';
import Text, { TextProps } from 'components/typography/Text';
// TODO: fix type exports for modules

const errorIconStyle = { fontSize: 36, width: '100%' };

export interface TableErrorProps {
  containerClassName?: string;
  contentClassName?: string;
  error: Error | string;
  iconType?: IconProps['type'];
  iconProps?: IconProps;
  textProps?: TextProps;
}

const TableError = ({
  containerClassName,
  contentClassName,
  error,
  iconType = 'exclamation-circle',
  iconProps,
  textProps,
}: TableErrorProps) => {
  const message = error instanceof Error ? error.message : error || 'Error';

  return (
    <div className={cx('table-error-container', containerClassName)}>
      <div className={cx('table-error-content', contentClassName)}>
        <Stack spacing="xs">
          <Icon type={iconType} style={errorIconStyle} {...iconProps} />
          <Text {...textProps}>{message}</Text>
        </Stack>
      </div>
    </div>
  );
};

export default TableError;
