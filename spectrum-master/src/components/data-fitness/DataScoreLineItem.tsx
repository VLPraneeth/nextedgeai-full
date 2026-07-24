import { useNavigate } from '@reach/router';
import Button from 'antd/lib/button';
import { useMemo } from 'react';

import CollapsibleLineItem from 'components/CollapsibleLineItem';
import DataScoreBatteryMeter from 'components/data-fitness/DataScoreBatteryMeter';
import { HStack, Stack } from 'components/layout';
import { useCurrentSyncStudioRootTab } from 'pages/sync-studio/entity/SyncStudioRootTabs';
import { DataScoreFactor, EntityDataScore } from 'store/datascore';
import { encodeFactorId } from 'store/datascore';
import { tNamespaced } from 'utils/i18nUtil';
import RouteConstants from 'utils/RouteConstants';
import { makeUrl } from 'utils/UrlUtil';

import DataScoreLink, { getLinkItemsForEntity } from './DataScoreLink';

import './DataScoreLineItem.less';

const tn = tNamespaced('DataQualityStudio.DataScoreLineItem');

const DataScoreFactorItem = ({
  averageScore,
  entityId,
  fieldName,
  ruleId,
  label,
  description,
  filterCondition,
}: DataScoreFactor) => {
  const navigate = useNavigate();
  const factorId = encodeFactorId(entityId, fieldName, ruleId);

  return (
    <div className="datascore-factor-item">
      <HStack spacing="sm">
        <span className="datascore-factor-item-title">{label}</span>
        <DataScoreBatteryMeter score={averageScore} />
      </HStack>
      <Button
        htmlType="button"
        type="link"
        className="datascore-records-link"
        onClick={() => {
          navigate(makeUrl(RouteConstants.DATA_STUDIO_ENTITY, { entityId }, { factorId }));
        }}>
        {tn('show_records')}
      </Button>
    </div>
  );
};

interface DataScoreLineItemProps extends Pick<EntityDataScore, 'label' | 'factors' | 'score'> {
  initialExpand?: boolean;
}

const DataScoreLineItem = ({ label, score, factors, initialExpand = false }: DataScoreLineItemProps) => {
  const { entityId } = factors.filter(Boolean)[0] ?? {};
  const { currentTab } = useCurrentSyncStudioRootTab();

  const linkItems = useMemo(() => getLinkItemsForEntity(entityId, currentTab), [currentTab, entityId]);

  const factorCount = factors.length;
  const contentMaxHeight = useMemo(() => {
    // this is an estimated maxheight. If you change styling, you might need to adjust this.
    return factorCount ? factorCount * 50 : 50;
  }, [factorCount]);

  return (
    <CollapsibleLineItem
      initialExpand={initialExpand}
      title={label}
      leftTitleChildren={<DataScoreBatteryMeter score={score} />}
      rightTitleChildren={linkItems.map((link) => (
        <DataScoreLink key={link.label} {...link} />
      ))}
      contentMaxHeight={contentMaxHeight}>
      <div className="datascore-factors-container">
        <Stack spacing="xs">
          {factors.length > 0 ? (
            factors.map((factor, idx) => <DataScoreFactorItem key={idx} {...factor} />)
          ) : (
            <div className="datascore-empty-content">{tn('no_factors')}</div>
          )}
        </Stack>
      </div>
    </CollapsibleLineItem>
  );
};

export default DataScoreLineItem;
