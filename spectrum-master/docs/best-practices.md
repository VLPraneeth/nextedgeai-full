# Best Practices

## Naming Conventions

when declaring function components - use `const` instead of `function`

### Synapse == Connector

Synapse: used in the presentation layer of the app
Connector: used in code layer to refer to Synapses

## CSS

### classnames

- when using the classnames package, always use `cx` when importing.
  - ex: `import cx from 'classnames'`

## HTML

### Localization

- when using text visible to a user, wrap the text in a i18next translator
  - the `useI18nContext` hook is the preferred method to do this.
  - example import: `import { useI18nContext } from 'components/I18nProvider'`
  - preferred usage:
    - `"TestComponent: "{'squirtle: 'squirtle is the best starter pokemon'}` in i18n .json file
    - component:
    - `const {tn} = useI18nContext()`
    - `<p>{tn("squirtle")}<p>`
    - `export withi18n(TextComponent, 'TextComponent')`

## Folder Structure

### ./pages

- 1st level will match the ui element associated on the screen
  - this will allow us to change label names and such in the app while keeping it consistent in code.
  - at this level the naming will always be consistent with the pages on the screen. After this level however, they may be a different name used to describe something.
  - this serves as a source of truth to easily find connections between code and ui
