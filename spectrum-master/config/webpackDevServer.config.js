const ignoredFiles = require('react-dev-utils/ignoredFiles');

const paths = require('./paths');

const protocol = process.env.HTTPS === 'true' ? 'https' : 'http';
const host = process.env.HOST || '0.0.0.0';

module.exports = function (proxy, allowedHost, port = 3000) {
  return {
    // Port 3000 is used to avoid conflicts with proxy/arcade
    port,
    allowedHosts: !proxy || process.env.DANGEROUSLY_DISABLE_HOST_CHECK === 'true' ? 'all' : 'none',
    // Enable gzip compression of generated files.
    compress: true,
    // Silence WebpackDevServer's own logs since they're generally not useful.
    // It will still show compile warnings and errors with this setting.
    client: {
      logging: 'error',
      overlay: {
        errors: true,
        warnings: false,
      },
      // not sure if this is needed v
      // client: allowedHost,
    },
    // By default WebpackDevServer serves physical files from current directory
    // in addition to all the virtual build products that it serves from memory.
    // This is confusing because those files won’t automatically be available in
    // production build folder unless we copy them. However, copying the whole
    // project directory is dangerous because we may expose sensitive files.
    // Instead, we establish a convention that only files in `public` directory
    // get served. Our build script will copy `public` into the `build` folder.
    // In `index.html`, you can get URL of `public` folder with %PUBLIC_URL%:
    // <link rel="shortcut icon" href="%PUBLIC_URL%/favicon.ico">
    // In JavaScript code, you can access it with `process.env.PUBLIC_URL`.
    // Note that we only recommend to use `public` folder as an escape hatch
    // for files like `favicon.ico`, `manifest.json`, and libraries that are
    // for some reason broken when imported through Webpack. If you just want to
    // use an image, put it in `src` and `import` it from JavaScript instead.

    // Enable hot reloading server. It will provide /sockjs-node/ endpoint
    // CSS hot reload. JS changes will refresh the browser.
    hot: true,
    // It is important to tell WebpackDevServer to use the same "root" path
    // as we specified in the config. In development, we always serve from /.
    devMiddleware: {
      publicPath: '/',
    },
    static: {
      staticOptions: {
        contentBase: paths.appPublic,
      },
      watch: {
        ignored: ignoredFiles(paths.appSrc),
      },
    },

    // Enable HTTPS if the HTTPS environment variable is set to 'true'
    server: protocol === 'https' ? 'https' : 'http',
    host,
    historyApiFallback: {
      // Paths with dots should still use the history fallback.
      // See https://github.com/facebook/create-react-app/issues/387.
      disableDotRule: true,
    },
    proxy,
    headers: {
      'Access-Control-Allow-Origin': '*',
      'Access-Control-Allow-Headers': '*',
    },
  };
};
