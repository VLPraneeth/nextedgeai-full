# Form & Field Validators

Declarative React components for re-usable validation logic & behavior.

#### Field validation features:

- Built-in validation tests for:
  - Required values
  - No special characters
  - No spaces
- Delayed validation
  - Validation waits until user touches and leaves a field to prevent showing errors before user finishes typing. After first validation, it then triggers on every change.
- Single-error processing
  - Regardless of how many errors _could_ be on a form field, only one error is shown at a time
- Extensible event handlers
  - Pass custom `onBlur` and `onChange` functions to hook into those events without disrupting validation logic
- Decoupled and re-usable
  - FieldValidator provides values through a `render` prop, allowing any wrapped component to adapt the output to it's own interface

#### Form validation features

- Auto-track form validity
  - Form validator automatically registers and tracks the validity of any child FieldValidators and will prevent form submission if any fields are invalid
- Validate on submit
  - Submitting a form will trigger every field to validate - even ones untouched by the user. If any are invalid, submission is prevented and invalid states are exposed by the child fields for display to the user.

NOTE: FieldValidators must be direct children of the FormValidator in order to be registered as part of form validation.

### Import

Components can be imported as a group and used with dot notation (recommended):

```js
import Validator from 'components/validator';

<Validator.Form>
  <Validator.Field>
</Validator.Form>
```

Or imported separately from respective files:

```js
import { FieldValidator } from 'components/Validator/FieldValidator';
import { FormValidator } from 'components/Validator/FormValidator';

<FormValidator>
  <FieldValidator>
</FormValidator>
```

### Basic usage

```jsx
// Uncontrolled Component
<Validator.Field
  name="displayName"
  validationOptions={{ required: true }}
  render={({ onChange, onBlur, isValid, errorMessage }) => (
    <>
      <input onChange={onChange} onBlur={onBlur} className={isValid ? 'error' : ''} />
      {errorMessage && <p>{errorMessage}</p>}
    </>
  )}
/>

// Controlled Component
<Validator.Field
  name="displayName"
  validationOptions={{ required: true }}
  value={value}
  render={({ value, onChange, onBlur, isValid, errorMessage }) => (
    <>
      <input value={value} onChange={onChange} onBlur={onBlur} className={isValid ? 'error' : ''} />
      {errorMessage && <p>{errorMessage}</p>}
    </>
  )}
/>
```

## Adding new validations

To extend the built-in validation options for fields:

1. Add a new validation function to `validationFunctions.ts`
2. Add a new property to `ValidationOptions` type
3. Add a condition in `validate` method of FieldValidator to trigger the new validation.
