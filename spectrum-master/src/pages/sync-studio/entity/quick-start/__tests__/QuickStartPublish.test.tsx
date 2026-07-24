//
// Copyright (c) 2019-Present Syncari All rights reserved.
//
import { RootState } from 'reducers/index';
import { render } from 'tests/helpers';
import CapConstants from 'utils/CapConstants';
import { tNamespaced } from 'utils/i18nUtil';
import { DeepPartial } from 'utils/TypeUtils';

import QuickStartPublish from '../QuickStartPublish';

const tn = tNamespaced('QuickStart');

describe('QuickStartPublish', () => {
  it.todo('should disable publish inputs if lacks permissions');

  it('should show the share to organization checkbox', async () => {
    const testState: DeepPartial<RootState> = {
      entity: {},
      fastMapper: {},
      connector: {},
      user: {
        userRoles: {
          TULFLY: [CapConstants.ADMIN],
        },
        currentInstanceNextEdgeId: 'TULFLY',
      },
    };
    const { findByText, queryByText } = render(<QuickStartPublish visible quickStartId={''} />, {
      testState,
    });

    expect(await findByText(tn('share_with_instance'))).toBeInTheDocument();
    expect(queryByText(tn('share_with_org'))).toBeInTheDocument();
  });

  it('should show the share to publish to library', async () => {
    const testState: DeepPartial<RootState> = {
      entity: {},
      fastMapper: {},
      connector: {},
      user: {
        userRoles: {
          TULFLY: [CapConstants.ADMIN, CapConstants.SUPER_ADMIN],
        },
        currentInstanceNextEdgeId: 'TULFLY',
      },
    };
    const { findByText, queryByText } = render(<QuickStartPublish visible quickStartId={''} />, {
      testState,
    });

    expect(await findByText(tn('share_with_instance'))).toBeInTheDocument();
    expect(queryByText(tn('publish_to_library_picklist'))).toBeInTheDocument();
  });

  it('should show the share to publish to library for super admin', async () => {
    const testState: DeepPartial<RootState> = {
      entity: {},
      fastMapper: {},
      connector: {},
      user: {
        userRoles: {
          TULFLY: [CapConstants.SUPER_ADMIN],
        },
        currentInstanceNextEdgeId: 'TULFLY',
      },
    };
    const { findByText, queryByText } = render(<QuickStartPublish visible quickStartId={''} />, {
      testState,
    });

    expect(await findByText(tn('share_with_instance'))).toBeInTheDocument();
    expect(queryByText(tn('publish_to_library_picklist'))).toBeInTheDocument();
  });
});
