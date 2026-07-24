## Setting up and running spectrum

## Run the proxy server

See <a href="proxy/README.md">proxy/README.md</a>

## Set up spectrum

Run in the project directory:

1. `cp .env.example .env`
   This will copy the example env file to the location loaded by our scripts. Please adjust any configuration as necessary.
2. `npm install`
   This will install all required `node_modules`

### Starting the dev server

Run in the project directory:

### `npm run start`

## Running the spectrum container

### `npm run build-all && npm run run-container`

### Using the mock server

See <a href="mock-server/README.md">mock-server/README.md</a>

### Running cypress for integration testing

See <a href="cypress/README.md">cypress/README.md</a>

### Spectrum development

Highly recommend VSCode for development. Install the prettier plugin and enable `Format on Save`.

### LESS Usage in JS/TS
After updating a LESS variables file, you will need to run `npm run convert-less-to-ts` so that the updated variables will be available via `constants/style`.

### FAQ

1.) File missing or package not found error:<br />

There is a good chance that a new package/s is needed. Shutdown spectrum servers and run `npm run clean-build` or `npm install` in the `root` and `proxy` directory. Start back up the spectrum servers after the install/build is done.
