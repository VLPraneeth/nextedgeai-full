//
// Copyright (c) 2019-Present Syncari - All rights reserved.
//
import { useState, useEffect } from 'react';
import { Button, Input, Table, Alert, message, Select, Modal, Tabs, Icon } from 'antd';
import { get, post } from 'utils/AjaxUtil';
import { useEnhancedSelector as useSelector } from 'hooks/redux';
import { selectUserId } from 'store/user/selectors';
import './UpdateQueries.less';

const { TextArea } = Input;
const { Option } = Select;
const { TabPane } = Tabs;

interface Query {
  id: string;
  queryText: string;
  queryType: string;
  status: string;
  justification: string;
  requesterId: string;
  requesterEmail: string;
  approverId?: string;
  approverEmail?: string;
  submittedAt: string;
  dryRunResult?: any;
  affectedRowCount?: number;
  errorMessage?: string;
  targetCollection?: string;
  approvalNote?: string;
  rejectionReason?: string;
}

const UpdateQueries = () => {
  const [queryText, setQueryText] = useState("db.accounts.updateMany({ _id: '123' }, { $set: { status: 'active' } })");
  const [justification, setJustification] = useState('');
  const [queryType, setQueryType] = useState<'UPDATE' | 'DELETE' | 'INSERT'>('UPDATE');
  const [approverId, setApproverId] = useState('');
  const [loading, setLoading] = useState(false);
  const [dryRunResult, setDryRunResult] = useState<any>(null);
  const [myQueries, setMyQueries] = useState<Query[]>([]);
  const [loadingQueries, setLoadingQueries] = useState(false);
  const [error, setError] = useState<string | null>(null);
  const [users, setUsers] = useState<any[]>([]);
  const [activeTab, setActiveTab] = useState('create');
  const [usersLoading, setUsersLoading] = useState(false);
  const [usersError, setUsersError] = useState<string | null>(null);
  const currentUserId = useSelector(selectUserId);

  useEffect(() => {
    if (currentUserId) {
      fetchUsers();
      fetchMyQueries();
    }
  }, [currentUserId]);

  const fetchUsers = async () => {
    setUsersLoading(true);
    setUsersError(null);
    try {
      const response = await get('/arcade/api/v1/organization/system-users');
      const userData = response.data;

      // The endpoint returns a List<UserResponse> directly
      if (Array.isArray(userData)) {
        // Filter to only show super admins or ghost users (exclude API users and current user)
        const eligibleApprovers = userData.filter(
          (user: any) => !user.isApiUser && (user.isSuperAdmin || user.isGhostUser) && user.id !== currentUserId
        );
        setUsers(eligibleApprovers);
        if (eligibleApprovers.length === 0) {
          setUsersError('No other super admins or ghost users found');
        }
      } else {
        console.error('Unexpected response format:', userData);
        setUsersError('Invalid response format from server');
        setUsers([]);
      }
    } catch (err: any) {
      console.error('Failed to fetch users', err);
      const errorMsg = err.response?.data?.message || err.message || 'Failed to fetch users';
      setUsersError(errorMsg);
      setUsers([]);
    } finally {
      setUsersLoading(false);
    }
  };

  const fetchMyQueries = async () => {
    setLoadingQueries(true);
    try {
      const response = await get('/arcade/api/v1/data-fix/queries/my-requests');
      const data = response.data || [];

      // Sort by submittedAt in descending order (most recent first)
      const sortedData = Array.isArray(data)
        ? data.sort((a: Query, b: Query) => {
            const dateA = a.submittedAt ? new Date(a.submittedAt).getTime() : 0;
            const dateB = b.submittedAt ? new Date(b.submittedAt).getTime() : 0;
            return dateB - dateA; // Descending order
          })
        : [];

      setMyQueries(sortedData);
    } catch (err) {
      console.error('Failed to fetch queries', err);
      setMyQueries([]);
    } finally {
      setLoadingQueries(false);
    }
  };

  const validateQuery = (): boolean => {
    if (!queryText.trim()) {
      message.error('Query text is required');
      return false;
    }

    if (!justification.trim()) {
      message.error('Justification is required');
      return false;
    }

    return true;
  };

  const handleDryRun = async () => {
    if (!validateQuery()) {
      return;
    }

    setLoading(true);
    setError(null);
    setDryRunResult(null);

    try {
      // Execute dry run directly without submitting for approval
      const dryRunResponse = await post('/arcade/api/v1/data-fix/dry-run', {
        queryText: queryText.trim(),
        queryType,
      });

      setDryRunResult(dryRunResponse.data);
      message.success('Dry run completed successfully');
    } catch (err: any) {
      const errorMsg = err.response?.data?.message || err.message || 'Failed to execute dry run';
      setError(errorMsg);
      message.error(errorMsg);
    } finally {
      setLoading(false);
    }
  };

  const handleRequestApproval = async () => {
    if (!validateQuery()) {
      return;
    }

    if (!approverId) {
      message.error('Please select an approver');
      return;
    }

    setLoading(true);
    setError(null);

    try {
      await post('/arcade/api/v1/data-fix/update-query', {
        queryText: queryText.trim(),
        justification: justification.trim(),
        queryType,
        approverId,
      });

      message.success('Query submitted for approval');
      setQueryText('');
      setJustification('');
      setApproverId('');
      setDryRunResult(null);
      fetchMyQueries();
      setActiveTab('my-requests');
    } catch (err: any) {
      const errorMsg = err.response?.data?.message || err.message || 'Failed to submit query';
      setError(errorMsg);
      message.error(errorMsg);
    } finally {
      setLoading(false);
    }
  };

  const handleExecuteApproved = async (queryId: string) => {
    Modal.confirm({
      title: 'Execute Approved Query',
      content: 'Are you sure you want to execute this approved query? This action cannot be undone.',
      okText: 'Execute',
      okType: 'danger',
      onOk: async () => {
        try {
          const response = await post(`/arcade/api/v1/data-fix/update-query/${queryId}/execute`);
          message.success(`Query executed successfully. ${response.data.affectedRows} rows affected.`);
          fetchMyQueries();
        } catch (err: any) {
          const errorMsg = err.response?.data?.message || err.message || 'Failed to execute query';
          message.error(errorMsg);
        }
      },
    });
  };

  const getDryRunColumns = () => {
    if (!dryRunResult?.data || dryRunResult.data.length === 0) {
      return [];
    }

    const firstRow = dryRunResult.data[0];
    return Object.keys(firstRow).map((key) => ({
      title: key,
      dataIndex: key,
      key,
      ellipsis: true,
      width: 150,
    }));
  };

  const getDryRunDataSource = () => {
    if (!dryRunResult?.data || !Array.isArray(dryRunResult.data) || dryRunResult.data.length === 0) {
      return [];
    }

    return dryRunResult.data.map((row: any, index: number) => ({
      ...row,
      key: index,
    }));
  };

  const myQueriesColumns = [
    {
      title: 'Query',
      dataIndex: 'queryText',
      key: 'queryText',
      ellipsis: true,
      width: 350,
    },
    {
      title: 'Type',
      dataIndex: 'queryType',
      key: 'queryType',
      width: 100,
    },
    {
      title: 'Status',
      dataIndex: 'status',
      key: 'status',
      width: 150,
      render: (status: string) => {
        const colors: any = {
          PENDING_APPROVAL: 'orange',
          APPROVED: 'green',
          REJECTED: 'red',
          EXECUTED: 'blue',
          FAILED: 'red',
        };
        return <span style={{ color: colors[status] || 'black' }}>{status}</span>;
      },
    },
    {
      title: 'Approver',
      dataIndex: 'approverEmail',
      key: 'approverEmail',
      width: 200,
    },
    {
      title: 'Submitted',
      dataIndex: 'submittedAt',
      key: 'submittedAt',
      width: 180,
      render: (date: string) => (date ? new Date(date).toLocaleString() : '-'),
      sorter: (a: Query, b: Query) => {
        const dateA = a.submittedAt ? new Date(a.submittedAt).getTime() : 0;
        const dateB = b.submittedAt ? new Date(b.submittedAt).getTime() : 0;
        return dateA - dateB;
      },
      defaultSortOrder: 'descend' as const,
    },
    {
      title: 'Approval Note',
      dataIndex: 'approvalNote',
      key: 'approvalNote',
      width: 200,
      ellipsis: true,
      render: (note: string, record: Query) => {
        if (record.status === 'APPROVED' && note) {
          return <span style={{ color: 'green' }}>{note}</span>;
        }
        return '-';
      },
    },
    {
      title: 'Rejection Reason',
      dataIndex: 'rejectionReason',
      key: 'rejectionReason',
      width: 200,
      ellipsis: true,
      render: (reason: string, record: Query) => {
        if (record.status === 'REJECTED' && reason) {
          return <span style={{ color: 'red' }}>{reason}</span>;
        }
        return '-';
      },
    },
    {
      title: 'Actions',
      key: 'actions',
      width: 150,
      render: (record: Query) => {
        if (record.status === 'APPROVED') {
          return (
            <Button type="danger" size="small" onClick={() => handleExecuteApproved(record.id)}>
              Execute
            </Button>
          );
        }
        return '-';
      },
    },
  ];

  return (
    <div className="update-queries">
      <Tabs activeKey={activeTab} onChange={setActiveTab} type="card">
        <TabPane tab="Create Query" key="create">
          <div className="query-section">
            <Alert
              message="Important"
              description="All UPDATE and DELETE queries must include a filter to specify which documents to modify. Queries will be automatically validated before submission."
              type="warning"
              showIcon
              closable
              className="warning-alert"
            />

            <div className="form-group">
              <label htmlFor="query-type">
                Query Type <span className="required">*</span>
              </label>
              <Select
                id="query-type"
                value={queryType}
                onChange={setQueryType}
                disabled={loading}
                style={{ width: 200 }}>
                <Option value="UPDATE">UPDATE</Option>
                <Option value="DELETE">DELETE</Option>
                <Option value="INSERT">INSERT</Option>
              </Select>
            </div>

            <div className="form-group">
              <label htmlFor="query-text">
                MongoDB Query (UPDATE / DELETE / INSERT) <span className="required">*</span>
              </label>
              <TextArea
                id="query-text"
                placeholder={
                  queryType === 'UPDATE'
                    ? "db.accounts.updateMany({ _id: '123' }, { $set: { status: 'active' } })"
                    : queryType === 'DELETE'
                    ? "db.accounts.deleteMany({ status: 'inactive' })"
                    : "db.accounts.insertOne({ name: 'New Account', status: 'active' })"
                }
                value={queryText}
                onChange={(e) => setQueryText(e.target.value)}
                rows={8}
                disabled={loading}
                className="sql-editor"
              />
            </div>

            <div className="form-group">
              <label htmlFor="justification">
                Justification (Link to ticket / explanation) <span className="required">*</span>
              </label>
              <Input
                id="justification"
                placeholder="e.g., https://jira.company.com/ticket/ABC-123 or detailed explanation"
                value={justification}
                onChange={(e) => setJustification(e.target.value)}
                disabled={loading}
              />
            </div>

            <div className="form-group">
              <label htmlFor="approver">
                Approver <span className="required">*</span>
              </label>
              <Select
                id="approver"
                placeholder={usersLoading ? 'Loading users...' : 'Select an approver'}
                value={approverId}
                onChange={setApproverId}
                disabled={loading || usersLoading}
                loading={usersLoading}
                style={{ width: '100%' }}
                showSearch
                filterOption={(input, option: any) => {
                  const label = option?.props?.children || '';
                  return String(label).toLowerCase().includes(input.toLowerCase());
                }}>
                {Array.isArray(users) &&
                  users.map((user) => {
                    const displayName = `${user.email || user.username || user.id}${
                      user.firstName && user.lastName ? ` (${user.firstName} ${user.lastName})` : ''
                    }`;
                    return (
                      <Option key={user.id} value={user.id}>
                        {displayName}
                      </Option>
                    );
                  })}
              </Select>
              <div style={{ marginTop: '5px', fontSize: '12px' }}>
                {usersLoading && <span style={{ color: '#1890ff' }}>Loading users...</span>}
                {!usersLoading && usersError && <span style={{ color: '#f5222d' }}>Error: {usersError}</span>}
                {!usersLoading && !usersError && Array.isArray(users) && (
                  <span style={{ color: users.length > 0 ? '#52c41a' : '#faad14' }}>
                    {users.length} user{users.length !== 1 ? 's' : ''} loaded
                  </span>
                )}
              </div>
            </div>

            <div className="action-buttons">
              <Button icon="play-circle" onClick={handleDryRun} loading={loading} size="large">
                Dry Run
              </Button>
              <Button type="primary" icon="send" onClick={handleRequestApproval} loading={loading} size="large">
                Request Approval
              </Button>
            </div>

            {error && (
              <Alert
                message="Error"
                description={error}
                type="error"
                closable
                onClose={() => setError(null)}
                className="error-alert"
              />
            )}

            {dryRunResult && (
              <div className="results-section">
                <div className="results-header">
                  <h3>Dry Run Preview - Rows that would be affected</h3>
                  <span className="row-count">
                    {dryRunResult.data?.length || 0} row{dryRunResult.data?.length !== 1 ? 's' : ''} would be affected
                  </span>
                </div>

                {dryRunResult.data && dryRunResult.data.length > 0 ? (
                  <Table
                    columns={getDryRunColumns()}
                    dataSource={getDryRunDataSource()}
                    scroll={{ x: 'max-content', y: 300 }}
                    pagination={false}
                    size="small"
                  />
                ) : (
                  <div className="no-results">No rows would be affected</div>
                )}
              </div>
            )}
          </div>
        </TabPane>

        <TabPane tab="My Requests" key="my-requests">
          <div className="my-queries-section">
            <h3>My Query Requests</h3>
            <Table
              columns={myQueriesColumns}
              dataSource={Array.isArray(myQueries) ? myQueries : []}
              loading={loadingQueries}
              rowKey="id"
              scroll={{ x: 'max-content' }}
              pagination={{
                pageSize: 20,
                showTotal: (total) => `Total ${total} queries`,
              }}
            />
          </div>
        </TabPane>
      </Tabs>
    </div>
  );
};

export default UpdateQueries;
