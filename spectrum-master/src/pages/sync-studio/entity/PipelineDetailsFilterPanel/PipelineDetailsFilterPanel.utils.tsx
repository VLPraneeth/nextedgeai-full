import { Select, Tooltip } from 'antd';
import { upperFirst } from 'lodash';
import InlineSVG from 'react-inlinesvg';

import { Connector } from 'reducers/connectorReducer';
import { PipelineDetails } from 'store/pipeline/types';

export const filterStateToSearchParam = (state: Record<string, boolean>) => {
  return Object.keys(state)
    .map((key) => (state[key] ? key : ''))
    .filter((string) => string !== '')
    .join(',');
};

export const makeSourceAndDestinationOptions = (
  data: PipelineDetails[] | undefined,
  connectors: Connector[] | undefined,
  type: 'sources' | 'sinks'
) => {
  const options: JSX.Element[] = [];
  const ids: string[] = [];

  if (data) {
    data.forEach((details) => {
      details[type]?.forEach((sinkOrSource) => {
        const value = `${upperFirst(sinkOrSource.connectorName)} / ${sinkOrSource.entityName}`;

        if (!ids.find((existingIds) => existingIds === sinkOrSource.entityId)) {
          const connector = connectors?.find(
            (connector) => connector.name.toLowerCase() === sinkOrSource.connectorName.toLowerCase()
          );

          ids.push(sinkOrSource.entityId);
          options.push(
            <Select.Option className="pipeline-details-filter-panel-option" value={sinkOrSource.entityId} key={value}>
              <div className="pipeline-details-filter-panel-option__container">
                <InlineSVG className="pipeline-details-filter-panel-option__icon" src={connector?.iconUri ?? ''} />
                <Tooltip title={value} mouseEnterDelay={0.5}>
                  <span className="pipeline-details-filter-panel-option__value">{value}</span>
                </Tooltip>
              </div>
            </Select.Option>
          );
        }
      });
    });
  }

  return options;
};
