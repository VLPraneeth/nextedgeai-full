import { Col, Input, Row } from 'antd';
import { Fragment } from 'react';

import { Spacer } from 'components/layout';
import { DataStoreConfig } from 'store/datastore/types';
import { tc, tNamespaced } from 'utils/i18nUtil';

import DataStoreActions from './DataStoreActions';

const tn = tNamespaced('Settings.DataStore');

export interface SyncariDataStoreConfigProps {
  syncariDataStore: any;
  activeDataStore?: DataStoreConfig;
}

const SyncariDataStoreConfig = ({ syncariDataStore, activeDataStore }: SyncariDataStoreConfigProps) => {
  return (
    <Fragment>
      <Row>
        <Col>
          <Row>
            <Col>
              <span className="synri-label">{tc('name')}</span>
            </Col>
          </Row>
          <Row>
            <Col>
              <Input name="syncariId" disabled value={syncariDataStore.name} />
            </Col>
          </Row>
          <Row>
            <Col>
              <span className="synri-label">{tn('endpoint')}</span>
            </Col>
          </Row>
          <Row>
            <Col>
              <Input name="endpoint" disabled value={syncariDataStore.endpoint} />
            </Col>
          </Row>
          <Row>
            <Col>
              <span className="synri-label">{tn('user')}</span>
            </Col>
          </Row>
          <Row>
            <Col>
              <Input name="uname" disabled value={syncariDataStore.authConfig.userName} />
            </Col>
          </Row>
          <Row>
            <Col>
              <span className="synri-label">{tc('password')}</span>
            </Col>
          </Row>
          <Row>
            <Col>
              <Input.Password disabled name="password" value={syncariDataStore.authConfig.password} />
            </Col>
          </Row>
          <Row>
            <Col>
              <span className="synri-label">{tn('database')}</span>
            </Col>
          </Row>
          <Row>
            <Col>
              <Input disabled name="dbname" value={syncariDataStore.metaConfig.dbName} />
            </Col>
          </Row>
          <Row>
            <Col>
              <span className="synri-label">{tn('schema')}</span>
            </Col>
          </Row>
          <Row>
            <Col>
              <Input disabled name="dbname" value={syncariDataStore.metaConfig.schemaName} />
            </Col>
          </Row>
          <Row>
            <Col>
              <span className="synri-label">{tn('port')}</span>
            </Col>
          </Row>
          <Row>
            <Col>
              <Input disabled name="port" value={syncariDataStore.metaConfig.port} />
            </Col>
          </Row>
        </Col>
      </Row>
      <Spacer y="lg" />
      <DataStoreActions dataStoreConfig={syncariDataStore} activeDataStore={activeDataStore} isSyncariDataStore />
    </Fragment>
  );
};

export default SyncariDataStoreConfig;
