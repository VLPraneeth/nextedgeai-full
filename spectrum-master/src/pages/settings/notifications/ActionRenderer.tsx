import { navigate } from '@reach/router';
import { Icon } from 'antd';

import { ReactComponent as EditIcon } from 'assets/icons/edit-pencil.svg';
import { ReactComponent as TrashIcon } from 'assets/icons/Trash.svg';
import { ErrorNotificationConfig } from 'store/error-notifications-v2/types';
import { tc } from 'utils/i18nUtil';
import RouteConstants from 'utils/RouteConstants';
import { makeUrl } from 'utils/UrlUtil';

interface ActionRendererProps {
  data: ErrorNotificationConfig | undefined;
  handleDelete: () => void;
}

export function ActionRenderer({ data, handleDelete }: ActionRendererProps) {
  return (
    <div>
      <Icon
        component={(props) => (
          <EditIcon
            onClick={() => {
              navigate(makeUrl(RouteConstants.SETTINGS_NOTIFICATIONS_TYPE_EDIT, { id: data?.id, type: data?.type }));
            }}
            {...props}
          />
        )}
        className="error-notifications__list__edit-icon"
        aria-label={tc('edit')}
        role="button"
      />
      <Icon
        className="error-notifications__list__delete-icon"
        component={(props) => <TrashIcon {...props} onClick={handleDelete} />}
        aria-label={tc('delete')}
        role="button"
      />
    </div>
  );
}
