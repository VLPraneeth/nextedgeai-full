# Skull Config

## TOC

### 1.) [What is Skull config?](#what-is-skull-config)

### 2.) [How to set up a Skull configuration?](#how-to-set-up-a-skull-configuration)

### 3.) [Defining the Skull Context](#defining-the-skull-context)

## What is Skull config?

"Skull" or "protoskull" is the name we selected for the protocol for configuring a portion of the UI (usually a form or wizard) from a configuration object. The configuration is of type `SkullConfig` and usually comes from the backend, though it can also be loaded directly in the Spectrum bundle.

## How to set up a Skull configuration?

The Skull configuration starts with a component like `src/pages/sync-studio/entity/quick-start/QuickStartContent.tsx` which provides a context. The context comes from the `useSkullConfig` hook to provide the data for the form/wizard, skull configuration, and other functions to fetch dynamic steps or execute a step.

### Defining the Skull Context

We use React.Context to pass configuration data and functions through the wizards/form. The data in this context is updated using the reducer from `skullConfigReducer`.
