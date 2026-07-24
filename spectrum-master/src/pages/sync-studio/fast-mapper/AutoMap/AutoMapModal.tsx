import { useMatch } from '@reach/router';
import { Button, Radio } from 'antd';
import Select, { OptionProps } from 'antd/lib/select';
import { sortBy } from 'lodash';
import { useCallback, useEffect, useMemo, useState } from 'react';

import InlineMessage, { Types as InlineMessageTypes } from 'components/InlineMessage';
import InputWithLabel from 'components/inputs/InputWithLabel';
import { Stack } from 'components/layout';
import Modal from 'components/Modal';
import { useEnhancedDispatch, useEnhancedSelector } from 'hooks/redux';
import { useConnectorIdToMetadataMap } from 'store/connectors';
import { selectUserConnectorsForDisplay } from 'store/connectors/selectors';
import { selectConnectorEntitiesOnly, selectConnectorFields } from 'store/entity/selectors';
import { getConnectorEntities, getEntityFields } from 'store/entity/thunks';
import { useAutoMapFieldsMutation } from 'store/fast-mapper/api';
import { getRtkQueryErrorMessage } from 'utils/getRtkQueryErrorMessage';
import { tc, tNamespaced } from 'utils/i18nUtil';

import { FastMapperMode } from '../FastMapperModal';
import { makeSynapseEntityOption, makeSynapseOption, useMapper } from '../Mapper';
import { useAutoMap } from './AutoMap.hooks';

import './AutoMapModal.scss';

const tn = tNamespaced('AutoMap');

type MapperType = 'basicSearch' | 'syncAI';

