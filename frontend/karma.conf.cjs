module.exports = function (config) {
  function StrictReporter(baseReporterDecorator) {
    baseReporterDecorator(this);
    this.onRunComplete = function (browsers, results) {
      let skipped = 0;
      browsers.forEach(browser => { skipped += browser.lastResult.skipped || 0; });
      const summary = { tests: results.success + results.failed, failed: results.failed, skipped };
      require('fs').mkdirSync('coverage', { recursive: true });
      require('fs').writeFileSync('coverage/frontend-results.json', JSON.stringify(summary));
      if (skipped || results.failed || results.error || summary.tests < 14) {
        results.exitCode = 1;
        throw new Error('Strict frontend gate: missing, failing or skipped tests');
      }
    };
  }
  StrictReporter.$inject = ['baseReporterDecorator'];
  config.set({
    frameworks: ['jasmine', '@angular-devkit/build-angular'],
    plugins: [require('karma-jasmine'), require('karma-chrome-launcher'), require('karma-coverage'), require('@angular-devkit/build-angular/plugins/karma'), { 'reporter:strict': ['type', StrictReporter] }],
    reporters: ['progress', 'strict'],
    customLaunchers: { ChromeHeadlessDocker: { base: 'ChromeHeadless', flags: ['--no-sandbox', '--disable-dev-shm-usage'] } },
    coverageReporter: { dir: require('path').join(__dirname, 'coverage'), reporters: [{ type: 'html' }, { type: 'lcovonly' }, { type: 'json-summary', subdir: '.', file: 'coverage-summary.json' }, { type: 'text-summary' }] },
    singleRun: true,
  });
};
