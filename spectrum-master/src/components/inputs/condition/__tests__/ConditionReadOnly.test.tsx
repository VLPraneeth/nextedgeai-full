//
// Copyright (c) 2019-Present Syncari - All rights reserved.
//
import { render, screen } from '@testing-library/react';

import { init } from 'utils/i18nUtil';

import ConditionReadOnly from '../ConditionReadOnly';

describe('Condition readonly', () => {
  beforeAll(() => init());

  test('should render values', async () => {
    render(<ConditionReadOnly left="leftVal" operator="eq" right="rightVal" />);
    expect(await screen.findByText('leftVal')).toBeInTheDocument();
    expect(await screen.findByText('equals (case-sensitive)')).toBeInTheDocument();
    expect(await screen.findByText('rightVal')).toBeInTheDocument();
  });
});
