import { Link } from '@reach/router';
import Icon from 'antd/lib/icon';
import Tooltip from 'antd/lib/tooltip';
import * as React from 'react';

import { ReactComponent as DataStudioIcon } from 'assets/icons/data-studio.svg';
import { ReactComponent as SyncStudioIcon } from 'assets/icons/sync-studio.svg';
import { tNamespaced } from 'utils/i18nUtil';
import RouteConstants from 'utils/RouteConstants';
import { makeUrl } from 'utils/UrlUtil';

const tn = tNamespaced('DataQualityStudio.DataScoreLineItem');

export const getLinkItemsForEntity = (entityId: string, tabId: string) => [
  {
    icon: SyncStudioIcon,
    label: tn('view_in_sync_studio'),
    to: makeUrl(RouteConstants.ENTITY, { entityId, tabId }),
  },
  {
    icon: DataStudioIcon,
    label: tn('view_in_data_studio'),
    to: makeUrl(RouteConstants.DATA_STUDIO_ENTITY, { entityId }),
  },
  // TODO: get link to dqs dashboard
  //{
  //icon: DataQualityStudioIcon,
  //label: tn('view_dashboard'),
  //to: '/data-quality-studio',
  //},
];

export interface DataScoreLinkProps {
  label: string;
  to: string;
  icon: React.FC;
}

const DataScoreLink = ({ label, to, icon }: DataScoreLinkProps) => {
  return (
    <Tooltip title={label}>
      <Link to={to} className="datascore-link">
        <Icon component={icon} className="datascore-link-icon" />
      </Link>
    </Tooltip>
  );
};

export default DataScoreLink;
