//
// Copyright (c) 2019-Present Syncari - All rights reserved.
//
import { fireEvent, render } from '@testing-library/react';
import { message } from 'antd';
import React from 'react';

import { CopyToClipboard } from 'components/copy-to-clipboard/CopyToClipboard';
import { sleep } from 'tests/helpers';
import { tNamespaced } from 'utils/i18nUtil';

const t = tNamespaced('CopyToClipboard');
const testText = 'Test Text';
const testLabel = 'Text Label';

if (!navigator.clipboard) {
  // @ts-expect-error - suppress error 'Cannot assign to 'clipboard' because it is a read-only property.'
  navigator.clipboard = { writeText: jest.fn() };
}

message.success = jest.fn();
message.error = jest.fn();

describe('CopyToClipboard', () => {
  afterEach(() => jest.resetAllMocks());

  test('Uses navigator browser API to copy provided text', async () => {
    // @ts-expect-error - writeText not recognized as mock function
    navigator.clipboard.writeText.mockImplementation(async () => new Promise((res) => res()));
    const { getByRole } = render(<CopyToClipboard textToCopy={testText} />);

    fireEvent.click(getByRole('button'));

    await sleep(2);

    expect(navigator.clipboard.writeText).toHaveBeenCalledWith(testText);
    expect(message.success).toHaveBeenCalledWith(t('confirmation', { text: 'text' }));
  });

  test('Includes textLabel in notification message when provided', async () => {
    const { getByRole } = render(<CopyToClipboard textToCopy={testText} textLabel={testLabel} />);
    fireEvent.click(getByRole('button'));
    await sleep(2);

    expect(message.success).toHaveBeenCalledWith(t('confirmation', { text: testLabel }));
  });

  test('Provides error message on failure', async () => {
    // @ts-expect-error - writeText not recognized as mock function
    navigator.clipboard.writeText.mockImplementation(async () => new Promise((res, rej) => rej()));
    const { getByRole } = render(<CopyToClipboard textToCopy={testText} textLabel={testLabel} />);
    fireEvent.click(getByRole('button'));
    await sleep(2);

    expect(message.error).toHaveBeenCalledWith(t('error'));
  });
});
