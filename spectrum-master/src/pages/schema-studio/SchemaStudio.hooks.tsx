import { Modal } from 'antd';

import { useI18nNamespace } from 'components/I18nProvider';
import { useEnhancedDispatch } from 'hooks/redux';
import { useConnectorCapabilities } from 'store/connectors';
import { discardEntitySchemaAndRefreshConnector, EntitySchemaParams } from 'store/schema/thunks';
import { Connector } from 'store/schema/types';
import AppConstants from 'utils/AppConstants';
/**
 * Discards an existing entity draft after confirming with user
 */
export const useDiscardEntityDraftWithConfirm = (onConfirm?: () => void) => {
  const dispatch = useEnhancedDispatch();

  const tc = useI18nNamespace();
  const tn = useI18nNamespace('SchemaStudio.EntityTable');

  return ({ entityId, connectorId, entityName }: EntitySchemaParams & { entityName: string }) =>
    new Promise((resolve, reject) => {
      Modal.confirm({
        title: tn('actions.confirm_delete_draft', { entityName }),
        cancelText: tc('cancel'),
        okText: tn('actions.delete'),
        onCancel: () => reject(),
        onOk: () => {
          onConfirm?.();
          dispatch(discardEntitySchemaAndRefreshConnector({ entityId, connectorId })).then(resolve);
        },
      });
    });
};

export const useFieldSchema = (synapse?: Connector, isSyncariDefined?: boolean) => {
  const { capabilities, isSyncariConnector } = useConnectorCapabilities(synapse?.id);
  const isDynamoDb = synapse?.typeName === AppConstants.SYNAPSE_NAMES.AMAZON_DYNAMO_DB;
  const isImportedFiles = synapse?.typeName === AppConstants.SYNAPSE_NAMES.IMPORTED_FILES;

  return {
    userEditableId: capabilities.includes('userEditableId'),
    userEditableWm: capabilities.includes('userEditableWm'),
    userEditableReadOnly: capabilities.includes('userEditableReadOnly'),
    parentAttributeSupported: synapse?.typeName === AppConstants.SYNAPSE_NAMES.MARKETO,
    multiValueFieldEnabled: isSyncariConnector || isSyncariDefined || isDynamoDb || isImportedFiles,
    canCreateSchemaField: isSyncariConnector || capabilities.includes('schemaCreateField'),
    isDynamoDb,
    isPostgresDb: synapse?.typeName === AppConstants.SYNAPSE_NAMES.POSTGRESQL,
    showCompositeKey: capabilities.includes('compositeId'),
    isImportedFiles,
    showUnique: [AppConstants.SYNAPSE_NAMES.AMAZON_DYNAMO_DB, AppConstants.SYNAPSE_NAMES.MARKETO].includes(
      synapse?.typeName
    ),
  };
};
