import cx from 'classnames';
import { useEffect } from 'react';

import AgTable from 'components/AgTable';
import { AgTableItemCellParams } from 'components/AgTable/AgTable';
import { OPEN_OUTLINE } from 'components/icons/Icons';
import InlineSvg from 'components/icons/InlineSvg';
import { Text, TranslatedText } from 'components/typography';
import { useUnifiedDataCardNavigate } from 'pages/insights-studio/utils/useUnifiedDataCardNavigate';
import { useLazyGetDataCardDependenciesQuery, useLazyGetDatasetDependenciesQuery } from 'store/insights-studio';
import { UsedByItem } from 'store/insights-studio/types';
import { tNamespaced } from 'utils/i18nUtil';
import RouteConstants from 'utils/RouteConstants';
import { makeUrl } from 'utils/UrlUtil';

import './UsedByList.scss';

const tn = tNamespaced('InsightsStudio.Review');

const defaultColDef = { flex: 1 };

const frameworkComponents = {
  newPageAnchor: ({ data, colDef }: AgTableItemCellParams<UsedByItem, string>) => {
    let url = makeUrl(RouteConstants.INSIGHTS_STUDIO_DASHBOARD, {
      dashboardId: colDef?.cellRendererParams?.dashboardId,
    });

    const type = data.type?.toUpperCase();
    if (type === 'DATACARD') {
      url += `/draft/datacard/${data.id}`;
    } else if (type === 'DATASET') {
      url += `/draft/dataset/${data.id}`;
    } else if (type === 'DASHBOARD') {
      const baseUrl = data.nestedDraft
        ? RouteConstants.INSIGHTS_STUDIO_DASHBOARD_DRAFT
        : RouteConstants.INSIGHTS_STUDIO_DASHBOARD;
      url = makeUrl(baseUrl, { dashboardId: data.id });
    }

    return (
      <div className="page-anchor-container">
        <a href={url} target="_blank" rel="noreferrer">
          {data.name}
          <InlineSvg className="page-anchor-icon" size="1x" src={OPEN_OUTLINE} title="open" />
        </a>
        {data.draftStatus && type === 'DASHBOARD' && <Text size="sm">({tn(data.draftStatus)})</Text>}
      </div>
    );
  },

  translatedText: ({ data, value }: AgTableItemCellParams<UsedByItem, string>) => {
    return <TranslatedText namespace="InsightsStudio.Review" text={value} />;
  },
};

export interface UsedByListProps {
  usedById: string;
  type: 'DATASET' | 'DATACARD';
}

const UsedByList = ({ usedById, type }: UsedByListProps) => {
  const [fetchDatasetDependencies, { data, isLoading }] = useLazyGetDatasetDependenciesQuery();
  const [
    fetchDataCardDependencies,
    { data: dataCardDependencies, isLoading: dataCardIsLoading },
  ] = useLazyGetDataCardDependenciesQuery();
  const { getCurrentDashboard } = useUnifiedDataCardNavigate();

  const { dashboardId } = getCurrentDashboard();
  useEffect(() => {
    if (usedById) {
      if (type === 'DATASET') {
        fetchDatasetDependencies({ datasetId: usedById });
      } else {
        fetchDataCardDependencies({ dataCardId: usedById });
      }
    }
    // Only fetch on mount. No need for a refresh
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, []);

  const COLUMNS = [
    {
      headerName: tn('link'),
      cellRenderer: 'newPageAnchor',
      resizable: true,
      cellRendererParams: {
        dashboardId,
      },
    },
    {
      headerName: tn('type'),
      field: 'type',
      cellRenderer: 'translatedText',
      resizable: true,
    },
    {
      headerName: tn('author'),
      field: 'author',
      resizable: true,
    },
  ];

  return (
    <div
      style={{ marginTop: 24, width: '50%' }}
      className={cx('used-by-list', {
        'used-by-list--dataset': type === 'DATASET',
        'used-by-list--data-card': type === 'DATACARD',
      })}>
      <Text color="gray-900" lineHeight="loose">
        {type === 'DATASET' ? tn('headline') : tn('headline_data_card')}
      </Text>
      <AgTable
        className="used-by-list__table"
        columnDefs={COLUMNS}
        defaultColDef={defaultColDef}
        frameworkComponents={frameworkComponents}
        domLayout="autoHeight"
        loading={type === 'DATASET' ? dataCardIsLoading : isLoading}
        rowData={type === 'DATASET' ? data : dataCardDependencies}
        suppressCellSelection
        enableCellTextSelection
      />
    </div>
  );
};

export default UsedByList;
