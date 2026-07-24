import { Empty } from 'antd';

// default No Rows overlay
const NoRowsOverlay = ({ description }: { description?: string }) => (
  <Empty image={Empty.PRESENTED_IMAGE_SIMPLE} description={description} />
);

export default NoRowsOverlay;
