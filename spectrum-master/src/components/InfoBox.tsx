import Alert, { AlertProps } from 'antd/lib/alert';

export type InfoBoxProps = AlertProps;

const InfoBox = (props: InfoBoxProps) => {
  return <Alert {...props} />;
};

export default InfoBox;
