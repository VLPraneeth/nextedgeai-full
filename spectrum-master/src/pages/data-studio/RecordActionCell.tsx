import { useNavigate } from '@reach/router';
import Button from 'antd/lib/button';
import Icon from 'antd/lib/icon';
import cx from 'classnames';
import * as React from 'react';

import { ReactComponent as OpenArrowIcon } from 'assets/icons/open-arrow.svg';
import Can from 'components/Can';
import { useI18nContext } from 'components/I18nProvider';
import KebabMenu, { MenuItem } from 'components/KebabMenu';
import { HStack } from 'components/layout';
import ActionsCell from 'components/renderers/ActionsCellRenderer';
import { TranslatedText } from 'components/typography';
import { wrapIcon } from 'utils/IconUtils';
import { AllPermissions } from 'utils/PermissionsConstants';
import RouteConstants from 'utils/RouteConstants';
import { UnreachableCaseError } from 'utils/TypeUtils';
import { makeUrl } from 'utils/UrlUtil';

import { useDeleteRecordDataModal } from './hooks';

import './DataStudio.less';

enum RecordAction {
  VIEW_TRANSACTIONS = 'transactions',
  DELETE = 'delete',
}

export interface RecordActionCellProps {
  entityId: string;
  recordId: string;
  onRequestRecordDetail: (entityId: string, recordId: string) => void;
}

export const RecordActionCell = ({ entityId, recordId, onRequestRecordDetail }: RecordActionCellProps) => {
  const { tc, tn } = useI18nContext();
  const navigate = useNavigate();

  const onRequestDelete = useDeleteRecordDataModal(entityId, recordId);

  return (
    <ActionsCell>
      <HStack>
        <Can permission={AllPermissions.WRITE_DATA_STUDIO}>
          <Button
            size="small"
            type="default"
            onClick={(evt: React.SyntheticEvent) => {
              evt.preventDefault();
              evt.stopPropagation();
              onRequestRecordDetail(entityId, recordId);
            }}
            className={cx('show-entity-fields-btn', 'actions-btn-xxs')}>
            <Icon component={wrapIcon(OpenArrowIcon)} />
            <TranslatedText text="fields_disclosure_btn" />
          </Button>
        </Can>
        <KebabMenu<RecordAction>
          ariaLabel={`Action menu for ${recordId}`}
          size="small"
          menuItems={[
            <MenuItem key={RecordAction.VIEW_TRANSACTIONS}>{tn('record_transactions')}</MenuItem>,
            <Can key={RecordAction.DELETE} permission={AllPermissions.WRITE_DATA_STUDIO}>
              <MenuItem>{tc('delete')}</MenuItem>
            </Can>,
          ]}
          onClick={(evt) => {
            switch (evt.key) {
              case RecordAction.DELETE:
                onRequestDelete();
                break;
              case RecordAction.VIEW_TRANSACTIONS:
                navigate(makeUrl(RouteConstants.DATA_STUDIO_RECORD_TRANSACTIONS, { entityId, recordId }));
                break;
              default:
                throw new UnreachableCaseError(evt.key);
            }
          }}
        />
      </HStack>
    </ActionsCell>
  );
};
