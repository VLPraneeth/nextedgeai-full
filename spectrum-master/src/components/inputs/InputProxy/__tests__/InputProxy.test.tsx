//
// Copyright (c) 2019-Present Syncari - All rights reserved.
//

import configureAppStore from 'store/configureStore';
import { getCurrentGraphFixture } from 'store/pipeline/fixtures';
import { testEntityPipelineState } from 'store/tokens/__testdata';
import { render, screen } from 'tests/helpers';
import AppConstants from 'utils/AppConstants';

import InputProxy from '../InputProxy';

describe('InputProxy', () => {
  test('should render tokens element when renderType===TOKENS', async () => {
    const tokenWithoutBraces = 'sample.token';
    const tokenValue = `{{${tokenWithoutBraces}}}`;

    render(
      <InputProxy
        renderType={AppConstants.INPUT_RENDER_TYPE.TOKENS as any}
        value={tokenValue}
        datatype="datetime"
        onChange={() => {}}
      />,
      {
        store: configureAppStore({
          entityPipeline: testEntityPipelineState,
          pipeline: {
            currentGraph: getCurrentGraphFixture(),
          },
        }),
      }
    );

    const element = await screen.findByText(tokenWithoutBraces);
    expect(element).toBeVisible();

    const tokenElement = await screen.findByTestId('SingleTokenBadge');
    expect(tokenElement).toBeVisible();

    // The raw token string should not be visible
    const tokenString = screen.queryByText(tokenValue);
    expect(tokenString).toBeNull();
  });
});
