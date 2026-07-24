//
// Copyright (c) 2019-Present Syncari - All rights reserved.
//
// TODO: This is a temporary wrapper alias since its
// conflicting with antd icon and the rendered markup is the alias name. Webpack bug????

import Icon, { IconProps, IconSize } from './Icon';

function sIcon(props: IconProps) {
  return <Icon {...props} />;
}

sIcon.SIZE = IconSize;

export default sIcon;
