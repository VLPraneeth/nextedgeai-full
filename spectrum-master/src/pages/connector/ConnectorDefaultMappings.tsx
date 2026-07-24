//
// Copyright (c) 2019-Present Syncari - All rights reserved.
//
import { Button, Spin } from 'antd';
import cx from 'classnames';
import { useMemo } from 'react';

import AgTable, { ResizeColumnsCondition } from 'components/AgTable';
import EmptyGraphPanel from 'components/EmptyGraphPanel';
import { withI18n, useI18nContext } from 'components/I18nProvider';
import { Stack } from 'components/layout';
import Modal from 'components/Modal';
import { Text } from 'components/typography';
import { useEnhancedSelector } from 'hooks/redux';
import { selectConnectorsMetadata } from 'selectors/connectorSelectors';
import { useGetDefaultMappingsQuery } from 'store/connector-meta/api';

import { useConnectorDetailsContext } from './ConnectorDetailsContext';

import './ConnectorDefaultMappings.scss';

const ConnectorDefaultMappings = withI18n(() => {
  const { defaultMappingsVisible, metaId = '', showDefaultMappings } = useConnectorDetailsContext();
  const connectorsMetadata = useEnhancedSelector(selectConnectorsMetadata);
  const { tc, tn } = useI18nContext();

  const { data: mappings, isLoading, isFetching } = useGetDefaultMappingsQuery({ metaId }, { skip: !Boolean(metaId) });

  const onClose = () => showDefaultMappings(false, '');

  const connectorMetadata = useMemo(() => connectorsMetadata?.find((meta) => meta.id === metaId), [
    connectorsMetadata,
    metaId,
  ]);

  const title = useMemo(() => {
    const displayName = connectorMetadata?.displayName;
    return displayName ? tn('title', { connectorMetadataName: displayName }) : tn('default_mapping');
  }, [connectorMetadata?.displayName, tn]);

  const columns = useMemo(() => {
    return [
      {
        headerName: tn('syncari_field_name'),
        field: 'syncariFieldName',
        sortable: true,
        resizable: true,
      },
      {
        headerName: tn('external_field_name'),
        field: 'externalFieldName',
        sortable: true,
        resizable: true,
      },
    ];
  }, [tn]);

  return (
    <Modal
      title={title}
      centered
      width="700px"
      visible={defaultMappingsVisible}
      className="connector-default-mappings"
      onOk={onClose}
      onCancel={onClose}
      footer={
        <Button type="primary" onClick={onClose}>
          {tc('close')}
        </Button>
      }
      destroyOnClose>
      <div className="connector-default-mappings__container">
        <Spin spinning={isLoading || isFetching}>
          <>
            {!mappings?.length && connectorMetadata?.displayName && !isLoading && !isFetching ? (
              <EmptyGraphPanel icon={connectorMetadata.iconUri}>{tn('no_default_mapping_connector')}</EmptyGraphPanel>
            ) : (
              <Stack spacing="lg">
                {mappings?.map((mapping) => {
                  const rowData = Object.keys(mapping.attributeMapping).map((syncariFieldName) => {
                    return {
                      syncariFieldName,
                      externalFieldName: mapping.attributeMapping[syncariFieldName],
                    };
                  });
                  return (
                    <Stack spacing="xxsm">
                      <Text size="lg" weight="bold">
                        {mapping.syncariEntity} ({mapping.direction})
                      </Text>
                      <Text>
                        {mapping.syncariEntity} &lt;&gt; {mapping.externalEntity}
                      </Text>
                      <div
                        className={cx('connector-default-mappings__map-table', {
                          'connector-default-mappings--empty': !rowData.length,
                        })}>
                        <AgTable
                          immutableData={false}
                          columnDefs={columns}
                          noRowsOverlayComponentProps={{ description: tn('no_default_mapping') }}
                          loading={isLoading}
                          rowData={rowData}
                          sizeColumnsToFit={ResizeColumnsCondition.WHEN_NARROWER}
                          domLayout="autoHeight"
                          enableCellTextSelection
                          colResizeDefault="shift"
                        />
                      </div>
                    </Stack>
                  );
                })}
              </Stack>
            )}
          </>
        </Spin>
      </div>
    </Modal>
  );
}, 'ConnectorDefaultMappings');

export { ConnectorDefaultMappings };
