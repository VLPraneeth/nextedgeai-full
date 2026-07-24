//
// Copyright (c) 2019-Present Syncari All rights reserved.
//

import { ReactNode, useCallback, useEffect, useMemo, useState } from 'react';
import { batch } from 'react-redux';

import { getConnectors } from 'actions/connectorActions';
import Button from 'components/Button';
import DrawerPanel from 'components/DrawerPanel';
import InlineMessage, { Types as InlineMessageTypes } from 'components/InlineMessage';
import Modal from 'components/Modal';
import TabPanelSpin from 'components/TabPanelSpin';
import { useEnhancedDispatch as useDispatch, useEnhancedSelector as useSelector } from 'hooks/redux';
import usePreviousValue from 'hooks/usePreviousValue';
import { selectConnectorsFetching } from 'selectors/connectorSelectors';
import { selectEntityById } from 'store/entity/selectors';
import { getEntities } from 'store/entity/thunks';
import {
  selectDeleteMappingsResponse,
  selectFastMapperEntityId,
  selectFastMapperModalVisible,
  selectGetMappingsStatus,
  selectSaveMappingErrorMessage,
  selectSaveMappingsResponse,
  selectSaveMappingsStatus,
} from 'store/fast-mapper/selectors';
import { resetAddMappingModal, showFastMapper, resetBrowseMappingModal } from 'store/fast-mapper/slice';
import { getMappings } from 'store/fast-mapper/thunks';
import { Mapping } from 'store/fast-mapper/types';
import AppConstants from 'utils/AppConstants';
import { tc, tNamespaced } from 'utils/i18nUtil';

import { AddMapping } from './AddMapping/AddMapping';
import { BrowseMapping } from './BrowseMapping';
import { isMappingEmpty } from './FastMapper.util';
import { EditedMapping } from './types';

import './FastMapperModal.scss';

const { FETCH_STATUS } = AppConstants;

const tn = tNamespaced('FastMapperModal');

export enum FastMapperMode {
  ADD = 'add',
  BROWSE = 'browse',
}

export const useFastMapper = () => {
  const visible = useSelector(selectFastMapperModalVisible);
  const entityId = useSelector(selectFastMapperEntityId);
  const saveMappingsStatus = useSelector(selectSaveMappingsStatus);
  const saveMappingsErrorMessage = useSelector(selectSaveMappingErrorMessage);
  const editMappingsErrorMessage = useSelector((state) => state.fastMapper.editMappingsErrorMessage);
  const editMappingsStatus = useSelector((state) => state.fastMapper.editMappingsStatus);
  const selectedEntity = useSelector((state) => selectEntityById(state, entityId));
  const [mode, setMode] = useState(FastMapperMode.BROWSE);
  const prevVisible = usePreviousValue(visible);
  const deleteMappingsResponse = useSelector(selectDeleteMappingsResponse);
  const previousDeleteMappingsResponse = usePreviousValue(deleteMappingsResponse);

  useEffect(() => {
    if (prevVisible !== visible && visible) {
      setMode(
        selectedEntity?.pipelineStatus === AppConstants.SYNCARI_NODE_STATUS.UNMAPPED
          ? FastMapperMode.ADD
          : FastMapperMode.BROWSE
      );
    }
  }, [visible, prevVisible, selectedEntity?.pipelineStatus]);

  const dispatch = useDispatch();
  const close = useCallback(() => dispatch(showFastMapper({ visible: false, entityId })), [dispatch, entityId]);

  return {
    visible,
    entityId,
    close,
    showFastMapper: useCallback(
      (entityId: string) => {
        dispatch(showFastMapper({ visible: true, entityId }));
      },
      [dispatch]
    ),
    switchToAdd: () => setMode(FastMapperMode.ADD),
    isAddMode: () => mode === FastMapperMode.ADD,
    switchToBrowse: () => setMode(FastMapperMode.BROWSE),
    isBrowseMode: () => mode === FastMapperMode.BROWSE,
    saveMappingsStatus,
    saveMappingsErrorMessage,
    editMappingsStatus,
    editMappingsErrorMessage,
    deleteMappingsResponse,
    previousDeleteMappingsResponse,
    entityPipelineStatus: selectedEntity?.pipelineStatus,
  };
};

