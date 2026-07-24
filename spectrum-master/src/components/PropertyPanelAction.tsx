//
// Copyright (c) 2019-Present Syncari - All rights reserved.
//
import { Icon, Tooltip } from 'antd';
import cx from 'classnames';
import * as React from 'react';

import Fieldset from 'components/Fieldset';
import InlineSvg from 'components/icons/InlineSvg';

import './PropertyPanelAction.less';
import { HStack, Stack } from './layout';

export interface PropertyPanelActionModel {
  id?: string | number;
  key?: string | number;
  disabled?: boolean;
  disabledMessage?: string;
  handler: React.MouseEventHandler;
  icon?: string;
  name: string;
  svgIcon?: string;
  suffix?: React.ReactElement;
}

interface PropertyPanelActionProps {
  className?: string;
  collapsible?: boolean;
  actions: PropertyPanelActionModel[];
}

const PropertyPanelAction = ({ actions, className, collapsible = false }: PropertyPanelActionProps) => {
  return (
    <div className={cx('synri-property-panel-actions', className)}>
      <Fieldset collapsible={collapsible} title="Actions">
        <Stack spacing="sm">
          {actions.map((action) => {
            const actionKey = action.id || action.key;
            const isActive = !action.disabled;

            const actionComp = (
              <button
                className={cx('synri-action-button', {
                  'synri-active': isActive,
                  'synri-action-disabled': action.disabled,
                })}
                onClick={action.disabled ? undefined : action.handler}
                data-testid={`action-name-${action.name}`}
                key={`panel-action-${actionKey}`}
                aria-disabled={action.disabled}>
                <HStack className="full-width" justify="space-between">
                  <HStack className="full-width">
                    {action.icon && <Icon className="synri-action-icon" type={action.icon} />}
                    {action.svgIcon && <InlineSvg src={action.svgIcon} title={action.name} />}
                    <div className="synri-action-button-text" title={action.name}>
                      {action.name}
                    </div>
                  </HStack>
                  {action.suffix}
                </HStack>
              </button>
            );

            return (
              <Tooltip
                title={action.disabled && action.disabledMessage}
                mouseEnterDelay={1}
                key={`panel-action-tooltip-${actionKey}`}>
                {actionComp}
              </Tooltip>
            );
          })}
        </Stack>
      </Fieldset>
    </div>
  );
};

export default PropertyPanelAction;
