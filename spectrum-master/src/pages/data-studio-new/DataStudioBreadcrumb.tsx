import { useMemo } from 'react';
import { withI18n } from 'components/I18nProvider';
import { BreadcrumbLink } from 'components/breadcrumb/BreadcrumbLink';
import { useEnhancedSelector } from 'hooks/redux';
import { useEntityRecordsCount } from 'hooks/useSyncariEntities';
import { useReferenceDataList } from 'store/reference-data';
import RouteConstants from 'utils/RouteConstants';
import { t } from 'utils/i18nUtil';
import { Icon } from 'antd';
import Tooltip from 'components/tooltip/Tooltip';

interface DataStudioBreadcrumbProps {
  type: 'data-studio' | 'reference-data';
  path: string;
}

export const DataStudioBreadcrumb = withI18n(({ type }: DataStudioBreadcrumbProps) => {
  const { data: referenceDataList } = useReferenceDataList();

  // Get entities list for data studio
  const entitiesList: string[] | undefined = useEnhancedSelector((state) =>
    state.entity.entities?.map((entity) => entity.apiName)
  );

  // Get total counts for data studio
  const { totalCounts } = useEntityRecordsCount(entitiesList ?? []);

  // Calculate records based on type
  const totalRecords = useMemo(() => {
    if (type === 'reference-data') {
      if (!referenceDataList) return null;
      const totalReferenceDataRecords = referenceDataList.reduce((sum, refData) => {
        return sum + Number(refData.totalRecords || 0);
      }, 0);
      const totalReferenceDataRecordsTooltip = (
        <>
          <div className="record-count-tooltip-text-wrapper">
            <p>Active:</p>
            <p className="count">{totalReferenceDataRecords.toLocaleString()}</p>
          </div>
        </>
      );
      return (
        <div className="total-count-item">
          {'Total Records: '}
          {totalReferenceDataRecords.toLocaleString()}
          <Tooltip
            mouseEnterDelay={0}
            placement="bottom"
            title={totalReferenceDataRecordsTooltip}
            overlayClassName="record-count-tooltip">
            <Icon type="info-circle" />
          </Tooltip>
        </div>
      );
    } else {
      // data-studio
      const total = totalCounts.active + totalCounts.deleted;
      const tooltipContent = (
        <>
          <div className="record-count-tooltip-text-wrapper">
            <p>Active:</p>
            <p className="count">{totalCounts.active.toLocaleString()}</p>
          </div>
          <div className="record-count-tooltip-text-wrapper">
            <p>Deleted:</p>
            <p className="count">{totalCounts.deleted.toLocaleString()}</p>
          </div>
        </>
      );

      return (
        <div className="total-count-item">
          {'Total Records: '}
          {total.toLocaleString()}
          <Tooltip
            mouseEnterDelay={0}
            placement="bottom"
            title={tooltipContent}
            overlayClassName="record-count-tooltip">
            <Icon type="info-circle" />
          </Tooltip>
        </div>
      );
    }
  }, [type, referenceDataList, totalCounts]);

  return (
    <div className="data-studio-breadcrumb">
      <BreadcrumbLink to={RouteConstants.DATA_STUDIO_ROOT}>{t('DataStudio.window_title')}</BreadcrumbLink>
      {totalRecords}
    </div>
  );
}, 'DataStudio');
