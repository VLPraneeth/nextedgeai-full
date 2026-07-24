//
// Copyright (c) 2019-Present Syncari - All rights reserved.
//
import { Tooltip } from 'antd';

import { useEnhancedDispatch as useDispatch, useEnhancedSelector as useSelector } from 'hooks/redux';
import { moveGraphTooltip } from 'store/pipeline/actions';

import './ConnectorTooltip.less';

const RestingLocation = { top: 0, left: 0 };

function ConnectorTooltip() {
  const nodeTootipMessage = useSelector((state) => state.connector.nodeTootipMessage);
  const tooltipCoordinates = useSelector((state) => state.pipeline.tooltipCoordinates);
  const dispatch = useDispatch();

  const visibilityChange = (visible: boolean) => {
    if (!visible) {
      dispatch(moveGraphTooltip(RestingLocation));
    }
  };

  return (
    <Tooltip title={nodeTootipMessage} placement="bottom" onVisibleChange={visibilityChange}>
      <div id="tooltipContainer" style={tooltipCoordinates} data-testid="tooltip-container" />
    </Tooltip>
  );
}

export default ConnectorTooltip;
