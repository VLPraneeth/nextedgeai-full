//
// Copyright (c) 2019-Present Syncari - All rights reserved.
//

import Spinner from 'components/Spinner';
import { TranslatedText } from 'components/typography';
import { ConnectorStatus as ConnectorStatusType } from 'reducers/connectorReducer';
import AppConstants from 'utils/AppConstants';

import './ConnectorStatus.less';

export interface CheckRendererProps {
  text: ConnectorStatusType;
}

const ConnectorStatus = ({ text }: CheckRendererProps) => {
  let displayText = <TranslatedText namespace="Connector" text={text} />;

  if (text === AppConstants.CONNECTOR_STATUS.ACTIVATING) {
    displayText = (
      <div className="connector-status-loading">
        <Spinner iconProps={{ style: { fontSize: 18 } }} />
        {displayText}
      </div>
    );
  }
  return displayText;
};

export default ConnectorStatus;
