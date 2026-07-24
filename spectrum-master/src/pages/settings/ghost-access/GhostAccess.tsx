//
// Copyright (c) 2019-Present Syncari - All rights reserved.
//
import { useState, useEffect, useMemo } from 'react';
import { Tabs } from 'antd';
import { RouteComponentProps, navigate } from '@reach/router';
import { useUserRolesForCurrentInstance } from 'store/user/hooks';
import { useEnhancedSelector as useSelector } from 'hooks/redux';
import { AllPermissions } from 'utils/PermissionsConstants';

import GrantAccessForm from './GrantAccessForm';
import ActiveAccessTable from './ActiveAccessTable';
import AuditHistoryTable from './AuditHistoryTable';
import './GhostAccess.less';

const { TabPane } = Tabs;

interface GhostAccessProps extends RouteComponentProps {}

const GhostAccess = (props: GhostAccessProps) => {
  const { roles } = useUserRolesForCurrentInstance();
  const isGhostUser = useSelector((state) => state.user.isGhostUser);
  const isSuperAdmin = useSelector((state) => state.user.isSuperAdmin);

  // Set initial tab based on permissions
  const initialTab = useMemo(() => (isSuperAdmin ? 'grant' : 'active'), [isSuperAdmin]);
  const [activeTab, setActiveTab] = useState(initialTab);

  // Permission check: Only superadmins and ghost users can access
  useEffect(() => {
    if (!(roles.superAdmin || isGhostUser)) {
      navigate('/settings');
    }
  }, [roles.superAdmin, isGhostUser]);

  return (
    <div className="ghost-access-page">
      <div className="ghost-access-header">
        <h1>Ghost Access Management</h1>
        <p>Manage ghost access to customer instances</p>
      </div>

      <Tabs activeKey={activeTab} onChange={setActiveTab} type="card" className="ghost-access-tabs">
        {isSuperAdmin && (
          <TabPane tab="Grant Access" key="grant">
            <GrantAccessForm />
          </TabPane>
        )}

        <TabPane tab="Active Access" key="active">
          <ActiveAccessTable />
        </TabPane>

        <TabPane tab="Audit History" key="history">
          <AuditHistoryTable />
        </TabPane>
      </Tabs>
    </div>
  );
};

export default GhostAccess;
