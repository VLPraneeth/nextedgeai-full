import { Tooltip } from 'antd';

import { tNamespaced } from 'utils/i18nUtil';

import { useDataQuality } from '../DataQuality.hooks';

const tn = tNamespaced('DataQuality');
export const DataQualityAction = ({ children }: { children: React.ReactNode }) => {
  const { editable } = useDataQuality();

  return (
    <Tooltip title={!editable && tn('approved_not_allowed')}>
      <div>{children}</div>
    </Tooltip>
  );
};
