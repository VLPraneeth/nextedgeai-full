//
// Copyright (c) 2019-Present Syncari - All rights reserved.
//
import { Link, useMatch } from '@reach/router';
import { Alert, Button, message, Modal, Radio, Spin } from 'antd';
import cx from 'classnames';
import { cloneDeep, isArray } from 'lodash';
import moment from 'moment';
import * as React from 'react';
import { useCallback, useEffect, useRef, useState, useMemo } from 'react';
import { connect, ConnectedProps } from 'react-redux';
import { bindActionCreators, Dispatch } from 'redux';

import {
  approveEntityPipeline,
  getEntityPipeline,
  getFieldDraftSummary,
  initializeApproveModal,
  showPublishDraftModal,
} from 'actions/entityPipelineActions';
import InlineMessage, { Types as InlineMessageTypes } from 'components/InlineMessage';
import InputWithLabel from 'components/inputs/InputWithLabel';
import { Spacer } from 'components/layout';
import { TextTag } from 'components/text-tag';
import { RootState } from 'reducers/index';
import { getEntities } from 'store/entity/actions';
import { getSchemaForEntity } from 'store/schema/thunks';
import AppConstants from 'utils/AppConstants';
import { SHORT_DATE_TIME_FORMAT } from 'utils/DateUtil';
import { tc, tNamespaced } from 'utils/i18nUtil';
import { navigateToGraphVersion } from 'utils/PipelineUtil';
import RouteConstants from 'utils/RouteConstants';
import { routeToMatch } from 'utils/StringUtil';
import { replaceToken } from 'utils/UrlUtil';

import './PublishDraftModal.scss';

export const ACTIONS = {
  PUBLISH_DRAFT: 'PUBLISH_DRAFT',
  DISCARD_DRAFT: 'DISCARD_DRAFT',
  DELETE_PUBLISHED: 'DELETE_PUBLISHED',
};

const tn = tNamespaced('PublishDraftModal');
const tv = tNamespaced('CreateVersionModal');
const PUBLISH_TYPE = {
  ALL: 'ALL',
  READY_ONLY: 'READY_ONLY',
};

interface FieldDraftSummaryModel {
  name: string;
  ready: boolean;
  updatedAt?: string;
  isDeleted: boolean;
  hasChanges: boolean;
  draftStatus: string;
  id: string;
}

const mapStateToProps = (state: RootState) => ({
  publishDraftModalEntityId: state.entityPipeline.publishDraftModalEntityId,
  hasUnpublishedSynapse: state.entityPipeline.hasUnpublishedSynapse,
  entities: state.entity.entities,
  entityPipeline: state.entityPipeline,
  fieldDraftSummary: state.entityPipeline.fieldDraftSummary,
  fieldDraftSummaryFetching: state.entityPipeline.fieldDraftSummaryFetching,
  entityPipelineApproving: state.entityPipeline.entityPipelineApproving,
  entityPipelineApprovingErrorMsg: state.entityPipeline.entityPipelineApprovingErrorMsg,
  graphForPublishReadyOnly: state.entityPipeline.graphForPublishReadyOnly,
  entitySchemas: state.schema.entities,
});

const mapDispatchToProps = (dispatch: Dispatch) => {
  return bindActionCreators(
    {
      showPublishDraftModal,
      getFieldDraftSummary,
      approveEntityPipeline,
      initializeApproveModal,
      getEntityPipeline,
      getEntities,
      getSchemaForEntity,
    },
    dispatch
  );
};

const connector = connect(mapStateToProps, mapDispatchToProps);
type PublishDraftModalPropsFromRedux = ConnectedProps<typeof connector>;
interface PublishDraftModalProps {}

