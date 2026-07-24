import { Link, navigate, RouteComponentProps, useMatch } from '@reach/router';
import { ColDef, ColGroupDef } from 'ag-grid-community';
import { Button } from 'antd';
import cx from 'classnames';
import { useCallback, useMemo, useState } from 'react';

import AgTable, { ResizeColumnsCondition } from 'components/AgTable';
import Can from 'components/Can';
import SearchBox from 'components/SearchBox';
import { useUtcTimeInUsersTimezone } from 'hooks/moment';
import { useGetHttpCustomSynapseEntityListQuery } from 'store/custom-synapse/http/api';
import { useGetCustomSynapseItemQuery } from 'store/custom-synapse/sdk/api';
import { HTTPCustomSynapseEntityMeta } from 'store/custom-synapse/types';
import { tc, tNamespaced } from 'utils/i18nUtil';
import { AllPermissions } from 'utils/PermissionsConstants';
import RouteConstants from 'utils/RouteConstants';
import { makeUrl } from 'utils/UrlUtil';

import { entitiesBasePath } from '../../CustomSynapseBreadcrumb';
import { EntitiesActions } from './EntitiesActions';
import { EntitiesToolbar } from './EntitiesToolbar';
import { EntityWizard } from './EntityWizard';
import './EntitiesList.scss';

const tn = tNamespaced('CustomSynapse.HttpCustomSynapse.Entities');

export function EntitiesList(props: RouteComponentProps) {
  const entitiesMatch = useMatch(entitiesBasePath);
  const { data: customSynapse, isLoading: customSynapseLoading } = useGetCustomSynapseItemQuery(
    { connectorMetaDefinitionId: entitiesMatch?.synapseId },
    {
      skip: !entitiesMatch?.synapseId,
    }
  );

  const isDraftRouteVersion = entitiesMatch?.version === 'draft';

  const metaDataId = isDraftRouteVersion ? customSynapse?.id : customSynapse?.parentId || customSynapse?.id;

  const { data: entities, isFetching: entitiesLoading } = useGetHttpCustomSynapseEntityListQuery(metaDataId!, {
    skip: !metaDataId,
    refetchOnMountOrArgChange: true,
  });

  const [filterString, setFilterString] = useState('');
  const utcToLocal = useUtcTimeInUsersTimezone();

  const columns: (ColDef | ColGroupDef)[] = useMemo(
    () => [
      {
        headerName: tc('name'),
        field: 'displayName',
        resizable: true,
      },
      {
        headerName: tc('endpoint'),
        field: 'endpoint',
        resizable: true,
      },
      {
        headerName: tc('method'),
        field: 'method',
        resizable: true,
      },
      {
        headerName: tc('last_modified_date'),
        field: 'updatedAt',
        resizable: true,
        cellRendererFramework: ({ data }: { data: HTTPCustomSynapseEntityMeta | undefined }) => {
          return <span className="ag-cell-value">{data?.updatedAt ? utcToLocal(data.updatedAt) : '-'}</span>;
        },
      },
      {
        headerName: tc('last_modified_by'),
        field: 'updatedBy',
        resizable: true,
      },
      {
        headerName: 'Used in Pipelines',
        field: 'usedInPipeline',
        resizable: true,
        cellRendererFramework: ({ data }: { data: HTTPCustomSynapseEntityMeta | undefined }) => {
          if (!data?.usedInPipeline.length) {
            return '-';
          }

          return (
            <span className="ag-cell-value">
              {data?.usedInPipeline.map((pipeline, index) => {
                return (
                  <span key={pipeline.id}>
                    <Link
                      to={makeUrl(RouteConstants.ENTITY_PIPELINE_GRAPH_VERSION, {
                        entityId: pipeline.id,
                        graphVersion: 'new',
                      })}>
                      {pipeline.name}
                    </Link>
                    {index < data.usedInPipeline.length - 1 && ', '}
                  </span>
                );
              })}
            </span>
          );
        },
      },
      {
        headerName: tc('actions'),
        field: 'statusView',
        cellRendererFramework: ({ data }: { data: HTTPCustomSynapseEntityMeta | undefined }) => {
          return <EntitiesActions entity={data} />;
        },
        headerClass: 'actions',
        pinned: 'right',
        width: 80,
        resizable: false,
      },
    ],
    [utcToLocal]
  );

  const handleCreateEntity = useCallback(() => {
    const url = makeUrl(RouteConstants.SYNAPSES_CUSTOM_ENTITY, {
      synapseId: customSynapse?.id,
      entityId: 'new',
      version: entitiesMatch?.version,
    });
    navigate(url);
  }, [customSynapse?.id, entitiesMatch?.version]);

  const handleCloseEntityWizard = useCallback(() => {
    const url = makeUrl(RouteConstants.SYNAPSES_CUSTOM_ENTITIES, {
      synapseId: entitiesMatch?.synapseId,
      version: entitiesMatch?.version,
    });

    navigate(url);
  }, [entitiesMatch?.synapseId, entitiesMatch?.version]);

  const tableData = useMemo(() => {
    return entities
      ?.filter((entity) => entity.displayName?.toLowerCase().includes(filterString.toLocaleLowerCase()))
      ?.map((entity) => ({
        ...entity,
        routeVersion: entitiesMatch?.version,
        routeSynapseId: entitiesMatch?.synapseId,
      }));
  }, [entities, entitiesMatch?.version, entitiesMatch?.synapseId, filterString]);

  return (
    <div className="entites-list">
      <EntitiesToolbar />
      <div className="custom-synapse__top-actions">
        <SearchBox
          onChange={(event) => setFilterString(event.target.value)}
          placeholder={tc('search')}
          className="custom-synapse__search"
          value={filterString}
        />

        {isDraftRouteVersion && (
          <Can key="delete_entity" permission={AllPermissions.WRITE_CONNECTOR}>
            <Button type="primary" onClick={handleCreateEntity} icon="plus">
              {tn('new_entity')}
            </Button>
          </Can>
        )}
      </div>

      <div className="custom-synapse__table-container">
        <AgTable
          className={cx('custom-synapse__table', !tableData?.length && 'empty')}
          domLayout="autoHeight"
          columnDefs={columns}
          loading={entitiesLoading || customSynapseLoading}
          rowData={tableData}
          noRowsOverlayComponentParams={{
            description: tc('no_records_found'),
          }}
          getRowNodeId={(data) => data?.id + data?.metaId}
          sizeColumnsToFit={ResizeColumnsCondition.WHEN_NARROWER}
        />
      </div>

      <EntityWizard path="/:entityId" customSynapse={customSynapse} close={handleCloseEntityWizard} />
    </div>
  );
}