export const AutoMapModal = () => {
  const [errorMessage, setErrorMessage] = useState('');
  const [sourceSynapse, setSourceSynapse] = useState('');
  const [sourceEntity, setSourceEntity] = useState('');
  const [autoCreateFields, setAutoCreateFields] = useState(false);
  const [mapperType, setMapperType] = useState<MapperType>('basicSearch');
  const { visible, setVisible } = useAutoMap();
  const connectors = useEnhancedSelector(selectUserConnectorsForDisplay);
  const connectorEntities = useEnhancedSelector(selectConnectorEntitiesOnly);
  const connectorFields = useEnhancedSelector(selectConnectorFields);

  const dispatch = useEnhancedDispatch();
  const syncariEntityIdMatch = useMatch('/sync-studio/entity/:syncariEntityId/*');
  const [autoMapFields, { isLoading }] = useAutoMapFieldsMutation();
  const {
    tableDataHandlers: { addFullMapping, addSyncariField },
  } = useMapper(FastMapperMode.ADD);

  useEffect(() => {
    if (visible) {
      setErrorMessage('');
      setSourceSynapse('');
      setSourceEntity('');
      setAutoCreateFields(false);
      setMapperType('basicSearch');
    }
  }, [visible]);

  const onApply = useCallback(async () => {
    if (syncariEntityIdMatch?.syncariEntityId) {
      setErrorMessage('');
      const fields: any = await autoMapFields({
        syncariEntityId: syncariEntityIdMatch.syncariEntityId,
        sourceEntityId: sourceEntity,
        autoCreateUnmappedFields: autoCreateFields,
        mapperType: mapperType,
      });
      if (fields?.data?.length <= 0) {
        setErrorMessage(tn('empty_auto_map'));
        return;
      }
      const rtkErrorMessage = getRtkQueryErrorMessage(fields.error);
      if (rtkErrorMessage) {
        setErrorMessage(rtkErrorMessage);
        return;
      }
      if (fields.data?.length) {
        fields.data?.forEach((mapping: any) => {
          const {
            id,
            directions,
            createNewSyncariField,
            syncariFieldApiName: apiName,
            syncariFieldDatatype: dataType,
            syncariFieldDisplayName: displayName,
            syncariFieldIsMultiValued: isMultivalued,
            syncariFieldIsRequired: isRequired,
            ...fullMapping
          } = mapping;
          const synapseReadOnly =
            connectorFields[sourceEntity]?.data?.find((field) => field.id === mapping.synapseFieldId)?.readOnly ?? true;
          addFullMapping({
            ...fullMapping,
            id,
            synapseReadOnly,
            syncDirectionId: directions[0],
          });

          if (createNewSyncariField) {
            addSyncariField({
              id,
              createNewSyncariField,
              apiName,
              dataType,
              displayName,
              isMultivalued,
              isRequired,
              title: `${displayName} (${apiName})`,
            });
          }
        });
      }
      setVisible(false);
    }
  }, [
    addFullMapping,
    addSyncariField,
    autoCreateFields,
    autoMapFields,
    connectorFields,
    mapperType,
    setVisible,
    sourceEntity,
    syncariEntityIdMatch?.syncariEntityId,
  ]);

  const onClose = useCallback(() => setVisible(false), [setVisible]);

  const filterOption = useCallback(
    (input: string, option: React.ReactElement<OptionProps>) =>
      (option.props.title?.toLowerCase() || '').indexOf(input.toLowerCase()) >= 0,
    []
  );

  const connectorIdToMetadataMap = useConnectorIdToMetadataMap();

  useEffect(() => {
    if (sourceSynapse && !connectorEntities?.[sourceSynapse]) {
      dispatch(getConnectorEntities(sourceSynapse, false, true));
    }
  }, [connectorEntities, dispatch, sourceSynapse]);

  const connectorOptions = useMemo(() => {
    const sortedConnectors = sortBy(connectors, 'name');
    // @ts-ignore
    const options = sortedConnectors?.map(makeSynapseOption(connectorIdToMetadataMap));
    return options;
  }, [connectorIdToMetadataMap, connectors]);

  const entityOptions = useMemo(() => {
    if (!sourceSynapse) {
      return [];
    }
    const entities = connectorEntities?.[sourceSynapse]?.data;
    const sortedEntity = sortBy(entities, 'name');

    const options = sortedEntity.map(makeSynapseEntityOption);
    return options;
  }, [connectorEntities, sourceSynapse]);

  return (
    <Modal
      title={tn('title')}
      centered
      visible={visible}
      footer={
        <>
          <Button key="cancel" onClick={onClose} disabled={isLoading}>
            {tc('cancel')}
          </Button>
          <Button key="ok" type="primary" onClick={onApply} loading={isLoading} disabled={isLoading}>
            {tc('apply')}
          </Button>
        </>
      }
      onOk={onApply}
      onCancel={onClose}
      destroyOnClose>
      <div className="automap-content-container">
        <InlineMessage type={InlineMessageTypes.ERROR} title={errorMessage}>
          {errorMessage}
        </InlineMessage>
        <form onSubmit={onApply}>
          <Stack>
            <InputWithLabel
              name="sourceSynapse"
              label={tn('source_synapse')}
              input={
                <Select
                  value={sourceSynapse}
                  onChange={(value: string) => {
                    setSourceSynapse(value);
                    setSourceEntity('');
                  }}
                  showSearch
                  dropdownMatchSelectWidth
                  filterOption={filterOption}
                  optionFilterProp="title">
                  {connectorOptions}
                </Select>
              }
            />
            <InputWithLabel
              name="sourceEntity"
              label={tn('source_entity')}
              input={
                <Select
                  value={sourceEntity}
                  onChange={(value: string) => {
                    dispatch(getEntityFields(value));
                    setSourceEntity(value);
                  }}
                  showSearch
                  dropdownMatchSelectWidth
                  filterOption={filterOption}
                  optionFilterProp="title">
                  {entityOptions}
                </Select>
              }
            />
            <InputWithLabel
              name="mapperType"
              label="Map Using"
              tooltip={tn('tool_tip')}
              input={
                <Radio.Group value={mapperType} onChange={(e) => setMapperType(e.target.value)}>
                  <Radio value="basicSearch">{tn('basic_search')}</Radio>
                  <Radio value="syncAI">{tn('sync_ai')}</Radio>
                </Radio.Group>
              }
            />
            <InputWithLabel
              name="autoCreateFields"
              label={tn('auto_create_no_match')}
              datatype="checkbox"
              value={autoCreateFields}
              checked={autoCreateFields}
              onChange={(evt: React.ChangeEvent<HTMLInputElement>) => {
                setAutoCreateFields(evt.target.checked);
              }}
            />
          </Stack>
        </form>
      </div>
    </Modal>
  );
};
