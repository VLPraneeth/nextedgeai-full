import I18nProvider from 'components/I18nProvider';
import { render, screen } from 'tests/helpers';

import NumberText from '../NumberText';
import Text, { TranslatedText } from '../Text';

describe('Text', () => {
  test('Renders text normally', async () => {
    const { findByText } = render(<Text>hello</Text>);

    expect(await findByText('hello')).toBeInTheDocument();
  });

  test('Renders dangerous inner html', async () => {
    const { findByTestId } = render(<Text beDangerous children={'<b data-testid="hello-test">hello</b>'} />);

    expect(await findByTestId('hello-test')).toBeInTheDocument();
  });
});

test('TranslatedText from context and namespace overrides', async () => {
  render(
    <I18nProvider namespace="DataStudio">
      <TranslatedText text="window_title" />
      <TranslatedText namespace="Common" text="apply" />
      <TranslatedText namespace="SchemaStudio" text="title" />
    </I18nProvider>
  );

  expect(await screen.findByText('Data Studio')).toBeInTheDocument();
  expect(await screen.findByText('Apply')).toBeInTheDocument();
  expect(await screen.findByText('Schema Studio')).toBeInTheDocument();
});

test('TranslatedText with args', async () => {
  render(
    <I18nProvider namespace="TableFilters">
      <TranslatedText text="filters" args={{ count: 1 }} />
      <TranslatedText text="filters" args={{ count: 5 }} />
    </I18nProvider>
  );

  expect(await screen.findByText('Filter (1 active)')).toBeInTheDocument();
  expect(await screen.findByText('Filters (5 active)')).toBeInTheDocument();
});

describe('NumberText', () => {
  it('renders number correctly', async () => {
    render(<NumberText>10000</NumberText>);
    expect(await screen.findByText('10,000')).toBeInTheDocument();
  });
});
