import { readFileSync } from 'node:fs';
import { runInNewContext } from 'node:vm';
import assert from 'node:assert/strict';
import test from 'node:test';

const code = readFileSync(new URL('../../main/java/com/futo/platformplayer/backend/YoutubeAuthenticatedSettings.kt', import.meta.url), 'utf8');
const wrapper = code.match(/AUTHENTICATED_YOUTUBE_SEARCH = """([\s\S]*?)"""/)[1];

test('search and its continuations use auth without altering arguments or responses', () => {
  const calls = [];
  const context = { bridge: { isLoggedIn: () => true },
    requestSearch: (...args) => { calls.push(args); return 'search-response'; },
    requestSearchContinuation: (...args) => { calls.push(args); return 'next-response'; } };
  runInNewContext(wrapper, context);
  assert.equal(context.requestSearch('artist', false, 'filter'), 'search-response');
  assert.equal(context.requestSearchContinuation('cursor', false), 'next-response');
  assert.deepEqual(calls, [['artist', true, 'filter'], ['cursor', true]]);
});

test('missing helpers and logged-out sessions do not manufacture credentials', () => {
  runInNewContext(wrapper, {});
  const calls = [];
  const context = { bridge: { isLoggedIn: () => false }, requestSearch: (...args) => calls.push(args) };
  runInNewContext(wrapper, context);
  context.requestSearch('title', false, null);
  assert.deepEqual(calls, [['title', false, null]]);
});
