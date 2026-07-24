import TokenizableFieldGroup from 'components/inputs/TokenizableFieldGroup';
import configureAppStore from 'store/configureStore';
import { getCurrentGraphFixture } from 'store/pipeline/fixtures';
import { testEntityPipelineState, testTokens } from 'store/tokens/__testdata';
import { userEvent, makeElementNotFoundError, render, screen, sleep } from 'tests/helpers';
import { t } from 'utils/i18nUtil';

import TokenTextArea from '../TokenTextArea';

test('renders token input', async () => {
  render(
    <TokenizableFieldGroup label="Tokenized Input" id="token-input">
      <TokenTextArea value="" />
    </TokenizableFieldGroup>,
    {
      store: configureAppStore({
        entityPipeline: testEntityPipelineState,
        pipeline: {
          // @ts-ignore
          currentGraph: getCurrentGraphFixture(),
        },
      }),
    }
  );

  expect(await screen.findByLabelText('Tokenized Input')).toBeInTheDocument();
  expect(await screen.findByTestId('token-input')).toBeInTheDocument();

  // no tokens, so we shouldn't see the dropdown
  const insertTokenText = t('Tokens.insert_token_trigger_label');
  expect(() => screen.getByText(insertTokenText)).toThrow(makeElementNotFoundError(insertTokenText));
});

// TODO: Uncomment this test once @testing-library/user-event is updated to v14
// which supports passing into a contenteditable element

// test.only('removes white space when pasting', async () => {
//   const { findByText } = render(
//     <TokenizableFieldGroup label="Tokenized Input" id="token-input">
//       <TokenTextArea value="starting value" />
//     </TokenizableFieldGroup>,
//     {
//       store: configureAppStore({
//         entityPipeline: testEntityPipelineState,
//         pipeline: {
//           // @ts-ignore
//           currentGraph: getCurrentGraphFixture(),
//         },
//       }),
//     }
//   );

//   const textArea = (await findByText('starting value')).closest('div');

//   console.log('userEvent', userEvent);
//   const user = userEvent.setup();
//   user.pointer({ target: textArea!, offset: 2, keys: '[MouseLeft]' });
//   logDOM();

//   user.paste(' with white space ');

//   // expect(textArea).toHaveValue('with white space');
// });

test('renders tokenized value from existing string', async () => {
  render(
    <TokenizableFieldGroup label="Default Value" id="token-input">
      <TokenTextArea value={testEntityPipelineState.selectedGraphNode.metadata.configuration.defaultValue} />
    </TokenizableFieldGroup>,
    {
      store: configureAppStore({
        entityPipeline: testEntityPipelineState,
        pipeline: {
          // @ts-ignore
          currentGraph: getCurrentGraphFixture(),
        },
      }),
    }
  );

  // find token dropdown
  const insertTokenText = t('Tokens.insert_token_trigger_label');
  expect(await screen.findByText(insertTokenText)).toBeInTheDocument();

  // find tokens
  expect(await screen.findByText('Last Modified')).toBeInTheDocument();
  expect(await screen.findByText('Name')).toBeInTheDocument();
  expect(await screen.findByText('SyncariRecordId')).toBeInTheDocument();
});

test('renders tokenized value from existing string including non token value', async () => {
  render(
    <TokenizableFieldGroup label="Tokenized Input" id="token-input">
      <TokenTextArea value={`${testEntityPipelineState.selectedGraphNode.metadata.configuration.defaultValue}-asdf`} />
    </TokenizableFieldGroup>,
    {
      store: configureAppStore({
        entityPipeline: testEntityPipelineState,
        pipeline: {
          // @ts-ignore
          currentGraph: getCurrentGraphFixture(),
        },
      }),
    }
  );

  // find token dropdown
  const insertTokenText = t('Tokens.insert_token_trigger_label');
  expect(await screen.findByText(insertTokenText)).toBeInTheDocument();

  // find tokens
  expect(await screen.findByText('Last Modified')).toBeInTheDocument();
  expect(await screen.findByText('Name')).toBeInTheDocument();
  expect(await screen.findByText('SyncariRecordId')).toBeInTheDocument();
  expect(await screen.findByText('-asdf')).toBeInTheDocument();
});

test('adding tokens', async () => {
  const mockOnChange = jest.fn();

  render(
    <TokenizableFieldGroup label="Tokenized Input" id="token-input">
      <TokenTextArea value="" onChange={mockOnChange} />
    </TokenizableFieldGroup>,
    {
      store: configureAppStore({
        entityPipeline: testEntityPipelineState,
        pipeline: {
          // @ts-ignore
          currentGraph: getCurrentGraphFixture(),
        },
      }),
    }
  );

  // find token dropdown
  const insertTokenText = t('Tokens.insert_token_trigger_label');
  const tokenDropdown = await screen.findByText(insertTokenText);
  await userEvent.click(tokenDropdown);

  // find tokens
  const nodeId = testEntityPipelineState.selectedGraphNode.id;

  const tokensForNode = testTokens[nodeId];
  const [category, tokens] = Object.entries(tokensForNode)[0];

  const firstToken = tokens[0];

  // select the right category
  await userEvent.click(await screen.findByText(category));
  await userEvent.click(await screen.findByText(firstToken.shortLabel));

  // wait for our debounced fn to fire
  await sleep(100);

  expect(mockOnChange).toHaveBeenLastCalledWith({
    target: {
      id: 'token-input',
      name: 'token-input',
      value: firstToken.token,
    },
  });
});
