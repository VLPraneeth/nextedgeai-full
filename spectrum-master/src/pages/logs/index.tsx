//
// Copyright (c) 2019-Present Syncari - All rights reserved.
//
import { RouteComponentProps, Router, useMatch } from '@reach/router';
import Menu, { ClickParam } from 'antd/lib/menu';
import { Suspense, useEffect, useState } from 'react';

import RouteSpin from 'components/RouteSpin';
import { useForbiddenRedirect } from 'hooks/useForbiddenRedirect';
import Error404 from 'pages/errors/Error404';
import { navigateTo } from 'utils/AppUtil';
import { tNamespaced } from 'utils/i18nUtil';
import { AllPermissions } from 'utils/PermissionsConstants';
import RouteConstants from 'utils/RouteConstants';
import { replaceToken } from 'utils/UrlUtil';

import SyncErrors from './SyncErrors';
import TransactionList from './TransactionList';
import './index.less';

const tn = tNamespaced('Logs');

const Logs = ({ location }: RouteComponentProps) => {
  const logMatch = useMatch('/logs/:logName/*');

  const [selectedKey, setSelectedKey] = useState('');
  const onSideNavClick = (event: ClickParam) => {
    navigateTo(replaceToken(RouteConstants.LOGS_TYPE, { type: event.key, replace: true }));
  };

  const Error403 = useForbiddenRedirect({
    studioPermissions: AllPermissions.VIEW_TRANSACTIONS,
  });

  useEffect(() => {
    logMatch?.logName && setSelectedKey(logMatch.logName?.toLowerCase());
  }, [logMatch?.logName]);

  return (
    Error403 ?? (
      <div className="logs-container">
        <div className="logs-sidebar">
          <Menu mode="inline" selectedKeys={[selectedKey]} onClick={onSideNavClick} className="full-height">
            <Menu.Item key="transactions">{tn('transactions_log')}</Menu.Item>
            <Menu.Item key="sync-errors">{tn('sync_errors')}</Menu.Item>
          </Menu>
        </div>
        <Suspense fallback={<RouteSpin />}>
          <Router className="logs-content">
            <TransactionList path="/" />
            <TransactionList path="/transactions" />
            <SyncErrors path="/sync-errors" />
            <Error404 default />
          </Router>
        </Suspense>
      </div>
    )
  );
};

export default Logs;
