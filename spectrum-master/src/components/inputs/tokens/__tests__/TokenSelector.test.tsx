import configureAppStore from 'store/configureStore';
import { getCurrentGraphFixture } from 'store/pipeline/fixtures';
import { fakeNodeId, testEntityPipelineState, testTokens } from 'store/tokens/__testdata';
import { useTokensForSelectedNode } from 'store/tokens/hooks';
import { makeElementNotFoundError, render, screen, userEvent } from 'tests/helpers';
import { t } from 'utils/i18nUtil';

import TokenSelector from '../TokenSelector';

test('renders token selector without tokens w/ no tokens available message', async () => {
  render(<TokenSelector tokensLoading={false} tokens={{}} onTokenSelect={() => {}} />, {
    store: configureAppStore({
      entityPipeline: testEntityPipelineState,
      pipeline: {
        // @ts-ignore
        currentGraph: getCurrentGraphFixture(),
      },
    }),
  });

  const insertTokenText = t('Tokens.no_tokens_available');
  // no tokens, so we shouldn't see the dropdown
  expect(() => screen.getByText(insertTokenText)).toThrow(makeElementNotFoundError(insertTokenText));
});

const TokensSelectorWrapper = ({ onTokenSelect = () => {} }) => {
  const { isLoading, tokens } = useTokensForSelectedNode();

  return <TokenSelector tokens={tokens} tokensLoading={isLoading} onTokenSelect={onTokenSelect} />;
};

test('renders tokens in dropdown, shows correct tokens when switching categories', async () => {
  render(<TokensSelectorWrapper />, {
    store: configureAppStore({
      entityPipeline: testEntityPipelineState,
      pipeline: {
        // @ts-ignore
        currentGraph: getCurrentGraphFixture(),
      },
    }),
  });

  const insertTokenText = t('Tokens.insert_token_trigger_label');
  const tokenTrigger = await screen.findByText(insertTokenText);

  // find token dropdown
  expect(tokenTrigger).toBeInTheDocument();

  await userEvent.click(tokenTrigger);

  // find tokens
  const expectedTokens = Object.entries(testTokens[fakeNodeId]);

  for (let [category, tokens] of expectedTokens) {
    // switch to category
    await userEvent.click(await screen.findByText(category));

    for (let token of tokens) {
      expect(await screen.findByText(token.shortLabel)).toBeInTheDocument();
    }
  }
});

test('adding tokens', async () => {
  const mockOnSelect = jest.fn();

  render(<TokensSelectorWrapper onTokenSelect={mockOnSelect} />, {
    store: configureAppStore({
      entityPipeline: testEntityPipelineState,
      pipeline: {
        // @ts-ignore
        currentGraph: getCurrentGraphFixture(),
      },
    }),
  });

  const insertTokenText = t('Tokens.insert_token_trigger_label');
  const tokenDropdown = await screen.findByText(insertTokenText);
  await userEvent.click(tokenDropdown);

  // find tokens
  const [category, tokens] = Object.entries(testTokens[fakeNodeId])[0];

  // switch to category
  await userEvent.click(await screen.findByText(category));

  for (let token of tokens) {
    expect(await screen.findByText(token.shortLabel)).toBeInTheDocument();
  }

  await userEvent.click(await screen.findByText(tokens[0].shortLabel));
  expect(mockOnSelect).toHaveBeenLastCalledWith(tokens[0].token);
});
