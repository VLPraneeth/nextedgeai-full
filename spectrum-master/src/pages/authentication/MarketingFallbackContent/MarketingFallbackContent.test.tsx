//
// Copyright (c) 2019-Present Syncari - All rights reserved.
//

import { render, screen } from '@testing-library/react';

import { tNamespaced } from 'utils/i18nUtil';

import { MarketingFallbackContent } from './MarketingFallbackContent';

const tn = tNamespaced('MarketingFallbackContent');

describe('MarketingFallbackContent', () => {
  it('renders marketing header', async () => {
    render(<MarketingFallbackContent />);

    const marketingBlurb = await screen.findByText(tn('header'));
    expect(marketingBlurb).toBeInTheDocument();
  });
});
