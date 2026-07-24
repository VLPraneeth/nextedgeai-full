/* eslint-disable jsx-a11y/anchor-is-valid */
//
// Copyright (c) 2019-Present Syncari - All rights reserved.
//

import Dropdown from 'antd/lib/dropdown';
import Icon from 'antd/lib/icon';
import Menu, { ClickParam } from 'antd/lib/menu';
import { isObject, map } from 'lodash';
import { identity, omitBy } from 'lodash/fp';
import { useCallback, useState } from 'react';

import { useI18nContext, withI18n } from 'components/I18nProvider';
import ModalTable, { TBody, TD, TH, THead, TR } from 'components/ModalTable';
import Tabs from 'components/Tabs';
import { TransactionMergeDetails } from 'store/transactions';
import AppConstants from 'utils/AppConstants';
import { capitalize } from 'utils/Fp';
import { tc } from 'utils/i18nUtil';

import './MergeTransactionRecords.less';

const { TabPane, Tab } = Tabs;

const getDisplayValue = (value: unknown) => {
  switch (typeof value) {
    case 'boolean':
      return capitalize(value ? tc('true') : tc('false'));
    case 'number':
      return value.toString();
    case 'string':
      return value;
    default:
      return null;
  }
};

export type RecordTableProps = {
  className?: string;
  values: Record<string, unknown>;
  valueKeyFormat: (key: string) => string;
};

const RecordTable = ({ className, valueKeyFormat = identity, values }: RecordTableProps) => {
  const { tn } = useI18nContext();

  return (
    <ModalTable className={className}>
      <THead>
        <TR>
          <TH>{tn('field')}</TH>
          <TH>{tn('value')}</TH>
        </TR>
      </THead>
      <TBody>
        {map(values, (value, key) => (
          <TR key={valueKeyFormat(key)}>
            <TD>{key}</TD>
            <TD>{getDisplayValue(value)}</TD>
          </TR>
        ))}
      </TBody>
    </ModalTable>
  );
};

// helper for building key fns
const transactionKeyFactory = (prefix: string) => (key?: string) => {
  return key ? `${prefix}-${key}` : prefix;
};

// Remove any fields that don't have a value we can directly render
const omitUnrenderableValues = omitBy(isObject);

function MergeTransactionRecords({ dataSource }: { dataSource: TransactionMergeDetails }) {
  const { tn } = useI18nContext();
  const getLosingRecordTitle = useCallback((idx: number) => tn('losingRecordIndex', { index: idx + 1 }), [tn]);

  const { winningRecord, losingRecords } = dataSource;

  const winningRecordEntries = omitUnrenderableValues(winningRecord?.values);
  const onlyOnelosingRecord = losingRecords.length === 1;

  const [losingRecordIndex, setLosingRecordIndex] = useState(0);
  const losingRecordTitle = getLosingRecordTitle(losingRecordIndex);

  const handleMenuClick = (e: ClickParam) => {
    setLosingRecordIndex(parseInt(e.key));
  };

  const losingRecordsDropdown = (
    <Tab className="winning-record">
      {onlyOnelosingRecord ? (
        losingRecordTitle
      ) : (
        <Dropdown
          overlay={
            <Menu onClick={handleMenuClick}>
              {losingRecords.map((_record, index) => {
                return <Menu.Item key={index}>{getLosingRecordTitle(index)}</Menu.Item>;
              })}
            </Menu>
          }
          trigger={['click']}>
          <a className="ant-dropdown-link" href="#">
            {losingRecordTitle}&nbsp;
            <Icon type="down" />
          </a>
        </Dropdown>
      )}
    </Tab>
  );

  const losingRecord = losingRecords[losingRecordIndex];

  const getTransactionWinningRecordKey = transactionKeyFactory(`transaction-change-win`);
  const getTransactionLosingRecordKey = transactionKeyFactory(`transaction-change-lose-${losingRecord.id}`);

  return (
    <div className="merge-transactions transactions-changes-expanded-row">
      <Tabs
        defaultActiveKey={tn('winningRecord')}
        size={AppConstants.MERGE_TRANSACTION_CONST.TAB_SIZE}
        type={AppConstants.MERGE_TRANSACTION_CONST.TAB_TYPE}>
        <TabPane
          key={tn('winningRecord')}
          className="transactions-changes-tab-content"
          tab={<Tab className="winning-record">{tn('winningRecord')}</Tab>}>
          <RecordTable valueKeyFormat={getTransactionWinningRecordKey} values={winningRecordEntries} />
        </TabPane>
        <TabPane key={tn('losingRecords')} className="transactions-changes-tab-content" tab={losingRecordsDropdown}>
          {losingRecord && (
            <RecordTable
              key={getTransactionLosingRecordKey()}
              className="transactions-changes-table"
              valueKeyFormat={getTransactionLosingRecordKey}
              values={omitUnrenderableValues(losingRecord.values)}
            />
          )}
        </TabPane>
      </Tabs>
    </div>
  );
}

export default withI18n(MergeTransactionRecords, 'Transaction');