const PublishDraftModal = ({
  publishDraftModalEntityId,
  hasUnpublishedSynapse,
  getFieldDraftSummary,
  initializeApproveModal,
  showPublishDraftModal,
  approveEntityPipeline,
  getEntityPipeline,
  getEntities,
  getSchemaForEntity,
  entities,
  fieldDraftSummary,
  fieldDraftSummaryFetching,
  entityPipelineApprovingErrorMsg,
  entityPipelineApproving,
  graphForPublishReadyOnly,
  entitySchemas,
}: PublishDraftModalProps & PublishDraftModalPropsFromRedux) => {
  const initialEntityPipelineApproving = useRef<boolean>(entityPipelineApproving);
  const [versionName, setVersionName] = useState('');
  const [versionSummary, setVersionSummary] = useState('');
  const [publishingDraft, setPublishingDraft] = useState(!!entityPipelineApproving);
  const [publishType, setPublishType] = useState(PUBLISH_TYPE.READY_ONLY);
  const [publishReadyCount, setPublishReadyCount] = useState(0);
  const [publishAllCount, setPublishAllCount] = useState(0);
  const [publishDeletedCount, setPublishDeletedCount] = useState(0);
  const [hasValidationError, setHasValidationError] = useState(false);

  const entityMatch = useMatch(routeToMatch(RouteConstants.ENTITY));

  const publishButtonDisabled = publishingDraft || fieldDraftSummaryFetching;
  const entity = entities?.find((entity) => entity.id === publishDraftModalEntityId);
  const entityVersion =
    (entity?.pipelineStatus as string) === AppConstants.GRAPH_STATUS.APPROVED_WITH_DRAFT ? 'DRAFT' : 'NEW';

  const entitySchema = useMemo(() => {
    let key: string | undefined = undefined;

    if (entitySchemas && entity) {
      key = Object.keys(entitySchemas).find((key) => {
        const [entityId, version] = key.split('-');

        return entityId === publishDraftModalEntityId && version === entityVersion;
      });
    }

    return entity && entitySchemas && key ? entitySchemas[key] : undefined;
  }, [entity, entitySchemas, entityVersion, publishDraftModalEntityId]);

  const summaries = useMemo(() => {
    const summaries = fieldDraftSummary?.[publishDraftModalEntityId];
    const deletedFields = entitySchema?.fields.filter((field) => field.hasPublishedPipeline && !field.isMapped);

    let result: FieldDraftSummaryModel[] = isArray(summaries) ? cloneDeep(summaries) : [];

    deletedFields?.forEach((field) => {
      const summaryIndex = result?.findIndex((summary: FieldDraftSummaryModel) => summary.id === field.id);

      if (summaryIndex !== -1) {
        result[summaryIndex].isDeleted = true;
      } else {
        result = [
          ...result,
          {
            id: field.id,
            ready: false,
            isDeleted: true,
            hasChanges: false,
            draftStatus: '',
            name: field.displayName,
          },
        ];
      }
    });

    return result;
  }, [entitySchema?.fields, fieldDraftSummary, publishDraftModalEntityId]);

  useEffect(() => {
    if (publishDraftModalEntityId) {
      getFieldDraftSummary(publishDraftModalEntityId);
    }
    return () => {
      initializeApproveModal();
    };
  }, [publishDraftModalEntityId, initializeApproveModal, getFieldDraftSummary]);

  useEffect(() => {
    getSchemaForEntity({ entityId: publishDraftModalEntityId, graphVersion: entityVersion });
  }, [entityVersion, getSchemaForEntity, publishDraftModalEntityId]);

  useEffect(() => {
    const readyCount = summaries?.filter((summary: FieldDraftSummaryModel) => summary.ready).length;

    const deletedCount = summaries?.filter((summary: FieldDraftSummaryModel) => summary.isDeleted).length;
    setPublishType(readyCount <= 0 ? PUBLISH_TYPE.ALL : PUBLISH_TYPE.READY_ONLY);
    setPublishReadyCount(readyCount);
    setPublishAllCount(summaries?.length);
    setPublishDeletedCount(deletedCount);
  }, [summaries]);

  const close = useCallback(() => {
    showPublishDraftModal(false);
  }, [showPublishDraftModal]);

  const onPublishSuccess = useCallback(() => {
    getEntityPipeline(publishDraftModalEntityId);
    getEntities();

    if (entityMatch) {
      message.success(tn('publishing_successful'));
    } else {
      navigateToGraphVersion({
        entityId: publishDraftModalEntityId,
        graphVersion: AppConstants.GRAPH_STATUS.APPROVED,
      });
    }

    close();
  }, [getEntityPipeline, publishDraftModalEntityId, getEntities, entityMatch, close]);

  useEffect(() => {
    if (
      initialEntityPipelineApproving.current === true &&
      entityPipelineApproving === false &&
      !entityPipelineApprovingErrorMsg
    ) {
      onPublishSuccess();
    }
    initialEntityPipelineApproving.current = entityPipelineApproving;
    setPublishingDraft(entityPipelineApproving);
  }, [entityPipelineApproving, entityPipelineApprovingErrorMsg, onPublishSuccess]);

  const publish = () => {
    if (!versionName.trim()) {
      setHasValidationError(true);
      return;
    }

    setPublishingDraft(true);
    approveEntityPipeline(
      publishDraftModalEntityId,
      { name: versionName.trim(), summary: versionSummary },
      false,
      // TODO: Switch this to ready only flag
      publishType === PUBLISH_TYPE.READY_ONLY,
      graphForPublishReadyOnly
    );
  };

  const fieldPipelines = (
    <>
      {publishAllCount > 0 &&
        summaries
          ?.filter((summary: FieldDraftSummaryModel) =>
            publishType === PUBLISH_TYPE.READY_ONLY ? summary.ready : true
          )
          .sort(
            // @ts-ignore
            (a: FieldDraftSummaryModel, b: FieldDraftSummaryModel) => a?.name && b?.name && a.name.localeCompare(b.name)
          )
          .map((summary: FieldDraftSummaryModel) => {
            const updatedAt = moment(summary.updatedAt ? summary.updatedAt : Date.now()).format(SHORT_DATE_TIME_FORMAT);
            const field = entitySchema?.fields.find((field) => field.id === summary.id);

            const url = replaceToken(RouteConstants.FIELD_PIPELINE, {
              entityId: publishDraftModalEntityId,
              fieldId: summary.id,
            });

            const tags: JSX.Element[] = [];

            if (summary.ready) {
              tags.push(<TextTag color="green" text={tc('ready')} />);
            }

            if (summary.isDeleted) {
              tags.push(<TextTag color="red" text={tc('deleted')} />);
            } else if (summary.hasChanges) {
              if (!field?.hasPublishedPipeline) {
                tags.push(<TextTag color="blue" text={tc('created')} />);
              } else {
                tags.push(<TextTag color="orange" text={tc('updated')} />);
              }
            }

            return (
              <div className="publish-draft-modal__pipeline" key={`field-pipeline-${summary.id}`}>
                <div>
                  <span className="publish-draft-modal__pipeline-name">
                    <Link to={url} onClick={close}>
                      {summary.name}
                    </Link>
                  </span>
                  <span className="publish-draft-modal__pipeline-updated-at">({updatedAt})</span>
                </div>
                <div className="publish-draft-modal__pipeline-tags">{tags}</div>
              </div>
            );
          })}
    </>
  );

  const description = useMemo(() => {
    const publishCount =
      (publishType === PUBLISH_TYPE.READY_ONLY ? publishReadyCount : publishAllCount) - publishDeletedCount;
    let description = [tn('publishing')];

    if (publishCount > 0) {
      description.push(` ${tn('publish_pipeline', { count: publishCount })}`);
    }

    if (publishCount > 0 && publishDeletedCount > 0) {
      description.push(`, ${tc('and')}`);
    }

    if (publishDeletedCount > 0) {
      description.push(` ${tn('delete_pipeline', { count: publishDeletedCount })}`);
    }

    description.push(':');

    return description.join('');
  }, [publishAllCount, publishDeletedCount, publishReadyCount, publishType]);

  return (
    <Modal
      title={tn('title')}
      className={cx('publish-draft-modal')}
      centered
      visible
      footer={
        <>
          <Button key="cancel" onClick={close}>
            {tc('cancel')}
          </Button>
          <Button key="ok" type="primary" onClick={publish} disabled={publishButtonDisabled}>
            {tn(publishingDraft ? 'publishing_draft' : 'published_draft')}
          </Button>
        </>
      }
      onOk={close}
      onCancel={close}
      destroyOnClose>
      <div className="publish-draft-modal__content">
        {entityPipelineApprovingErrorMsg && (
          <InlineMessage type={InlineMessageTypes.ERROR} title={entityPipelineApprovingErrorMsg}>
            {entityPipelineApprovingErrorMsg}
          </InlineMessage>
        )}
        {hasUnpublishedSynapse && (
          <>
            <Alert type={InlineMessageTypes.INFO} message={tn('pipeline_contains_unpublished_synapse')} />
            <Spacer y="md" />
          </>
        )}
        <InputWithLabel
          label={tv('version_name')}
          required
          placeholder={tv('type_version_name')}
          validateStatus={hasValidationError ? 'error' : undefined}
          datatype={AppConstants.INPUT_TYPE.STRING}
          value={versionName}
          onChange={(e: React.ChangeEvent<HTMLInputElement>) => setVersionName(e.target.value)}
        />
        <InputWithLabel
          label={tv('summary_optional')}
          placeholder={tv('add_description')}
          datatype={AppConstants.INPUT_TYPE.TEXTAREA}
          value={versionSummary}
          onChange={(e: React.ChangeEvent<HTMLInputElement>) => setVersionSummary(e.target.value)}
        />
        <h4>{tn('field_pipelines')}</h4>
        <Radio.Group
          onChange={(evt) => setPublishType(evt.target.value)}
          value={publishType}
          className="publish-draft-modal__type-selection">
          <Radio value={PUBLISH_TYPE.READY_ONLY} disabled={publishReadyCount === 0}>
            {tn('publishing_ready', { count: publishReadyCount || 0 })}
          </Radio>
          <Radio value={PUBLISH_TYPE.ALL}>{tn('publishing_all', { count: publishAllCount })}</Radio>
        </Radio.Group>
        <h4 className="publish-draft-modal__pipelines-summary">{description}</h4>
        <div
          className={cx('publish-draft-modal__pipelines-container', {
            'publish-draft-modal__pipelines-container--with-error': entityPipelineApprovingErrorMsg,
          })}>
          <Spin tip={tc('loading')} spinning={fieldDraftSummaryFetching}>
            {fieldPipelines}
          </Spin>
        </div>
      </div>
    </Modal>
  );
};

export default connector(PublishDraftModal);
