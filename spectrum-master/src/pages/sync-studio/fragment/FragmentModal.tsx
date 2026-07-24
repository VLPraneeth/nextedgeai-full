//
// Copyright (c) 2019-Present Syncari - All rights reserved.
//
import { Button } from 'antd';
import cx from 'classnames';
import { useCallback, useEffect, useState } from 'react';

import DrawerPanel from 'components/DrawerPanel';
import InlineMessage, { Types as InlineMessageTypes } from 'components/InlineMessage';
import InputWithLabel from 'components/inputs/InputWithLabel';
import { TagValueModel } from 'components/inputs/Tag';
import { PipelineErrorPanel } from 'components/pipeline-error-panel/PipelineErrorPanel';
import TabPanelSpin from 'components/TabPanelSpin';
import usePreviousValue from 'hooks/usePreviousValue';
import { FragmentModel, NodeCheckValuesModel } from 'store/fragment/types';
import { ENTITY_DRAWER_HEIGHT_OFFSET, FIELD_DRAWER_HEIGHT_OFFSET } from 'styles/style.constants';
import { tc, tNamespaced } from 'utils/i18nUtil';

import './FragmentModal.less';

const tn = tNamespaced('FragmentModal');

export interface FragmentModalProps {
  className?: string;
  clearNodeCheckValues: () => void;
  pipelineContext?: string;
  createFragmentVisible: boolean;
  enableNodeCheck: (enable?: boolean) => void;
  errorMessage?: string;
  fragmentSaving: boolean;
  nodeCheckValues: NodeCheckValuesModel;
  resetFragmentModal: () => void;
  saveFragment: (fragment: FragmentModel) => void;
  saveFragmentErrorMessage?: string;
  selectAllNodeCheck: () => void;
  showCreateFragmentModal: (visble?: boolean) => void;
  unselectAllNodeCheck: () => void;
  validate?: () => void;
  validating: boolean;
}

const FragmentModal = ({
  className,
  clearNodeCheckValues,
  pipelineContext,
  createFragmentVisible,
  enableNodeCheck,
  errorMessage,
  fragmentSaving,
  nodeCheckValues,
  resetFragmentModal,
  saveFragment,
  saveFragmentErrorMessage,
  selectAllNodeCheck,
  showCreateFragmentModal,
  unselectAllNodeCheck,
  validate,
  validating,
}: FragmentModalProps) => {
  const [valuesCount, setValuesCount] = useState(0);
  const [formValues, setFormValues] = useState<FragmentModel | null>();
  const wasSaving = usePreviousValue<boolean>(fragmentSaving);

  const previousCreateFragmentVisible = usePreviousValue(createFragmentVisible);

  useEffect(() => {
    // validate is new on every re-render so checking the
    // previousCreateFragmentVisible to only call validate when that changes.
    if (previousCreateFragmentVisible !== createFragmentVisible) {
      createFragmentVisible && validate?.();
    }

    return () => {
      if (!createFragmentVisible) {
        setFormValues({});
      }
    };
  }, [createFragmentVisible, previousCreateFragmentVisible, validate]);

  useEffect(() => {
    setValuesCount(Object.keys(nodeCheckValues)?.filter((key) => nodeCheckValues[key]).length);
  }, [nodeCheckValues]);

  const close = useCallback(() => {
    setFormValues({});
    enableNodeCheck(false);
    showCreateFragmentModal(false);
    resetFragmentModal();
  }, [enableNodeCheck, showCreateFragmentModal, resetFragmentModal]);

  useEffect(() => {
    if (wasSaving === true && fragmentSaving === false && !saveFragmentErrorMessage) {
      close();
    }
  }, [wasSaving, fragmentSaving, saveFragmentErrorMessage, close]);

  const onTextChange = (evt: React.ChangeEvent<HTMLInputElement>) => {
    setFormValues({
      ...formValues,
      [evt.target.name]: evt.target.value,
    });
  };

  const onTagChange = (tags: TagValueModel) => {
    setFormValues({
      ...formValues,
      tags,
    });
  };

  const unselectAll = () => {
    unselectAllNodeCheck();
    clearNodeCheckValues();
  };

  if (!validating && errorMessage) {
    return <PipelineErrorPanel onClose={close} title={tn('new_fragment')} visible={createFragmentVisible} />;
  }

  return (
    <DrawerPanel
      absolutePositioning
      className={cx('synri-fragment-modal', className)}
      additionalHeightOffset={pipelineContext === 'entity' ? ENTITY_DRAWER_HEIGHT_OFFSET : FIELD_DRAWER_HEIGHT_OFFSET}
      onClose={close}
      title={tn('new_fragment')}
      visible={createFragmentVisible}
      footer={
        <>
          <Button onClick={() => formValues && saveFragment(formValues)} type="primary" disabled={validating}>
            {tc('save')}
          </Button>
          <Button onClick={close} disabled={validating}>
            {tc('cancel')}
          </Button>
        </>
      }>
      <TabPanelSpin spinning={validating} tip={tn('validating')}>
        <InlineMessage
          className="synri-fragment-modal__message"
          type={InlineMessageTypes.ERROR}
          title={saveFragmentErrorMessage}>
          {saveFragmentErrorMessage}
        </InlineMessage>
        <div className="synri-fragment-modal__node-selection">
          <div>{tn('node_selected', { count: valuesCount })}</div>
          <div>
            <Button className="synri-fragment-modal__button-left" onClick={selectAllNodeCheck} type="link">
              {tn('select_all')}
            </Button>
            |
            <Button className="synri-fragment-modal__button-right" onClick={unselectAll} type="link">
              {tn('clear_all')}
            </Button>
          </div>
        </div>
        <InputWithLabel
          name="displayName"
          datatype="string"
          label={tn('name')}
          value={formValues?.displayName}
          onChange={onTextChange}
        />
        <InputWithLabel
          name="description"
          datatype="textarea"
          label={tn('description')}
          value={formValues?.description}
          onChange={onTextChange}
        />
        <InputWithLabel
          name="tags"
          datatype="tag"
          label={tn('tags')}
          defaultValue={formValues?.tags}
          onChange={onTagChange}
        />
      </TabPanelSpin>
    </DrawerPanel>
  );
};

export default FragmentModal;
