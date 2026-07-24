import { Redirect, RouteComponentProps, Router, useNavigate } from '@reach/router';
import { useMemo } from 'react';

import { useI18nContext } from 'components/I18nProvider';
import { Dropdown, Toolbar } from 'components/toolbar';
import RouteConstants from 'utils/RouteConstants';
import { makeUrl } from 'utils/UrlUtil';

import RecordFields, { fieldsPageOption } from './Fields';
import RecordLineage, { lineagePageOption } from './Lineage';
import './RecordDetail.less';
interface DataStudioRecordDetailProps extends RouteComponentProps {
  entityId: string;
}

const DataStudioRecordDetail = ({ entityId, location }: DataStudioRecordDetailProps) => {
  const { tn } = useI18nContext();
  const navigate = useNavigate();

  const recordPageOptions = useMemo(
    () =>
      [fieldsPageOption, lineagePageOption].map((opt) => ({
        ...opt,
        name: tn(`Pages.${opt.id}`),
      })),
    [tn]
  );

  const currentPage = recordPageOptions.find((opt) => location?.pathname.endsWith(opt.id));

  return (
    <div className="data-studio-record-detail">
      <Toolbar
        backToName={tn('records_list')}
        onRequestBack={() => navigate(makeUrl(RouteConstants.DATA_STUDIO_ENTITY, { entityId }))}
        leftChildren={
          <Dropdown
            options={recordPageOptions}
            selected={currentPage}
            onChange={({ id }) => {
              navigate?.(`./${id}`);
            }}
          />
        }
      />
      <Router className="data-studio-record-detail-content">
        <RecordFields path="fields" />
        <RecordLineage path="lineage" entityId={entityId} />
        <Redirect from="/*" to="fields" noThrow />
      </Router>
    </div>
  );
};

export default DataStudioRecordDetail;
