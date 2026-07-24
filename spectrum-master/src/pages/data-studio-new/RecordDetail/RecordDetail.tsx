import { RouteComponentProps, Router, useNavigate, useLocation } from '@reach/router';
import { useEffect, useState } from 'react';

import { useI18nContext } from 'components/I18nProvider';

import RecordFields from './Fields';
import './RecordDetail.less';
import { makeUrl } from 'utils/UrlUtil';
import RouteConstants from 'utils/RouteConstants';
import { Icon } from 'antd';

interface DataStudioRecordDetailProps extends RouteComponentProps {
  onClose?: () => void;
  entityId: string;
  onRecordCreated?: () => void;
  onRecordDeleted?: () => void;
}

type RecordDetailMode = 'view' | 'edit' | 'create';

const DataStudioRecordDetail = ({
  onClose,
  entityId,
  onRecordCreated,
  onRecordDeleted,
}: DataStudioRecordDetailProps) => {
  const { tn } = useI18nContext();
  const navigate = useNavigate();
  const location = useLocation();

  // Check if we're on a record detail route (fields or lineage)
  const isCreateRoute = location?.pathname.includes('/create');
  const isEditFieldsRoute = location?.pathname.includes('/fields') && !location?.pathname.includes('/view');

  const mode: RecordDetailMode = isCreateRoute ? 'create' : isEditFieldsRoute ? 'edit' : 'view';

  const handleClose = () => {
    if (onClose) {
      onClose();
    } else {
      // Navigate back to entity grid when closing
      navigate(makeUrl(RouteConstants.DATA_STUDIO_ENTITY, { entityId }));
    }
  };

  useEffect(() => {
    const header = document.querySelector('.main-header');
    if (header) {
      if (mode) {
        header.setAttribute('data-drawer-visible', 'true');
      } else {
        header.removeAttribute('data-drawer-visible');
      }
    }
    return () => {
      if (header) {
        header.removeAttribute('data-drawer-visible');
      }
    };
  }, [mode]);

  return (
    <>
      <div className="record-detail-drawer" data-testid="record-detail-drawer" data-drawer-visible={Boolean(mode)}>
        <div className="record-detail-content" data-testid="record-detail-content">
          <div className="record-detail-header">
            <h3>{mode === 'view' ? 'View Record' : mode === 'edit' ? 'Edit Record' : 'Create New Record'}</h3>
            <button aria-label="close drawer" type="button" className="close-btn" onClick={handleClose}>
              <Icon className="close-icon" type="close" />
            </button>
          </div>
          <div className="record-detail-body" data-testid="record-detail-body">
            <div className="data-studio-record-detail-content">
              <Router>
                <RecordFields
                  path={isEditFieldsRoute ? 'fields' : 'fields/:mode'}
                  displayMode={mode}
                  onRecordCreated={onRecordCreated}
                  onRecordDeleted={onRecordDeleted}
                />
              </Router>
              {isCreateRoute && (
                <RecordFields displayMode={mode} onRecordCreated={onRecordCreated} onRecordDeleted={onRecordDeleted} />
              )}
            </div>
          </div>
        </div>
      </div>
    </>
  );
};

export default DataStudioRecordDetail;
