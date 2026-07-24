//
// Copyright (c) 2019-Present Syncari - All rights reserved.
//
import { Icon } from 'antd';
import { IconComponent, IconProps } from 'antd/lib/icon';

const BaselinePublishIconSvg = () => (
  <svg width="12px" height="14px" viewBox="0 0 12 14" version="1.1">
    <g id="Canvas-Navbar-Adjustments" stroke="none" strokeWidth="1" fill="none" fillRule="evenodd">
      <g id="Draft" transform="translate(-526.000000, -86.000000)" fill="#FFFFFF" fillRule="nonzero">
        <g id="baseline-publish" transform="translate(526.000000, 86.000000)">
          <path
            d="M0.510416667,0.583333333 L0.510416667,2.1875 L11.7395833,2.1875 L11.7395833,0.583333333 L0.510416667,0.583333333 Z M0.510416667,8.60416667 L3.71875,8.60416667 L3.71875,13.4166667 L8.53125,13.4166667 L8.53125,8.60416667 L11.7395833,8.60416667 L6.125,2.98958333 L0.510416667,8.60416667 Z"
            id="Shape"
          />
        </g>
      </g>
    </g>
  </svg>
);

const BaselinePublishIcon = (props: Partial<IconComponent<IconProps>>) => (
  <Icon component={BaselinePublishIconSvg} {...props} />
);

export default BaselinePublishIcon;
