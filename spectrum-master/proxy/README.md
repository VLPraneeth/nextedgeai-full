
## Setting up the spectrum-proxy

Run in the `proxy` directory:

### `npm install`

## Running the spectrum-proxy server

Run in `proxy` directory:

### `npm run start`

## Running the spectrum-proxy server in development

Run in `proxy` directory:

### `npm run dev`

## Running the spectrum-proxy server proxying all the request to the integration server

Run in `proxy` directory:

### `ARCADE_TARGET=http://arcade.integration.syncari.net/ npm run dev`

## Useful environment variables

See <a href="src/index.js#L4">Environment Variables</a>

## Debugging the spectrum-proxy server with chrome

Run in `proxy` directory:

### `npm run debug`

or

### `node --inspect --inspect-brk app.js`

Go to your chrome browser and open: 

### `chrome://inspect`
