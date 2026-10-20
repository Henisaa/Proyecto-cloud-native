module.exports = function (config) {
  config.set({
    frameworks: ['jasmine', '@angular-devkit/build-angular'],
    plugins: [require('karma-jasmine'), require('karma-chrome-launcher'), require('karma-coverage'), require('@angular-devkit/build-angular/plugins/karma')],
    reporters: ['progress'],
    customLaunchers: { ChromeHeadlessDocker: { base: 'ChromeHeadless', flags: ['--no-sandbox', '--disable-dev-shm-usage'] } },
    coverageReporter: { dir: require('path').join(__dirname, 'coverage'), reporters: [{ type: 'html' }, { type: 'lcovonly' }, { type: 'text-summary' }] },
    singleRun: true,
  });
};
