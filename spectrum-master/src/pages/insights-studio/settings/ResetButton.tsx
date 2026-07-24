//
// Copyright (c) 2019-Present Syncari - All rights reserved.
//

import { Icon } from 'antd';
import React from 'react';

import Button from 'components/Button';
import './ResetButton.scss';

export interface ResetButtonProps {
  onClick: () => void;
  children: React.ReactNode;
}
export const ResetButton = ({ onClick, children }: ResetButtonProps) => {
  return (
    <Button type="link" onClick={onClick} className="reset-button">
      <Icon type="close-circle" className="reset-button__icon" />
      {children}
    </Button>
  );
};
