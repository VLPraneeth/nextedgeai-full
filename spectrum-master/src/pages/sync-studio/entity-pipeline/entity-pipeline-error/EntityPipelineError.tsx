import { useNavigate } from '@reach/router';
import { Button, Icon } from 'antd';
import { useCallback } from 'react';

import { getEntityPipeline } from 'actions/entityPipelineActions';
import { useI18nContext, withI18n } from 'components/I18nProvider';
import { useEnhancedDispatch } from 'hooks/redux';
import RouteConstants from 'utils/RouteConstants';
import { makeUrl } from 'utils/UrlUtil';

import './EntityPipelineError.less';

export interface EntityPipelineErrorProps {
  entityId: string;
  error: string;
}

export const EntityPipelineError = withI18n(({ entityId, error }: EntityPipelineErrorProps) => {
  const { tc, tn } = useI18nContext();

  const navigate = useNavigate();
  const dispatch = useEnhancedDispatch();

  const handleGotoSyncStudio = useCallback(() => {
    navigate(makeUrl(RouteConstants.SYNC_STUDIO));
  }, [navigate]);

  const handleRefresh = useCallback(() => {
    dispatch(getEntityPipeline(entityId));
  }, [dispatch, entityId]);

  return (
    <div className="entity-pipeline-error">
      <Icon className="entity-pipeline-error__icon" type="exclamation-circle" theme="filled" />
      <h1 className="entity-pipeline-error__title">{tn('title')}</h1>
      <h2 className="entity-pipeline-error__error">{error}</h2>
      <nav className="entity-pipeline-error__navigation">
        <Button onClick={handleGotoSyncStudio}>{tn('go_to_sync_studio')}</Button>
        <Button type="primary" onClick={handleRefresh}>
          {tc('retry')}
        </Button>
      </nav>
    </div>
  );
}, 'EntityPipelineError');