const FastMapperModal = () => {
  const dispatch = useDispatch();
  const {
    visible,
    entityId,
    close,
    saveMappingsErrorMessage,
    deleteMappingsResponse,
    previousDeleteMappingsResponse,
    isAddMode,
    switchToAdd,
    isBrowseMode,
    switchToBrowse,
    editMappingsErrorMessage,
  } = useFastMapper();
  const [mappings, setMappings] = useState<Mapping[]>([]);
  const [editedMappings, setEditedMappings] = useState<EditedMapping[]>([]);
  const entity = useSelector((state) => selectEntityById(state, entityId));

  const mappingsStatus = useSelector(selectGetMappingsStatus);
  const fetchingConnectors = useSelector(selectConnectorsFetching);
  const loading = fetchingConnectors || mappingsStatus === FETCH_STATUS.LOADING;

  useEffect(() => {
    if (visible) {
      // Connectors are needed for the mapping
      dispatch(getConnectors());
    }
  }, [dispatch, visible]);

  const saveMappingsResponse = useSelector(selectSaveMappingsResponse);
  const prevSaveMappingsResponse = usePreviousValue(saveMappingsResponse);
  const editMappingsResponse = useSelector((state) => state.fastMapper.editMappingsResponse);
  const prevEditMappingsResponse = usePreviousValue(editMappingsResponse);
  const [successMessage, setSuccessMessage] = useState('');
  const [childFooter, setChildFooter] = useState<ReactNode>(null);

  const reset = useCallback(() => {
    setSuccessMessage('');
    dispatch(resetAddMappingModal());
    dispatch(resetBrowseMappingModal());
  }, [dispatch]);

  const closeModal = useCallback(() => {
    reset();
    close();
  }, [close, reset]);

  const switchToAddMode = useCallback(() => {
    reset();
    switchToAdd();
  }, [switchToAdd, reset]);

  const switchToBrowseMode = useCallback(() => {
    reset();
    switchToBrowse();
  }, [switchToBrowse, reset]);

  const title = useMemo(
    () =>
      isAddMode() ? tn('title', { name: entity?.displayName }) : tn('title_browse', { name: entity?.displayName }),
    [entity?.displayName, isAddMode]
  );

  const confirmSwitchToBrowse = useCallback(() => {
    if (!isMappingEmpty(mappings)) {
      Modal.confirm({
        title: tn('confirm_title'),
        content: tn('confirm_description'),
        okText: tc('discard_changes'),
        onOk: () => switchToBrowseMode(),
      });
    } else {
      switchToBrowseMode();
    }
  }, [mappings, switchToBrowseMode]);

  const confirmSwitchToAdd = useCallback(() => {
    if (editedMappings.length !== 0) {
      Modal.confirm({
        title: tn('confirm_title'),
        content: tn('confirm_description'),
        okText: tc('discard_changes'),
        onOk: () => switchToAddMode(),
      });
    } else {
      switchToAddMode();
    }
  }, [editedMappings.length, switchToAddMode]);

  const confirmCloseModal = useCallback(() => {
    if (editedMappings.length !== 0) {
      Modal.confirm({
        title: tn('confirm_title'),
        content: tn('confirm_description'),
        okText: tc('discard_changes'),
        onOk: () => closeModal(),
      });
    } else {
      closeModal();
    }
  }, [editedMappings.length, closeModal]);

  useEffect(() => {
    if (prevSaveMappingsResponse !== saveMappingsResponse && saveMappingsResponse?.success) {
      batch(() => {
        dispatch(resetAddMappingModal());
        dispatch(getMappings({ entityId }));
        if (saveMappingsResponse.newEntityDraft) {
          dispatch(getEntities());
        }
      });
      switchToBrowse();
      setSuccessMessage(tn('new_mapping_added', { count: saveMappingsResponse.result?.length }));
    }
  }, [prevSaveMappingsResponse, saveMappingsResponse, switchToBrowse, dispatch, entityId, reset]);

  useEffect(() => {
    if (prevEditMappingsResponse !== editMappingsResponse && editMappingsResponse?.success) {
      batch(() => {
        dispatch(getMappings({ entityId }));
        if (editMappingsResponse.result?.length) {
          dispatch(getEntities());
        }
      });
      setSuccessMessage(tn('remap_fields', { count: editMappingsResponse.result?.length }));
    }
  }, [dispatch, editMappingsResponse, entityId, prevEditMappingsResponse, reset, switchToBrowse]);

  useEffect(() => {
    if (previousDeleteMappingsResponse !== deleteMappingsResponse && deleteMappingsResponse?.success) {
      setSuccessMessage(tn('mapping_deleted', { count: deleteMappingsResponse.result?.length }));
      if (deleteMappingsResponse.newEntityDraft) {
        dispatch(getEntities());
      }
    }
  }, [previousDeleteMappingsResponse, deleteMappingsResponse, dispatch, reset]);

  return (
    <DrawerPanel
      className="fast-mapper-modal"
      footer={
        <div className="fast-mapper-modal__footer">
          {isAddMode() && (
            <Button
              key="cancel"
              className="fast-mapper-modal__button--secondary"
              onClick={confirmSwitchToBrowse}
              aria-label={tc('cancel')}>
              {tc('cancel')}
            </Button>
          )}
          {isBrowseMode() && (
            <Button
              key="ok"
              className="fast-mapper-modal__button--secondary"
              aria-label={tc('close')}
              onClick={confirmCloseModal}>
              {tc('close')}
            </Button>
          )}
          {childFooter}
        </div>
      }
      keyboard={false}
      onClose={isBrowseMode() ? confirmCloseModal : confirmSwitchToBrowse}
      title={title}
      visible={visible}
      width="full">
      <TabPanelSpin className="fast-mapper-modal__spinner" spinning={loading}>
        <InlineMessage type={InlineMessageTypes.ERROR} title={saveMappingsErrorMessage || editMappingsErrorMessage}>
          {saveMappingsErrorMessage || editMappingsErrorMessage}
        </InlineMessage>
        <InlineMessage type={InlineMessageTypes.SUCCESS} title={successMessage}>
          {successMessage}
        </InlineMessage>
        {isBrowseMode() && (
          <BrowseMapping
            switchToAdd={confirmSwitchToAdd}
            onChange={(editedMappings) => {
              setEditedMappings(editedMappings);
            }}
            setChildFooter={setChildFooter}
          />
        )}
        {isAddMode() && (
          <AddMapping
            switchToBrowse={confirmSwitchToBrowse}
            onChange={(values) => setMappings(values)}
            setChildFooter={setChildFooter}
          />
        )}
      </TabPanelSpin>
    </DrawerPanel>
  );
};

export default FastMapperModal;
