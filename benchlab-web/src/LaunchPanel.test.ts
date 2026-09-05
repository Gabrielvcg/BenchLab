import React from 'react';
import { renderToStaticMarkup } from 'react-dom/server';
import { expect, it } from 'vitest';
import { LaunchPanel } from './LaunchPanel';

const props = {submitting:false, disabled:false, invocations:87, languages:3, message:'Ready', onRun:() => {}};
it('shows run action, workload cost and accessible feedback together', () => {
  const html = renderToStaticMarkup(React.createElement(LaunchPanel, props));
  expect(html).toContain('Run comparison');
  expect(html).toContain('87 estimated container invocations');
  expect(html).toContain('role="status"');
});
it('disables submission while queuing', () => {
  const html = renderToStaticMarkup(React.createElement(LaunchPanel, {...props, submitting:true}));
  expect(html).toContain('disabled=""');
  expect(html).toContain('aria-busy="true"');
  expect(html).toContain('Queuing runs');
});
it('explains an empty selection', () => {
  const html = renderToStaticMarkup(React.createElement(LaunchPanel, {...props, disabled:true}));
  expect(html).toContain('Select at least one language and input size.');
  expect(html).toContain('disabled=""');
});
