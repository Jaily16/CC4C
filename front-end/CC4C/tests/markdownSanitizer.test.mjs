import assert from 'node:assert/strict';
import test from 'node:test';

import { markdownHeadingId, sanitizeMarkdownHtml } from '../src/utils/markdownSanitizer.js';

test('removes executable HTML, event handlers, unsafe URLs and inline styles', () => {
  const result = sanitizeMarkdownHtml(`
    <script>alert('xss')</script>
    <img src="javascript:alert(1)" onerror="alert(2)" style="position:fixed" alt="avatar">
    <a href="javascript:alert(3)" onclick="alert(4)">unsafe</a>
    <iframe src="https://example.com"></iframe>
  `);

  assert.doesNotMatch(result, /script|javascript:|onerror|onclick|style=|iframe/i);
  assert.match(result, /<img alt="avatar" \/>/);
  assert.match(result, /<a rel="nofollow noopener noreferrer">unsafe<\/a>/);
});

test('preserves the safe Markdown structures used by the editor', () => {
  const result = sanitizeMarkdownHtml(`
    <h2 id="cc4c-md-1-intro"><a href="#cc4c-md-1-intro">Intro</a></h2>
    <pre class="language-java"><code class="language-java">record Demo() {}</code></pre>
    <table><thead><tr><th scope="col">Name</th></tr></thead></table>
    <img src="/blogImg/example.png" alt="example" loading="lazy">
    <input type="text" checked onclick="alert(1)">
  `);

  assert.match(result, /id="cc4c-md-1-intro"/);
  assert.match(result, /class="language-java"/);
  assert.match(result, /<table>/);
  assert.match(result, /src="\/blogImg\/example.png"/);
  assert.match(result, /<input checked type="checkbox" disabled \/>/);
  assert.doesNotMatch(result, /onclick|type="text"/);
});

test('hardens external links and strips untrusted heading identifiers', () => {
  const result = sanitizeMarkdownHtml(`
    <h1 id="location">Unsafe id</h1>
    <a href="https://example.com" target="_blank">external</a>
    <a href="//example.com">protocol relative</a>
  `);

  assert.doesNotMatch(result, /id="location"/);
  assert.match(result, /target="_blank" rel="nofollow noopener noreferrer"/);
  assert.doesNotMatch(result, /href="\/\/example.com"/);
});

test('generates stable, prefixed and bounded heading identifiers', () => {
  assert.equal(markdownHeadingId('<em>Hello, World!</em>', 2, 3), 'cc4c-md-3-hello-world');
  assert.equal(markdownHeadingId('中文标题', 2, 1), 'cc4c-md-1-section');
  assert.match(markdownHeadingId('a'.repeat(100), 1, 2), /^cc4c-md-2-a{64}$/);
});
