//
// Copyright (c) 2019-Present Syncari - All rights reserved.
//
import { useState, useEffect } from 'react';
import { Table, Button, Modal, Input, message, Alert, Descriptions, Icon } from 'antd';
import { get, post } from 'utils/AjaxUtil';
import './Approvals.less';

const { TextArea } = Input;

interface Query {
  id: string;
  queryText: string;
  queryType: string;
  status: string;
  justification: string;
  requesterId: string;
  requesterEmail: string;
  submittedAt: string;
  dryRunResult?: any;
  affectedRowCount?: number;
  targetCollection: string;
  targetDatabase?: string;
}

const Approvals = () => {
  const [pendingApprovals, setPendingApprovals] = useState<Query[]>([]);
  const [loading, setLoading] = useState(false);
  const [reviewModalVisible, setReviewModalVisible] = useState(false);
  const [selectedQuery, setSelectedQuery] = useState<Query | null>(null);
  const [approvalNote, setApprovalNote] = useState('');
  const [rejectionReason, setRejectionReason] = useState('');
  const [actionLoading, setActionLoading] = useState(false);

  useEffect(() => {
    fetchPendingApprovals();
  }, []);

  const fetchPendingApprovals = async () => {
    setLoading(true);
    try {
      const response = await get('/arcade/api/v1/data-fix/queries/pending-approvals');
      const data = response.data || [];
      setPendingApprovals(Array.isArray(data) ? data : []);
    } catch (err) {
      console.error('Failed to fetch pending approvals', err);
      message.error('Failed to load pending approvals');
      setPendingApprovals([]);
    } finally {
      setLoading(false);
    }
  };

  const handleReview = (query: Query) => {
    setSelectedQuery(query);
    setReviewModalVisible(true);
    setApprovalNote('');
    setRejectionReason('');
  };

  const handleApprove = async () => {
    if (!selectedQuery) return;

    if (!approvalNote.trim()) {
      message.error('Approval note is required');
      return;
    }

    setActionLoading(true);
    try {
      await post(`/arcade/api/v1/data-fix/update-query/${selectedQuery.id}/approve`, {
        approvalNote: approvalNote.trim(),
      });

      message.success('Query approved successfully');
      setReviewModalVisible(false);
      setSelectedQuery(null);
      fetchPendingApprovals();
    } catch (err: any) {
      const errorMsg = err.response?.data?.message || err.message || 'Failed to approve query';
      message.error(errorMsg);
    } finally {
      setActionLoading(false);
    }
  };

  const handleReject = async () => {
    if (!selectedQuery) return;

    if (!rejectionReason.trim()) {
      message.error('Rejection reason is required');
      return;
    }

    setActionLoading(true);
    try {
      await post(`/arcade/api/v1/data-fix/update-query/${selectedQuery.id}/reject`, {
        rejectionReason: rejectionReason.trim(),
      });

      message.success('Query rejected');
      setReviewModalVisible(false);
      setSelectedQuery(null);
      fetchPendingApprovals();
    } catch (err: any) {
      const errorMsg = err.response?.data?.message || err.message || 'Failed to reject query';
      message.error(errorMsg);
    } finally {
      setActionLoading(false);
    }
  };

  const getDryRunColumns = () => {
    if (!selectedQuery?.dryRunResult?.data || selectedQuery.dryRunResult.data.length === 0) {
      return [];
    }

    const firstRow = selectedQuery.dryRunResult.data[0];
    return Object.keys(firstRow).map((key) => ({
      title: key,
      dataIndex: key,
      key,
      ellipsis: true,
      width: 150,
    }));
  };

  const getDryRunDataSource = () => {
    if (
      !selectedQuery?.dryRunResult?.data ||
      !Array.isArray(selectedQuery.dryRunResult.data) ||
      selectedQuery.dryRunResult.data.length === 0
    ) {
      return [];
    }

    return selectedQuery.dryRunResult.data.map((row: any, index: number) => ({
      ...row,
      key: index,
    }));
  };

  const columns = [
    {
      title: 'Requester',
      dataIndex: 'requesterEmail',
      key: 'requesterEmail',
      width: 200,
    },
    {
      title: 'Query Type',
      dataIndex: 'queryType',
      key: 'queryType',
      width: 100,
    },
    {
      title: 'Query',
      dataIndex: 'queryText',
      key: 'queryText',
      ellipsis: true,
      width: 300,
    },
    {
      title: 'Collection',
      dataIndex: 'targetCollection',
      key: 'targetCollection',
      width: 150,
    },
    {
      title: 'Justification',
      dataIndex: 'justification',
      key: 'justification',
      ellipsis: true,
      width: 200,
    },
    {
      title: 'Submitted',
      dataIndex: 'submittedAt',
      key: 'submittedAt',
      width: 180,
      render: (date: string) => new Date(date).toLocaleString(),
    },
    {
      title: 'Affected Rows',
      dataIndex: 'affectedRowCount',
      key: 'affectedRowCount',
      width: 130,
      render: (count: number) => count || '-',
    },
    {
      title: 'Actions',
      key: 'actions',
      width: 120,
      fixed: 'right' as const,
      render: (record: Query) => (
        <Button type="primary" size="small" onClick={() => handleReview(record)}>
          Review
        </Button>
      ),
    },
  ];

  return (
    <div className="approvals">
      <div className="approvals-header">
        <h2>Pending Approvals</h2>
        <Button onClick={fetchPendingApprovals} loading={loading}>
          Refresh
        </Button>
      </div>

      <Table
        columns={columns}
        dataSource={Array.isArray(pendingApprovals) ? pendingApprovals : []}
        loading={loading}
        rowKey="id"
        scroll={{ x: 'max-content' }}
        pagination={{
          pageSize: 20,
          showTotal: (total) => `Total ${total} pending approvals`,
        }}
      />

      <Modal
        title="Review Update Request"
        visible={reviewModalVisible}
        onCancel={() => {
          setReviewModalVisible(false);
          setSelectedQuery(null);
        }}
        width={900}
        footer={[
          <Button key="cancel" onClick={() => setReviewModalVisible(false)}>
            Cancel
          </Button>,
          <Button key="reject" type="danger" icon="close-circle" onClick={handleReject} loading={actionLoading}>
            Reject
          </Button>,
          <Button key="approve" type="primary" icon="check-circle" onClick={handleApprove} loading={actionLoading}>
            Approve & Mark as Ready
          </Button>,
        ]}>
        {selectedQuery && (
          <div className="review-modal-content">
            <Alert
              message="Review this data modification request carefully"
              description="Once approved, the query can be executed and will modify data in the customer database."
              type="warning"
              showIcon
              className="review-warning"
            />

            <Descriptions bordered column={1} size="small" className="query-details">
              <Descriptions.Item label="Requester">{selectedQuery.requesterEmail}</Descriptions.Item>
              <Descriptions.Item label="Query Type">{selectedQuery.queryType}</Descriptions.Item>
              <Descriptions.Item label="Target Collection">{selectedQuery.targetCollection}</Descriptions.Item>
              {selectedQuery.targetDatabase && (
                <Descriptions.Item label="Target Database">{selectedQuery.targetDatabase}</Descriptions.Item>
              )}
              <Descriptions.Item label="Submitted">
                {new Date(selectedQuery.submittedAt).toLocaleString()}
              </Descriptions.Item>
              <Descriptions.Item label="Justification">
                <a href={selectedQuery.justification} target="_blank" rel="noopener noreferrer">
                  {selectedQuery.justification}
                </a>
              </Descriptions.Item>
              <Descriptions.Item label="SQL Query">
                <pre className="sql-query">{selectedQuery.queryText}</pre>
              </Descriptions.Item>
            </Descriptions>

            {selectedQuery.dryRunResult && selectedQuery.dryRunResult.data && (
              <div className="dry-run-preview">
                <h3>Dry run preview - {selectedQuery.affectedRowCount || 0} row(s) would be affected</h3>
                {selectedQuery.dryRunResult.data.length > 0 ? (
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

            <div className="approval-actions">
              <div className="form-group">
                <label htmlFor="approval-note">
                  Approval note <span className="required">(required if approving)</span>
                </label>
                <TextArea
                  id="approval-note"
                  placeholder="Add any notes or comments for approval"
                  value={approvalNote}
                  onChange={(e) => setApprovalNote(e.target.value)}
                  rows={3}
                />
              </div>

              <div className="form-group">
                <label htmlFor="rejection-reason">
                  Rejection reason <span className="required">(required if rejecting)</span>
                </label>
                <TextArea
                  id="rejection-reason"
                  placeholder="Provide reason for rejection"
                  value={rejectionReason}
                  onChange={(e) => setRejectionReason(e.target.value)}
                  rows={3}
                />
              </div>
            </div>
          </div>
        )}
      </Modal>
    </div>
  );
};

export default Approvals;
