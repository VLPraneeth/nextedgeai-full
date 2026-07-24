//
// Copyright (c) 2019-Present Syncari - All rights reserved.
//
import { useState, useEffect } from 'react';
import { Table, Button, message, Tag, DatePicker, Input, Icon } from 'antd';
import { get, post } from 'utils/AjaxUtil';
import moment, { Moment } from 'moment';
import './AuditLogs.less';

const { RangePicker } = DatePicker;

interface AuditLog {
  id: string;
  timestamp: string;
  userId: string;
  userEmail: string;
  actionType: string;
  queryId?: string;
  queryText?: string;
  targetDatabase?: string;
  targetCollection?: string;
  affectedRows?: number;
  status: string;
  failureReason?: string;
  justification?: string;
  instanceId?: string;
}

const AuditLogs = () => {
  const [logs, setLogs] = useState<AuditLog[]>([]);
  const [loading, setLoading] = useState(false);
  const [pagination, setPagination] = useState({
    current: 1,
    pageSize: 50,
    total: 0,
  });
  const [dateRange, setDateRange] = useState<[Moment, Moment] | undefined>(undefined);
  const [searchText, setSearchText] = useState('');

  useEffect(() => {
    fetchAuditLogs();
  }, [pagination.current, pagination.pageSize]);

  const fetchAuditLogs = async () => {
    setLoading(true);
    try {
      let url = '/arcade/api/v1/data-fix/audit-logs';
      const params: any = {
        page: pagination.current - 1,
        size: pagination.pageSize,
        sortBy: 'timestamp',
        sortDirection: 'DESC',
      };

      if (dateRange && dateRange[0] && dateRange[1]) {
        url = '/arcade/api/v1/data-fix/audit-logs/date-range';
        params.startDate = dateRange[0].valueOf();
        params.endDate = dateRange[1].valueOf();
      }

      const response = await get(url, { params });

      if (response.data.content) {
        // Paginated response
        setLogs(Array.isArray(response.data.content) ? response.data.content : []);
        setPagination((prev) => ({
          ...prev,
          total: response.data.totalElements || 0,
        }));
      } else {
        // Array response
        const logsData = response.data || [];
        setLogs(Array.isArray(logsData) ? logsData : []);
        setPagination((prev) => ({
          ...prev,
          total: Array.isArray(logsData) ? logsData.length : 0,
        }));
      }
    } catch (err) {
      console.error('Failed to fetch audit logs', err);
      message.error('Failed to load audit logs');
      setLogs([]);
    } finally {
      setLoading(false);
    }
  };

  const handleTableChange = (newPagination: any) => {
    setPagination({
      current: newPagination.current,
      pageSize: newPagination.pageSize,
      total: pagination.total,
    });
  };

  const handleDateRangeChange = (dates: any) => {
    setDateRange(dates);
  };

  const handleSearch = () => {
    fetchAuditLogs();
  };

  const getActionTypeColor = (actionType: string): string => {
    const colors: { [key: string]: string } = {
      QUERY_SUBMITTED: 'blue',
      QUERY_APPROVED: 'green',
      QUERY_REJECTED: 'red',
      QUERY_EXECUTED: 'purple',
      USER_LOGIN: 'cyan',
      DRY_RUN_EXECUTED: 'orange',
    };
    return colors[actionType] || 'default';
  };

  const getStatusColor = (status: string): string => {
    return status === 'SUCCESS' ? 'green' : 'red';
  };

  const filteredLogs = Array.isArray(logs)
    ? logs.filter((log) => {
        if (!searchText) return true;
        const search = searchText.toLowerCase();
        return (
          log.userEmail.toLowerCase().includes(search) ||
          log.actionType.toLowerCase().includes(search) ||
          log.queryText?.toLowerCase().includes(search) ||
          log.targetCollection?.toLowerCase().includes(search)
        );
      })
    : [];

  const columns = [
    {
      title: 'Timestamp',
      dataIndex: 'timestamp',
      key: 'timestamp',
      width: 180,
      fixed: 'left' as const,
      render: (date: string) => moment(date).format('YYYY-MM-DD HH:mm:ss'),
      sorter: (a: AuditLog, b: AuditLog) => moment(a.timestamp).unix() - moment(b.timestamp).unix(),
    },
    {
      title: 'User',
      dataIndex: 'userEmail',
      key: 'userEmail',
      width: 200,
      ellipsis: true,
    },
    {
      title: 'Action',
      dataIndex: 'actionType',
      key: 'actionType',
      width: 180,
      render: (actionType: string) => <Tag color={getActionTypeColor(actionType)}>{actionType.replace(/_/g, ' ')}</Tag>,
    },
    {
      title: 'Collection',
      dataIndex: 'targetCollection',
      key: 'targetCollection',
      width: 150,
      ellipsis: true,
    },
    {
      title: 'Query',
      dataIndex: 'queryText',
      key: 'queryText',
      width: 300,
      ellipsis: true,
      render: (text: string) => (text ? <code>{text}</code> : '-'),
    },
    {
      title: 'Affected Rows',
      dataIndex: 'affectedRows',
      key: 'affectedRows',
      width: 130,
      align: 'right' as const,
      render: (count: number) => (count !== undefined && count !== null ? count : '-'),
    },
    {
      title: 'Status',
      dataIndex: 'status',
      key: 'status',
      width: 100,
      render: (status: string) => <Tag color={getStatusColor(status)}>{status}</Tag>,
    },
    {
      title: 'Failure Reason',
      dataIndex: 'failureReason',
      key: 'failureReason',
      width: 200,
      ellipsis: true,
      render: (reason: string) => reason || '-',
    },
    {
      title: 'Justification',
      dataIndex: 'justification',
      key: 'justification',
      width: 200,
      ellipsis: true,
      render: (justification: string) => {
        if (!justification) return '-';
        if (justification.startsWith('http')) {
          return (
            <a href={justification} target="_blank" rel="noopener noreferrer">
              Link
            </a>
          );
        }
        return justification;
      },
    },
  ];

  return (
    <div className="audit-logs">
      <div className="audit-logs-header">
        <h2>Audit Logs</h2>
        <div className="audit-logs-filters">
          <RangePicker
            value={dateRange}
            onChange={handleDateRangeChange}
            showTime
            format="YYYY-MM-DD HH:mm"
            className="date-range-picker"
          />
          <Input
            placeholder="Search by user, action, query, collection..."
            value={searchText}
            onChange={(e) => setSearchText(e.target.value)}
            prefix={<Icon type="search" />}
            style={{ width: 300 }}
          />
          <Button type="primary" icon="reload" onClick={handleSearch} loading={loading}>
            Search
          </Button>
        </div>
      </div>

      <Table
        columns={columns}
        dataSource={filteredLogs}
        loading={loading}
        rowKey="id"
        scroll={{ x: 'max-content' }}
        pagination={{
          ...pagination,
          showSizeChanger: true,
          showTotal: (total) => `Total ${total} audit logs`,
        }}
        onChange={handleTableChange}
        size="small"
      />
    </div>
  );
};

export default AuditLogs;
