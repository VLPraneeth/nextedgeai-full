import { navigate, RouteComponentProps, Router, useLocation } from '@reach/router';
import { ColDef, ColGroupDef } from 'ag-grid-community';
import { Button, Dropdown, Icon, Menu } from 'antd';
import cx from 'classnames';
import { sortBy } from 'lodash';
import { useCallback, useMemo, useState } from 'react';

import AgTable, { ResizeColumnsCondition } from 'components/AgTable';
import { CustomSynapse } from 'components/custom-synapse/types';
import SearchBox from 'components/SearchBox';
import { useUtcTimeInUsersTimezone } from 'hooks/moment';
import { useGetAllCustomSynapseListQuery } from 'store/custom-synapse/sdk/api';
import { CustomSynapseDraftStatuses } from 'store/custom-synapse/types';
import { nextEdgeHelpUrl } from 'utils/Branding';
import { getRtkQueryErrorMessage } from 'utils/getRtkQueryErrorMessage';
import { tNamespaced, tc } from 'utils/i18nUtil';
import RouteConstants from 'utils/RouteConstants';
import { makeUrl } from 'utils/UrlUtil';

import { CustomSynapseSharePanel } from './custom-synapse-share-panel';
import { CustomSynapseActions } from './CustomSynapseActions';
import { CustomSynapseStatusTags } from './CustomSynapseStatusTags';
import { EntitiesList } from './http/entities/EntitiesList';
import HTTPCustomSynapseWizard from './http/HTTPCustomSynapseWizard';
import { CustomSdkSynapseSharePanel } from './sdk/custom-sdk-synapse-share-panel';
import { CustomSynapseApprovalModal } from './sdk/CustomSynapseApprovalModal';
import { DownloadSampleModal } from './sdk/DownloadSampleModal';
import SDKCustomSynapseWizard from './sdk/SDKCustomSynapseWizard';
import WebhookCustomSynapseWizard from './webhook/WebhookCustomSynapseWizard';
import './CustomSynapses.scss';

const tn = tNamespaced('CustomSynapse');

const gettingStartedHelp = nextEdgeHelpUrl('custom-synapse-overview');

