//
// Copyright (c) 2019-Present Syncari - All rights reserved.
//
import { useState, useEffect } from 'react';
import { Tabs } from 'antd';
import { RouteComponentProps, navigate } from '@reach/router';
import { useUserRolesForCurrentInstance } from 'store/user/hooks';
import { useEnhancedSelector as useSelector } from 'hooks/redux';
import { selectUserGhosted } from 'store/user/selectors';
import ReadQueries from './ReadQueries';
import UpdateQueries from './UpdateQueries';
import Approvals from './Approvals';
import AuditLogs from './AuditLogs';
import './DataFixQueries.less';

const { TabPane } = Tabs;

interface DataFixQueriesProps extends RouteComponentProps {}

const DataFixQueries = (props: DataFixQueriesProps) => {
  const [activeTab, setActiveTab] = useState('read');
  const { roles } = useUserRolesForCurrentInstance();
  const ghosted = useSelector(selectUserGhosted);
  const isGhostUser = useSelector((state) => state.user.isGhostUser);

  useEffect(() => {
    // Only super admins, ghost users, and actively ghosting users can access Data Fix
    if (!(roles.superAdmin || ghosted || isGhostUser)) {
      navigate('/settings');
    }
  }, [roles.superAdmin, ghosted, isGhostUser]);

  return (
    <div className="data-fix-queries">
      <div className="data-fix-header">
        <h1>Data Fix Support Tool</h1>
        <p>Execute read-only queries and manage data modification requests with approval workflow</p>
      </div>

      <Tabs activeKey={activeTab} onChange={setActiveTab} type="card" className="data-fix-tabs">
        <TabPane tab="Read Queries" key="read">
          <ReadQueries />
        </TabPane>
        <TabPane tab="Update Queries" key="update">
          <UpdateQueries />
        </TabPane>
        <TabPane tab="Approvals" key="approvals">
          <Approvals />
        </TabPane>
        <TabPane tab="Audit Logs" key="audit">
          <AuditLogs />
        </TabPane>
      </Tabs>
    </div>
  );
};

export default DataFixQueries;
