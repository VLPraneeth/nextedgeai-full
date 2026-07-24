import Modal, { ModalProps } from 'antd/lib/modal';
import Spin from 'antd/lib/spin';
import { useCallback, useEffect } from 'react';
import { connect, ConnectedProps } from 'react-redux';
import { bindActionCreators } from 'redux';

import Button from 'components/Button';
import InlineMessage, { Types as InlineMessageTypes } from 'components/InlineMessage';
import { Stack } from 'components/layout';
import ModalTable, { TBody, TD, TH, THead, TR } from 'components/ModalTable';
import useUserLocalMoment from 'hooks/moment';
import usePreviousValue from 'hooks/usePreviousValue';
import { RootState } from 'reducers/index';
import { resetPublishEntitySchema } from 'store/schema/actions';
import { selectEntitySchema, selectEntitySchemaStatus } from 'store/schema/selectors';
import {
  getSchemaForConnectorEntity as getSchemaForConnectorEntityAction,
  publishEntitySchemaAndRefreshConnector as publishEntitySchema,
} from 'store/schema/thunks';
import AppConstants from 'utils/AppConstants';
import { SHORT_DATE_24_TIME_TZ_FORMAT } from 'utils/DateUtil';
import { tNamespaced } from 'utils/i18nUtil';
import './EntityDraftPublishConfirmationModal.less';

const tn = tNamespaced('SchemaStudio.PublishDraftModal');
const { FETCH_STATUS } = AppConstants;

const mapState = (state: RootState, props: ConfirmationModalProps) => ({
  entitySchema: selectEntitySchema(state, props),
  entitySchemaStatus: selectEntitySchemaStatus(state, props),
  entitySchemaDraftPublishStatus: state.schema.entitySchemaDraftPublishStatus,
  entitySchemaDraftPublishErrorMessage: state.schema.entitySchemaDraftPublishErrorMessage,
});

const connector = connect(mapState, (dispatch) =>
  bindActionCreators(
    {
      getSchemaForConnectorEntity: getSchemaForConnectorEntityAction,
      publishEntitySchema,
      resetPublishEntitySchema,
    },
    dispatch
  )
);

interface ConfirmationModalProps extends ModalProps {
  entityId: string;
  connectorId: string;
  onRequestClose: () => void;
  onConfirm: (entityId: string, connectorId: string) => void;
}

type ReduxProps = ConnectedProps<typeof connector>;

const EntityDraftPublishConfirmationModal = ({
  entityId,
  connectorId,
  entitySchema,
  entitySchemaStatus,
  getSchemaForConnectorEntity,
  onRequestClose,
  onConfirm,
  entitySchemaDraftPublishStatus,
  entitySchemaDraftPublishErrorMessage,
  publishEntitySchema,
  resetPublishEntitySchema,
}: ConfirmationModalProps & ReduxProps) => {
  const moment = useUserLocalMoment();
  useEffect(() => {
    if (!entitySchema && entitySchemaStatus === FETCH_STATUS.IDLE) {
      getSchemaForConnectorEntity?.(entityId);
    }
  }, [entityId, getSchemaForConnectorEntity, entitySchema, entitySchemaStatus]);

  const previousEntitySchemaDraftPublishStatus = usePreviousValue(entitySchemaDraftPublishStatus);

  const updatedFields =
    entitySchema?.data
      .filter((field) => Boolean(field?.draft) && Boolean(field.draft.fields.lastUpdated))
      .map((field) => {
        const { lastUpdated, ...fields } = field.draft.fields;

        return {
          ...fields,
          lastUpdated: moment(lastUpdated, moment.ISO_8601).format(SHORT_DATE_24_TIME_TZ_FORMAT),
        };
      }) || [];

  useEffect(() => {
    if (entitySchemaDraftPublishStatus === FETCH_STATUS.SUCCESS) {
      connectorId && entityId && onConfirm(entityId, connectorId);
      onRequestClose();
    }
  }, [
    entitySchemaDraftPublishStatus,
    previousEntitySchemaDraftPublishStatus,
    entityId,
    connectorId,
    onConfirm,
    onRequestClose,
  ]);

  const onCancel = useCallback(() => {
    resetPublishEntitySchema();
    onRequestClose();
  }, [onRequestClose, resetPublishEntitySchema]);

  return (
    <Modal
      title={tn('title')}
      className="schema-entity-publish-modal"
      centered
      visible
      destroyOnClose
      onCancel={onCancel}
      footer={
        <>
          <Button key="cancel" onClick={onCancel}>
            {tn('cancel')}
          </Button>
          <Button
            key="ok"
            type="primary"
            onClick={() => connectorId && entityId && publishEntitySchema({ connectorId, entityId })}
            disabled={updatedFields.length < 1 || entitySchemaDraftPublishStatus === FETCH_STATUS.LOADING}>
            {tn('publish')}
          </Button>
        </>
      }>
      <>
        {entitySchemaDraftPublishErrorMessage && (
          <InlineMessage type={InlineMessageTypes.ERROR} title={entitySchemaDraftPublishErrorMessage}>
            {entitySchemaDraftPublishErrorMessage}
          </InlineMessage>
        )}
        <Spin spinning={entitySchemaStatus === FETCH_STATUS.LOADING}>
          <div className="schema-entity-publish-modal-content">
            {updatedFields.length > 0 ? (
              <Stack>
                <div>{tn('description', { count: updatedFields.length })}</div>
                <ModalTable flex className="schema-entity-publish-table">
                  <THead>
                    <TR>
                      <TH className="schema-field-displayname">{tn('field')}</TH>
                      <TH>{tn('last_updated')}</TH>
                    </TR>
                  </THead>
                  <TBody>
                    {updatedFields.map((field) => (
                      <TR className="schema-field" key={field.id}>
                        <TD className="schema-field-displayname">{field.displayName}</TD>
                        <TD>{field.lastUpdated?.trim()}</TD>
                      </TR>
                    ))}
                  </TBody>
                </ModalTable>
              </Stack>
            ) : (
              <div>{tn('no_updates')}</div>
            )}
          </div>
        </Spin>
      </>
    </Modal>
  );
};

export default connector(EntityDraftPublishConfirmationModal);
