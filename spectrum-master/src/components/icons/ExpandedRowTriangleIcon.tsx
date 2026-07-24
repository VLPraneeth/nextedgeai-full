//
// Copyright (c) 2019-Present Syncari - All rights reserved.
//
import { Icon } from 'antd';
import { IconComponent, IconProps } from 'antd/lib/icon';

const ExpandedRowTriangleSvg = () => (
  <svg xmlns="http://www.w3.org/2000/svg" className="expanded-row-triangle">
    <polyline points="0,10 10,0 20,10" />
  </svg>
);

const ExpandedRowTriangleIcon = (props: Partial<IconComponent<IconProps>>) => (
  <Icon component={ExpandedRowTriangleSvg} {...props} />
);

export default ExpandedRowTriangleIcon;
