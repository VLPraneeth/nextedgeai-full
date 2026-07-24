import Icon, { IconProps } from 'antd/lib/icon';
import Spin, { SpinProps } from 'antd/lib/spin';

interface SpinnerProps extends SpinProps {
  iconProps?: IconProps;
}

const Spinner = ({ iconProps, ...spinProps }: SpinnerProps) => {
  return <Spin indicator={<Icon type="loading" spin {...iconProps} />} {...spinProps} />;
};

export default Spinner;
