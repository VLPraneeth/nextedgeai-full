// @svgr/webpack version 8.x automatically searches for SVGR configuration files
// (like .svgrrc.js, .svgrrc, svgr.config.js, etc.) starting from the project root
// directory. This is built into the SVGR loader itself - it uses a configuration
// resolution system (similar to how ESLint finds .eslintrc files or Babel finds
// .babelrc files).
// When the webpack loader processes an SVG file, it internally calls SVGR's config
// resolution logic which walks up the directory tree looking for these config files
// and automatically applies any configuration it finds.
// So by placing .svgrrc.js in your project root, the @svgr/webpack loader
// automatically discovered and applied it when processing your SVG imports.
// The viewBox attribute should now be preserved in all your SVG components

module.exports = {
  titleProp: true,
  ref: true,
  svgoConfig: {
    plugins: [
      {
        name: 'preset-default',
        params: {
          overrides: {
            removeViewBox: false,
          },
        },
      },
    ],
  },
};
