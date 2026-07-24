//
// Copyright (c) 2019-Present Syncari - All rights reserved.
//
import { useState, useEffect } from 'react';
import { Button, Input, Table, Alert, message, Select } from 'antd';
import { get, post } from 'utils/AjaxUtil';
import './ReadQueries.less';

const { TextArea } = Input;
const { Option } = Select;

const ReadQueries = () => {
  const [queryText, setQueryText] = useState('db.collection.find({})');
  const [loading, setLoading] = useState(false);
  const [results, setResults] = useState<any>(null);
  const [error, setError] = useState<string | null>(null);
  const [collections, setCollections] = useState<string[]>([]);
  const [loadingCollections, setLoadingCollections] = useState(false);

  useEffect(() => {
    fetchCollections();
  }, []);

  const fetchCollections = async () => {
    setLoadingCollections(true);
    try {
      const response = await get('/arcade/api/v1/data-fix/collections');
      const data = response.data || [];
      setCollections(Array.isArray(data) ? data : []);
    } catch (err) {
      console.error('Failed to fetch collections', err);
      setCollections([]);
    } finally {
      setLoadingCollections(false);
    }
  };

  const handleRunQuery = async () => {
    if (!queryText.trim()) {
      message.error('Query text is required');
      return;
    }

    setLoading(true);
    setError(null);
    setResults(null);

    try {
      const response = await post('/arcade/api/v1/data-fix/read-query', {
        queryText: queryText.trim(),
      });

      // Backend returns { results: [], rowCount: number, limited: boolean }
      // But we expect { data: [] } format
      const backendData = response.data;
      setResults({
        data: backendData.results || backendData.data || [],
        rowCount: backendData.rowCount,
        limited: backendData.limited,
      });
      message.success('Query executed successfully');
    } catch (err: any) {
      const errorMsg = err.response?.data?.message || err.message || 'Failed to execute query';
      setError(errorMsg);
      message.error(errorMsg);
    } finally {
      setLoading(false);
    }
  };

  const getColumns = () => {
    if (!results?.data || results.data.length === 0) {
      return [];
    }

    const firstRow = results.data[0];
    return Object.keys(firstRow).map((key) => ({
      title: key,
      dataIndex: key,
      key,
      ellipsis: true,
      width: 150,
    }));
  };

  const getDataSource = () => {
    if (!results?.data || !Array.isArray(results.data) || results.data.length === 0) {
      return [];
    }

    return results.data.map((row: any, index: number) => ({
      ...row,
      key: index,
    }));
  };

  return (
    <div className="read-queries">
      <div className="query-section">
        <div className="form-group">
          <label htmlFor="collection-select">All Collections</label>
          <Select
            id="collection-select"
            placeholder="View available collections (informational only)"
            disabled={loading || loadingCollections}
            loading={loadingCollections}
            style={{ width: '100%' }}
            showSearch
            filterOption={(input, option: any) => option?.props?.children?.toLowerCase().includes(input.toLowerCase())}>
            {Array.isArray(collections) &&
              collections.map((collection) => (
                <Option key={collection} value={collection}>
                  {collection}
                </Option>
              ))}
          </Select>
        </div>

        <div className="form-group">
          <label htmlFor="query-text">
            MongoDB Query <span className="required">*</span>
          </label>
          <TextArea
            id="query-text"
            placeholder="db.accounts.find({ status: 'active' })"
            value={queryText}
            onChange={(e) => setQueryText(e.target.value)}
            rows={10}
            disabled={loading}
            className="sql-editor"
          />
          <div className="query-limits">
            <small>Limits: 60 seconds, 10,000 rows max</small>
          </div>
        </div>

        <Button type="primary" icon="play-circle" onClick={handleRunQuery} loading={loading} size="large">
          Run Query
        </Button>
      </div>

      {error && (
        <Alert
          message="Query Failed"
          description={error}
          type="error"
          closable
          onClose={() => setError(null)}
          className="error-alert"
        />
      )}

      {results && (
        <div className="results-section">
          <div className="results-header">
            <h3>Query Results</h3>
            <span className="row-count">
              {results.rowCount || results.data?.length || 0} row
              {(results.rowCount || results.data?.length) !== 1 ? 's' : ''}
              {results.limited && ' (limited to 10,000)'}
            </span>
          </div>

          {results.data && results.data.length > 0 ? (
            <Table
              columns={getColumns()}
              dataSource={getDataSource()}
              scroll={{ x: 'max-content', y: 500 }}
              pagination={{
                pageSize: 50,
                showSizeChanger: true,
                showTotal: (total) => `Total ${total} rows`,
              }}
              size="small"
            />
          ) : (
            <div className="no-results">No results found</div>
          )}
        </div>
      )}
    </div>
  );
};

export default ReadQueries;