export function CustomSynapses({ uri }: RouteComponentProps) {
  const { data: customSynapses, isFetching, error } = useGetAllCustomSynapseListQuery();
  const utcToLocal = useUtcTimeInUsersTimezone();
  const [filterString, setFilterString] = useState('');
  const location = useLocation();
  const [downloadSampleVisible, setDownloadSampleVisible] = useState(false);

  const showCustomSynapse = useCallback((synapse: CustomSynapse | null = null) => {
    const url = makeUrl(RouteConstants.SYNAPSES_CUSTOM_ITEM, {
      synapseId: synapse?.id,
      synapseType: synapse?.customSynapseType?.toLowerCase(),
    });
    navigate(url);
  }, []);

  const handleWizardClose = useCallback(() => {
    const searchParams = new URLSearchParams(location.search);
    const referrer = searchParams.get('referrer');
    if (referrer === 'entities') {
      navigate(`${location.pathname}/entities/draft`);
    } else {
      navigate(RouteConstants.SYNAPSES_CUSTOM);
    }
  }, [location]);

  const columns: (ColDef | ColGroupDef)[] = useMemo(() => {
    return [
      {
        headerName: tc('name'),
        field: 'displayName',
        resizable: true,
      },
      {
        headerName: tc('type'),
        field: 'customSynapseType',
        resizable: true,
      },
      {
        headerName: tc('status'),
        field: 'draftStatus',
        resizable: true,
        cellRendererFramework: ({ data }: { data: CustomSynapse | undefined }) => (
          <CustomSynapseStatusTags customSynapse={data} />
        ),
      },
      {
        headerName: tn('authentication_type'),
        field: 'authenticationType',
        resizable: true,
      },
      {
        headerName: tn('shared_globally'),
        field: 'isGlobal',
        resizable: true,

        cellRendererFramework: ({ data }: { data: CustomSynapse | undefined }) => {
          return data?.isGlobal ? tc('yes') : tc('no');
        },
      },
      {
        headerName: tc('last_modified_date'),
        field: 'updatedAt',
        resizable: true,
        cellRendererFramework: ({ data }: { data: CustomSynapse | undefined }) => {
          return <span className="ag-cell-value">{data?.updatedAt ? utcToLocal(data.updatedAt) : '-'}</span>;
        },
      },
      {
        headerName: tc('last_modified_by'),
        field: 'updatedBy',
        resizable: true,
      },
      {
        headerName: tc('actions'),
        field: 'updatedAt',
        cellRendererFramework: ({ data }: { data: CustomSynapse | undefined }) => (
          <CustomSynapseActions customSynapse={data} showCustomSynapse={showCustomSynapse} />
        ),
        headerClass: 'actions',
        pinned: 'right',
        width: 100,
        resizable: false,
      },
    ];
  }, [showCustomSynapse, utcToLocal]);

  const synapseStudioList = useMemo(() => {
    if (!customSynapses) {
      return [];
    }

    const filteredCustomSynapses = customSynapses.filter((synapse) => {
      return synapse.displayName.toLowerCase().includes(filterString.toLocaleLowerCase());
    });

    // Filter out published synapses that have a draft
    const combinedCustomSynapses = filteredCustomSynapses.filter((customSynapse) => {
      const hasLinkedDraft = customSynapses.some((synapseIteration) => synapseIteration.parentId === customSynapse.id);
      if (customSynapse.draftStatus === CustomSynapseDraftStatuses.APPROVED && hasLinkedDraft) {
        return false;
      }
      return true;
    });

    const sortedSynapses = sortBy(combinedCustomSynapses, (synapse) => synapse.displayName.toLowerCase());
    return sortedSynapses;
  }, [customSynapses, filterString]);

  const menu = (
    <Menu
      onClick={(e) => {
        const url = makeUrl(RouteConstants.SYNAPSES_CUSTOM_ITEM, {
          synapseId: 'new',
          synapseType: e.key.toLowerCase(),
        });
        navigate(url);
      }}>
      <Menu.Item key="SDK">{tn('sdk_synapse')}</Menu.Item>
      <Menu.Item key="HTTP">{tn('http_synapse')}</Menu.Item>
      <Menu.Item key="webhook">{tn('webhook_synapse')}</Menu.Item>
    </Menu>
  );

  const isBaseUrl = location?.pathname === makeUrl(RouteConstants.SYNAPSES_CUSTOM);

  const getStartedMenu = (
    <Menu>
      <Menu.Item key="help" onClick={() => window.open(gettingStartedHelp)}>
        {tc('help')}
      </Menu.Item>
      <Menu.Item key="download_sample" onClick={() => setDownloadSampleVisible(true)}>
        {tn('download_sample')}
      </Menu.Item>
    </Menu>
  );

  return (
    <div className="custom-synapse__container">
      {isBaseUrl && (
        <>
          <div className="custom-synapse__top-actions">
            <SearchBox
              onChange={(event) => setFilterString(event.target.value)}
              value={filterString}
              placeholder={tc('search')}
              className="custom-synapse__search"
            />

            <div className="custom-synapse__top-buttons">
              <Dropdown overlay={menu} trigger={['click']}>
                <Button type="primary">
                  {tc('create_new')} <Icon type="down" />
                </Button>
              </Dropdown>

              <Dropdown overlay={getStartedMenu} trigger={['click']}>
                <Button>
                  {tc('get_started')} <Icon type="down" />
                </Button>
              </Dropdown>
            </div>
          </div>

          <div className="custom-synapse__table-container">
            <AgTable
              className={cx('custom-synapse__table', !synapseStudioList?.length && 'empty')}
              columnDefs={columns}
              loading={isFetching}
              rowData={synapseStudioList}
              error={getRtkQueryErrorMessage(error)}
              noRowsOverlayComponentParams={{
                description: tc('no_records_found'),
              }}
              getRowNodeId={(data) => data.id}
              sizeColumnsToFit={ResizeColumnsCondition.WHEN_NARROWER}
            />
          </div>
        </>
      )}
      <Router className="entities-list">
        <SDKCustomSynapseWizard path="/sdk/:id" close={handleWizardClose} />

        <HTTPCustomSynapseWizard path="/http/:id" close={handleWizardClose} />

        <WebhookCustomSynapseWizard path="/webhook/:id" close={handleWizardClose} />

        <EntitiesList path="/http/:id/entities/*" />
      </Router>
      <CustomSynapseApprovalModal />
      <CustomSdkSynapseSharePanel />
      <CustomSynapseSharePanel />
      <DownloadSampleModal visible={downloadSampleVisible} onClose={() => setDownloadSampleVisible(false)} />
    </div>
  );
}
