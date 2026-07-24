//
// Copyright (c) 2019-Present Syncari All rights reserved.
//
import { screen, render } from 'tests/helpers';
import { t } from 'utils/i18nUtil';

import { AddSyncariField } from '../AddSyncariField/AddSyncariField';

describe('AddSyncariField', () => {
  it('should test render', async () => {
    render(<AddSyncariField id={'test-id'} />);

    await screen.findByText(t('AddMapping.create_field'));
  });
});
