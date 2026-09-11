import assert from 'node:assert/strict';
import { createHash } from 'node:crypto';
import { readFileSync } from 'node:fs';
import { runInNewContext } from 'node:vm';
import test from 'node:test';

const source = readFileSync(new URL('./Code.gs', import.meta.url), 'utf8');
const csvFixtures = new Map();
function load(overrides = {}) {
  const environment = {
    Date, console: { log() {} },
    Utilities: {
      DigestAlgorithm: { SHA_256: 'sha256' }, Charset: { UTF_8: 'utf8' },
      computeDigest: (_, value) => [...createHash('sha256').update(value).digest()].map(byte => byte > 127 ? byte - 256 : byte),
      sleep: () => assert.fail('unexpected sleep'),
      parseCsv: csv => {
        // GAS owns CSV parsing; assert the real exported bytes decode to the expected CSV string.
        assert.ok(csvFixtures.has(csv), 'decoded CSV differs from the export fixture');
        return structuredClone(csvFixtures.get(csv));
      },
    },
  };
  return runInNewContext(source + `\n({
    initializeReviewNotifications, importHistoricalReviews, notifyHistoricalReviews,
    pollPlayReviews, sendTestNotification, installHourlyTrigger, removeReviewTriggers,
    withReviewLock_, requestReviewJson_, fetchPlayReviews_, playReviewEntry_, mergeReviewEntries_,
    processPlayReviews_, createReviewSheetStore_, configureReviewSheet_, reviewRecordRow_,
    discordWebhookUrl_, reviewDiscordPayload_, openInitializedReviewStore_,
    reviewReportBucket_, listReviewReports_, fetchReviewReport_, reviewReportEntries_, DiscordRateLimitError
  });`, {
    ...environment, ...overrides, Utilities: { ...environment.Utilities, ...overrides.Utilities },
  });
}

function testClock() {
  let now = 1900000000000;
  const delays = [];
  class FakeDate extends Date {
    constructor(...args) { super(...(args.length ? args : [now])); }
    static now() { return now; }
  }
  return { get now() { return now; }, delays, advance(ms) { now += ms; },
    services: { Date: FakeDate, Utilities: { sleep: ms => {
      assert.ok(ms > 0 && ms <= 60000); delays.push(ms); now += ms;
    } } },
  };
}
function review(id = 'review-1', user = {}) {
  return { reviewId: id, authorName: '投稿者', comments: [{ userComment: {
    starRating: 5, text: '使いやすいです', lastModified: { seconds: '1700000000', nanos: 0 },
    appVersionName: '1.1.0', reviewerLanguage: 'ja', ...user,
  } }] };
}
function memoryStore(initial = []) {
  return {
    records: structuredClone(initial), snapshots: [],
    load() { return structuredClone(this.records); },
    saveAll(records) { this.records = structuredClone(records); this.snapshots.push(structuredClone(records)); },
    save(record) { this.records[this.records.findIndex(item => item.id === record.id)] = structuredClone(record); },
  };
}
function response(status, body, headers = {}) {
  return { getResponseCode: () => status, getContentText: () => JSON.stringify(body), getHeaders: () => headers };
}
function fakeSheet() {
  const sheet = {
    values: [], maxRows: 1000, formulas: [], getSheetId: () => 123,
    getLastRow() { return this.values.length; }, getMaxRows() { return this.maxRows; },
    insertRowsAfter(_, count) { this.maxRows += count; },
    getRange(row, col, height = 1, width = 1) {
      if (typeof row === 'string') return { setWrap() {}, setNumberFormat() {} };
      const range = {
        getValues: () => Array.from({ length: height }, (_, r) => Array.from({ length: width }, (_, c) => sheet.values[row - 1 + r]?.[col - 1 + c] ?? '')),
        getValue: () => sheet.values[row - 1]?.[col - 1] ?? '',
        getFormulas: () => sheet.formulas.length ? sheet.formulas : Array.from({ length: height }, () => Array(width).fill('')),
        setValues(values) {
          assert.equal(values.length, height);
          values.forEach((cells, r) => {
            assert.equal(cells.length, width);
            const target = sheet.values[row - 1 + r] ||= [];
            cells.forEach((value, c) => {
              // Simulate the Sheets apostrophe escape; never evaluate review text.
              target[col - 1 + c] = typeof value === 'string' && value.startsWith("'") ? value.slice(1) : value;
            });
          });
          return range;
        },
        setBackground() { return range; }, setFontColor() { return range; }, setFontWeight() { return range; },
      };
      return range;
    },
    setFrozenRows() {}, setColumnWidths() {}, setColumnWidth() {}, hideColumns() {},
  };
  return sheet;
}
function gasEnvironment(fetchImpl, reportFetch = () => response(200, {})) {
  const properties = new Map([
    ['DISCORD_WEBHOOK_URL', 'https://discord.com/api/webhooks/123/fake-token'],
    ['PLAY_REPORT_BUCKET', 'pubsite_prod_rev_123456'],
  ]);
  const sheets = [], triggers = [];
  let flushes = 0;
  const spreadsheet = {
    getId: () => 'spreadsheet-1', getSheets: () => sheets, getSheetByName: () => sheets[0] || null,
    insertSheet() { const sheet = fakeSheet(); sheets.push(sheet); return sheet; },
  };
  return {
    properties, sheets, triggers, get flushes() { return flushes; },
    services: {
      PropertiesService: { getScriptProperties: () => ({
        getProperty: key => properties.get(key) ?? null, setProperty: (key, value) => properties.set(key, value),
      }) },
      SpreadsheetApp: { getActiveSpreadsheet: () => spreadsheet, openById: id => {
        assert.equal(id, 'spreadsheet-1'); return spreadsheet;
      }, flush: () => flushes++ },
      LockService: { getScriptLock: () => ({ tryLock: () => true, releaseLock() {} }) },
      ScriptApp: {
        getOAuthToken: () => 'fake-access-token', getProjectTriggers: () => [...triggers],
        newTrigger: handler => ({ timeBased: () => ({ everyHours: hours => ({ create() {
          assert.equal(hours, 1); triggers.push({ getHandlerFunction: () => handler });
        } }) }) }),
        deleteTrigger: trigger => triggers.splice(triggers.indexOf(trigger), 1),
      }, UrlFetchApp: { fetch: (url, options) => url.startsWith('https://storage.googleapis.com/')
        ? reportFetch(url, options) : fetchImpl(url, options) },
    },
  };
}

test('initialization checks APIs and baselines existing reviews without posting or resetting history', () => {
  let calls = 0;
  const env = gasEnvironment((url, options) => {
    calls++; assert.equal(options.method, 'get');
    if (url.startsWith('https://discord.com/')) return response(200, { id: '123' });
    assert.equal(options.headers.Authorization, 'Bearer fake-access-token');
    return response(200, { reviews: [review()] });
  });
  const api = load(env.services), result = api.initializeReviewNotifications();
  assert.equal(result.initialized, 1); assert.equal(result.notified, 0); assert.equal(calls, 2);
  const record = api.createReviewSheetStore_(env.sheets[0]).load()[0];
  assert.equal(record.status, '初回対象外'); assert.equal(record.notifiedVersion, record.version);
  assert.equal(record.notifiedAt, ''); assert.equal(record.messageId, '');
  assert.throws(() => api.initializeReviewNotifications(), /初期化済み/);
});

test('empty initial poll is initialized and failed initial API creates no sheet', () => {
  const env = gasEnvironment(() => response(200, {})), api = load(env.services);
  assert.equal(api.initializeReviewNotifications().initialized, 0);
  assert.equal(api.openInitializedReviewStore_().load().length, 0);
  const failing = gasEnvironment(() => response(403, { secret: 'not logged' }));
  assert.throws(() => load(failing.services).initializeReviewNotifications(), /HTTP 403/);
  assert.equal(failing.sheets.length, 0); assert.equal(failing.properties.has('PLAY_REVIEW_STATE'), false);
});

test('poll entrypoint persists pending rows before POST and saves the confirmed Discord ID', () => {
  let initialized = false;
  const env = gasEnvironment((url, options) => {
    if (options.method === 'post') {
      assert.equal(env.sheets[0].values[1][0], '未通知'); assert.equal(env.sheets[0].values[1][11], '');
      assert.ok(env.flushes >= 2); assert.ok(url.endsWith('?wait=true'));
      return response(200, { id: '456789012345678901' });
    }
    return response(200, { reviews: initialized ? [review()] : [] });
  });
  const api = load(env.services);
  api.initializeReviewNotifications(); initialized = true;
  assert.equal(api.pollPlayReviews().notified, 1);
  assert.equal(env.sheets[0].values[1][0], '通知済み');
  assert.equal(env.sheets[0].values[1][12], '456789012345678901');
  assert.ok(env.sheets[0].values[1][6] instanceof Date);
  assert.equal(api.pollPlayReviews().notified, 0);
});

test('new/updated reviews notify once; developer replies and helpful votes are ignored', () => {
  const api = load(), original = review();
  const store = memoryStore(api.mergeReviewEntries_([], [api.playReviewEntry_(original)], true));
  original.comments.push({ developerComment: { text: 'ありがとうございます' } });
  original.comments[0].userComment.thumbsUpCount = 20;
  const options = { store, hasTime: () => true, getEntries: () => [api.playReviewEntry_(original)],
    send: () => assert.fail('must not notify') };
  assert.equal(api.processPlayReviews_(options).notified, 0);
  const sent = [];
  options.getEntries = () => [api.playReviewEntry_(review('review-1', { text: '更新しました', starRating: 2 })), api.playReviewEntry_(review('review-2'))];
  options.send = record => { sent.push(api.reviewDiscordPayload_(record)); return '123'; };
  assert.equal(api.processPlayReviews_(options).notified, 2);
  assert.match(sent[0].embeds[0].title, /レビュー更新/); assert.match(sent[1].embeds[0].title, /新着レビュー/);
  assert.equal(api.processPlayReviews_(options).notified, 0);
});

test('failed sends preserve progress and queued content survives the Play API time window', () => {
  const api = load(), store = memoryStore();
  const entries = [api.playReviewEntry_(review('first')), api.playReviewEntry_(review('second'))];
  let sent = 0;
  assert.throws(() => api.processPlayReviews_({ store, getEntries: () => entries, hasTime: () => true,
    send: () => { if (++sent === 2) throw new Error('send failed'); return '1'; },
  }), /send failed/);
  assert.equal(store.records[0].status, '通知済み'); assert.equal(store.records[1].status, '送信エラー');
  assert.equal(store.records[1].notifiedVersion, '');
  const remaining = [];
  assert.equal(api.processPlayReviews_({ store, getEntries: () => [], hasTime: () => true,
    send: record => { remaining.push(record.id); return '2'; },
  }).notified, 1);
  assert.deepEqual(remaining, ['second']);
});

test('checkpoint failure stops later sends; failed initial persistence sends nothing', () => {
  const api = load(), store = memoryStore();
  const entries = [api.playReviewEntry_(review('first')), api.playReviewEntry_(review('second'))];
  let sent = 0;
  const options = { store, getEntries: () => entries, hasTime: () => true, send: () => { sent++; return '1'; } };
  store.save = () => { throw new Error('checkpoint failed'); };
  assert.throws(() => api.processPlayReviews_(options), /checkpoint failed/); assert.equal(sent, 1);
  store.saveAll = () => { throw new Error('write failed'); }; sent = 0;
  assert.throws(() => api.processPlayReviews_(options), /write failed/); assert.equal(sent, 0);
});

test('time budget persists remaining work and resumes next time', () => {
  const api = load(), store = memoryStore();
  const entries = [api.playReviewEntry_(review('first')), api.playReviewEntry_(review('second'))];
  let checks = 0;
  const result = api.processPlayReviews_({ store, getEntries: () => entries, hasTime: () => ++checks < 3, send: () => '1' });
  assert.equal(result.notified, 1); assert.equal(result.deferred, true); assert.equal(store.records[1].status, '未通知');
  assert.equal(api.processPlayReviews_({ store, getEntries: () => [], hasTime: () => true, send: () => '2' }).notified, 1);
});

test('Play paging encodes tokens and a partial failure never saves state', () => {
  let calls = 0;
  const env = gasEnvironment((url, options) => {
    assert.equal(options.headers.Authorization, 'Bearer fake-access-token');
    if (++calls === 1) return response(200, { reviews: [review()], tokenPagination: { nextPageToken: 'next/+=' } });
    assert.ok(url.includes('token=next%2F%2B%3D')); return response(403, {});
  });
  const api = load(env.services), store = memoryStore();
  assert.throws(() => api.processPlayReviews_({ store, getEntries: () => api.fetchPlayReviews_(Date.now() + 5000),
    hasTime: () => true, send: () => assert.fail('must not send'),
  }), /HTTP 403/);
  assert.equal(store.snapshots.length, 0);
});

test('pagination collapses duplicate IDs to the newest revision and rejects repeated tokens', () => {
  let calls = 0;
  const env = gasEnvironment(() => ++calls === 1
    ? response(200, { reviews: [review()], tokenPagination: { nextPageToken: 'next' } })
    : response(200, { reviews: [review('review-1', { lastModified: { seconds: '1700000000', nanos: 1 }, text: '最新版' })] }));
  const api = load(env.services), entries = api.fetchPlayReviews_(Date.now() + 5000);
  assert.equal(entries.length, 1); assert.equal(entries[0].text, '最新版');
  env.services.UrlFetchApp.fetch = () => response(200, { tokenPagination: { nextPageToken: 'same' } });
  assert.throws(() => api.fetchPlayReviews_(Date.now() + 5000), /ページトークンが重複/);
});

test('formula text, apostrophes, leading zero IDs and dates round-trip through the sheet', () => {
  const api = load({ SpreadsheetApp: { flush() {} } }), sheet = fakeSheet();
  api.configureReviewSheet_(sheet);
  const store = api.createReviewSheetStore_(sheet);
  const entry = api.playReviewEntry_(review('000123', { text: '=IMPORTXML("https://example.com", "//a")', appVersionName: "'1.2" }));
  const records = api.mergeReviewEntries_([], [entry], false);
  assert.ok(api.reviewRecordRow_(records[0])[2].startsWith("'="));
  store.saveAll(records);
  const restored = store.load()[0];
  assert.equal(restored.id, '000123'); assert.equal(restored.text, entry.text); assert.equal(restored.appVersion, "'1.2");
  assert.equal(restored.modifiedAt.getTime(), entry.modifiedAt.getTime());
});

test('history rejects corrupt headers, duplicate IDs, formulas and sorting during a send', () => {
  const api = load({ SpreadsheetApp: { flush() {} } }), sheet = fakeSheet();
  const store = api.createReviewSheetStore_(sheet);
  assert.throws(() => store.load(), /見出し/);
  api.configureReviewSheet_(sheet);
  const records = api.mergeReviewEntries_([], [api.playReviewEntry_(review('first')), api.playReviewEntry_(review('second'))], false);
  store.saveAll(records); sheet.values[2][8] = 'first';
  assert.throws(() => store.load(), /破損/);
  store.saveAll(records); sheet.formulas = [['=1+1']];
  assert.throws(() => store.load(), /数式/);
  sheet.formulas = []; store.load();
  [sheet.values[1], sheet.values[2]] = [sheet.values[2], sheet.values[1]];
  assert.throws(() => store.save(records[0]), /行が移動/);
});

test('missing initialization, deleted sheet or changed spreadsheet fails closed', () => {
  const env = gasEnvironment(() => response(200, {})), api = load(env.services);
  assert.throws(() => api.pollPlayReviews(), /初期化情報/); api.initializeReviewNotifications();
  env.properties.set('SPREADSHEET_ID', 'different');
  assert.throws(() => api.pollPlayReviews(), /異なります/); env.properties.delete('SPREADSHEET_ID'); env.sheets.length = 0;
  assert.throws(() => api.pollPlayReviews(), /通知履歴シートがありません/);
});

test('HTTP 429 respects retry_after and failures omit URL, response body and credentials', () => {
  let calls = 0;
  const clock = testClock();
  const env = gasEnvironment(() => ++calls === 1 ? response(429, { retry_after: 1.5 }) : response(200, { id: '1' }));
  const api = load({ ...env.services, ...clock.services });
  assert.equal(api.requestReviewJson_('secret-url', { method: 'post' }, 'Discord', clock.now + 5000).id, '1');
  assert.deepEqual(clock.delays, [1750]);
  const failing = load(gasEnvironment(() => response(500, { secret: 'secret-token' })).services);
  assert.throws(() => failing.requestReviewJson_('secret-url', { method: 'post' }, 'Discord', Date.now() + 5000),
    error => error.message.includes('HTTP 500') && !error.message.includes('secret'));
  const network = load(gasEnvironment(() => { throw new Error('secret-url'); }).services);
  assert.throws(() => network.requestReviewJson_('secret-url', { method: 'post' }, 'Discord', Date.now() + 5000), /Discord: 通信に失敗しました。/);
});

test('lock skips overlapping runs, releases on failure and sanitizes unexpected errors', () => {
  let released = false, available = false;
  const api = load({ LockService: { getScriptLock: () => ({ tryLock: () => available, releaseLock: () => { released = true; } }) } });
  assert.equal(api.withReviewLock_(() => assert.fail('must not run')).skipped, true); available = true;
  assert.throws(() => api.withReviewLock_(() => { throw new Error('secret-token'); }), error => !error.message.includes('secret-token'));
  assert.equal(released, true);
});

test('trigger installation is idempotent and stopping preserves history and unrelated triggers', () => {
  const env = gasEnvironment(() => response(200, {})), api = load(env.services);
  api.initializeReviewNotifications(); env.triggers.push({ getHandlerFunction: () => 'unrelatedTask' });
  api.installHourlyTrigger(); api.installHourlyTrigger(); assert.equal(env.triggers.length, 2);
  api.removeReviewTriggers(); assert.equal(env.triggers.length, 1);
  assert.equal(env.triggers[0].getHandlerFunction(), 'unrelatedTask'); assert.ok(env.properties.has('PLAY_REVIEW_STATE'));
});

test('webhook validation forces wait, preserves thread and rejects foreign hosts', () => {
  let url = 'https://discord.com/api/webhooks/123/test-token?wait=false&thread_id=456';
  const api = load({ PropertiesService: { getScriptProperties: () => ({ getProperty: () => url }) } });
  assert.equal(api.discordWebhookUrl_(), 'https://discord.com/api/webhooks/123/test-token?wait=true&thread_id=456');
  for (const invalid of ['http://discord.com/api/webhooks/123/token', 'https://example.com/api/webhooks/123/token',
    'https://discord.com/api/webhooks/123/token/github', 'https://discord.com/api/webhooks/123/token#fragment', '']) {
    url = invalid; assert.throws(() => api.discordWebhookUrl_(), /DISCORD_WEBHOOK_URL/);
  }
});

test('Discord payload suppresses mentions and fits long emoji-heavy text', () => {
  const api = load(), entry = api.playReviewEntry_(review('id/+?', { text: '@everyone\t' + '😀'.repeat(3000) }));
  const payload = api.reviewDiscordPayload_(entry);
  assert.equal(payload.allowed_mentions.parse.length, 0);
  assert.ok(payload.embeds[0].description.length <= 4096); assert.ok(payload.embeds[0].description.isWellFormed());
  assert.ok(payload.embeds[0].description.startsWith('@everyone\n'));
  assert.ok(payload.embeds[0].url.includes('reviewId=id%2F%2B%3F'));
});

test('manifest declares only required OAuth scopes and V8 runtime', () => {
  const manifest = JSON.parse(readFileSync(new URL('./appsscript.json', import.meta.url), 'utf8'));
  assert.equal(manifest.runtimeVersion, 'V8');
  assert.deepEqual(manifest.oauthScopes, [
    'https://www.googleapis.com/auth/androidpublisher', 'https://www.googleapis.com/auth/devstorage.read_only',
    'https://www.googleapis.com/auth/spreadsheets',
    'https://www.googleapis.com/auth/script.external_request', 'https://www.googleapis.com/auth/script.scriptapp',
  ]);
});

test('manual connection test sends a clearly labeled message without altering history', () => {
  let posted = 0;
  const env = gasEnvironment((_, options) => {
    if (options.method === 'post') {
      const payload = JSON.parse(options.payload);
      assert.match(payload.content, /接続テスト/);
      assert.equal(payload.allowed_mentions.parse.length, 0);
      posted++;
      return response(200, { id: '123456' });
    }
    return response(200, {});
  });
  const api = load(env.services);
  api.initializeReviewNotifications();
  const before = JSON.stringify(env.sheets[0].values);
  assert.equal(api.sendTestNotification().messageId, '123456');
  assert.equal(posted, 1);
  assert.equal(JSON.stringify(env.sheets[0].values), before);
});

test('a successful HTTP response without Discord confirmation never marks a review notified', () => {
  let initialized = false;
  const env = gasEnvironment((_, options) => options.method === 'post'
    ? response(200, {}) : response(200, { reviews: initialized ? [review()] : [] }));
  const api = load(env.services);
  api.initializeReviewNotifications(); initialized = true;
  assert.throws(() => api.pollPlayReviews(), /送信完了の確認/);
  assert.equal(env.sheets[0].values[1][0], '送信エラー');
  assert.equal(env.sheets[0].values[1][11], '');
});

const reportHeaders = [
  'Review Text', 'Package Name', 'Star Rating', 'Review Submit Millis Since Epoch',
  'Review Last Update Millis Since Epoch', 'Review Title', 'Review Link', 'App Version Name',
  'Reviewer Language', 'Developer Reply Text', 'Device',
];
function reportRow(id = 'old-review', fields = {}) {
  const record = {
    'Package Name': 'com.yt8492.doujinshimanager', 'Star Rating': '4',
    'Review Submit Millis Since Epoch': '1600000000123', 'Review Last Update Millis Since Epoch': '',
    'Review Text': '過去のレビュー「日本語, 😀」', 'Review Title': '感想',
    'Review Link': id ? 'https://play.google.com/apps/publish/?dev_acc=123#ReviewPlace:id=com.yt8492.doujinshimanager&reviewid=' + encodeURIComponent(id) : '',
    'App Version Name': '1.0', 'Reviewer Language': 'ja', 'Developer Reply Text': '', 'Device': 'device', ...fields,
  };
  return reportHeaders.map(header => record[header]);
}
const reportName = month => `reviews/reviews_com.yt8492.doujinshimanager_${month}.csv`;
function reportResponse(rows, charset = 'UTF-16LE', bom = true) {
  const table = [reportHeaders, ...rows];
  const csv = table.map(row => row.map(cell => '"' + cell.replaceAll('"', '""') + '"').join(',')).join('\r\n');
  csvFixtures.set(csv, table);
  let bytes = Buffer.from((bom ? '\uFEFF' : '') + csv, charset === 'UTF-8' ? 'utf8' : 'utf16le');
  if (charset === 'UTF-16BE') bytes = bytes.swap16();
  return { ...response(200, {}), getBlob: () => ({
    getBytes: () => [...bytes].map(value => value > 127 ? value - 256 : value),
    getDataAsString: encoding => new TextDecoder(encoding).decode(bytes),
  }) };
}
function reportServer(files) {
  return (url, options) => {
    assert.equal(options.method, 'get');
    assert.equal(options.headers.Authorization, 'Bearer fake-access-token');
    const parsed = new URL(url);
    if (parsed.searchParams.get('alt') === 'media') {
      const name = decodeURIComponent(parsed.pathname.split('/o/')[1]);
      assert.ok(files.has(name), 'unexpected report download');
      return files.get(name)();
    }
    assert.equal(parsed.searchParams.get('prefix'), 'reviews/reviews_com.yt8492.doujinshimanager_');
    return response(200, { items: [...files.keys()].map(name => ({ name })) });
  };
}

test('initialization imports all available months, deduplicates IDs and prefers live API content without posting', () => {
  const files = new Map([
    [reportName('202102'), () => reportResponse([
      reportRow('old', { 'Review Last Update Millis Since Epoch': '1610000000000', 'Review Text': '更新された過去レビュー' }),
      reportRow('review-1', { 'Review Last Update Millis Since Epoch': '1700000000000', 'Review Text': 'CSVでは改行なし' }),
      reportRow('', { 'Review Text': '=IMPORTXML("https://example.test", "//*")' }),
      reportRow('', { 'Review Text': '=IMPORTXML("https://example.test", "//*")' }),
    ])],
    [reportName('202001'), () => reportResponse([reportRow('old')])],
  ]);
  const serveReports = reportServer(files);
  const env = gasEnvironment((url, options) => {
    assert.equal(options.method, 'get');
    return response(200, url.includes('androidpublisher') ? { reviews: [review()] } : { id: '123' });
  }, (url, options) => {
    assert.ok(new URL(url).pathname.startsWith('/storage/v1/b/pubsite_prod_123456/o'));
    return serveReports(url, options);
  });
  env.properties.set('PLAY_REPORT_BUCKET', 'gs://pubsite_prod_123456/reviews/');
  const api = load(env.services), result = api.initializeReviewNotifications();
  assert.equal(result.complete, true); assert.equal(result.reportsProcessed, 2);
  assert.equal(result.initialized, 4); assert.equal(result.unidentified, 2); assert.equal(result.notified, 0);
  const records = api.openInitializedReviewStore_().load();
  assert.equal(records.find(record => record.id === 'review-1').author, '投稿者');
  assert.equal(records.find(record => record.id === 'review-1').text, '使いやすいです');
  const old = records.find(record => record.id === 'old');
  assert.equal(old.text, '感想\t更新された過去レビュー'); assert.equal(old.status, '過去取込');
  assert.equal(old.author, '不明（CSVに記載なし）');
  assert.ok(records.every(record => record.version === record.notifiedVersion && record.notifiedAt === '' && record.messageId === ''));
  assert.equal(records.filter(record => record.text.startsWith('感想\t=IMPORTXML')).length, 2);
  assert.equal(api.pollPlayReviews().notified, 0);
});

test('report decoding handles UTF-16 LE/BE with or without BOM, UTF-8, commas, quotes and newlines', () => {
  for (const charset of ['UTF-16LE', 'UTF-16BE', 'UTF-8']) {
    for (const bom of [true, false]) {
      const env = gasEnvironment(() => assert.fail('only report GET expected'), () => reportResponse([
        reportRow('id/+?', { 'Review Title': '', 'Review Text': '=テスト,"引用"\n😀' }),
      ], charset, bom));
      const entries = load(env.services).fetchReviewReport_('pubsite_prod_rev_123456', reportName('202001'), Date.now() + 60000);
      assert.equal(entries[0].id, 'id/+?'); assert.equal(entries[0].text, '=テスト,"引用"\n😀');
      assert.equal(entries[0].modifiedAt.getTime(), 1600000000123);
    }
  }
});

test('CSV parsing accepts reordered/optional columns, skips ratings, and rejects corrupt rows instead of losing them', () => {
  const api = load(), name = reportName('202001');
  const rows = [reportHeaders, reportRow('valid'), reportRow('rating', { 'Review Title': '', 'Review Text': '' })];
  assert.equal(api.reviewReportEntries_(rows, name).length, 1);
  for (const field of [{ 'Star Rating': '9' }, { 'Package Name': 'another.app' },
    { 'Review Last Update Millis Since Epoch': 'bad' }, { 'Review Submit Millis Since Epoch': '99999999999999999999' }]) {
    assert.throws(() => api.reviewReportEntries_([reportHeaders, reportRow('bad', field)], name), /2行目/);
  }
  assert.throws(() => api.reviewReportEntries_([['wrong'], ['value']], name), /見出し/);
  assert.throws(() => api.reviewReportEntries_([reportHeaders, ['short']], name), /2行目/);
  const unknown = api.reviewReportEntries_([reportHeaders, reportRow('', {
    'Review Link': 'https://evil.test/?reviewId=valid',
  })], name)[0];
  assert.match(unknown.id, /^csv:/); assert.ok(!unknown.url.includes('evil.test'));
  const noLinkHeader = reportHeaders.filter(header => !['Review Link', 'App Version Name'].includes(header));
  const noLinkRow = reportRow().filter((_, index) => noLinkHeader.includes(reportHeaders[index]));
  assert.match(api.reviewReportEntries_([noLinkHeader, noLinkRow], name)[0].id, /^csv:/);
});

test('report listing follows every page and filters exact package/month CSV names', () => {
  let calls = 0;
  const env = gasEnvironment(() => assert.fail('only report GET expected'), url => {
    if (++calls === 1) return response(200, { items: [
      { name: reportName('202102') }, { name: reportName('202113') },
      { name: 'reviews/reviews_com.yt8492.doujinshimanager.other_202001.csv' },
    ], nextPageToken: 'next/+=' });
    assert.equal(new URL(url).searchParams.get('pageToken'), 'next/+=');
    return response(200, { items: [{ name: reportName('202001') }, { name: reportName('202102') }] });
  });
  const api = load(env.services);
  assert.deepEqual([...api.listReviewReports_('pubsite_prod_rev_123456', Date.now() + 60000)], [reportName('202001'), reportName('202102')]);
  const cyclic = gasEnvironment(() => {}, () => response(200, { nextPageToken: 'same' }));
  assert.throws(() => load(cyclic.services).listReviewReports_('pubsite_prod_rev_123456', Date.now() + 60000), /トークン/);
});

test('initial import saves each month and resumes after a failed download without allowing notifications prematurely', () => {
  let fail = true, firstDownloads = 0;
  const files = new Map([
    [reportName('202001'), () => { firstDownloads++; return reportResponse([reportRow('first'), reportRow('')]); }],
    [reportName('202002'), () => fail ? response(403, { secret: 'hidden' }) : reportResponse([reportRow('second')])],
  ]);
  const env = gasEnvironment(() => response(200, {}), reportServer(files)), api = load(env.services);
  assert.throws(() => api.initializeReviewNotifications(), /HTTP 403/);
  assert.equal(env.sheets[0].values.length, 3);
  assert.equal(JSON.parse(env.properties.get('PLAY_REVIEW_STATE')).historyImport.lastReport, reportName('202001'));
  assert.throws(() => api.pollPlayReviews(), /初回取り込み中/);
  assert.throws(() => api.installHourlyTrigger(), /初回取り込み中/);
  assert.throws(() => api.notifyHistoricalReviews(), /初回取り込み中/);
  fail = false;
  assert.equal(api.initializeReviewNotifications().complete, true);
  assert.equal(firstDownloads, 1); assert.equal(api.openInitializedReviewStore_().load().length, 3);
});

test('history import stops before the GAS deadline and the same entrypoint resumes remaining months', () => {
  let now = 1900000000000, downloads = 0;
  class FakeDate extends Date { static now() { return now; } }
  const files = new Map([
    [reportName('202001'), () => { downloads++; now += 200000; return reportResponse([reportRow('first')]); }],
    [reportName('202002'), () => { downloads++; return reportResponse([reportRow('second')]); }],
  ]);
  const env = gasEnvironment(() => response(200, {}), reportServer(files));
  const api = load({ ...env.services, Date: FakeDate });
  const partial = api.initializeReviewNotifications();
  assert.equal(partial.complete, false); assert.equal(partial.remainingReports, 1); assert.equal(downloads, 1);
  assert.equal(api.initializeReviewNotifications().complete, true); assert.equal(downloads, 2);
});

test('a failure between sheet flush and import checkpoint safely retries the month without duplicate archive rows', () => {
  let fail = true, downloads = 0;
  const env = gasEnvironment(() => response(200, {}), reportServer(new Map([
    [reportName('202001'), () => { downloads++; return reportResponse([reportRow('first'), reportRow('')]); }],
  ])));
  const actualProperties = env.services.PropertiesService.getScriptProperties();
  env.services.PropertiesService.getScriptProperties = () => ({ ...actualProperties, setProperty(key, value) {
    if (fail && JSON.parse(value).historyImport?.lastReport) { fail = false; throw new Error('checkpoint failed'); }
    return actualProperties.setProperty(key, value);
  } });
  const api = load(env.services);
  assert.throws(() => api.initializeReviewNotifications(), /レビュー処理に失敗/);
  assert.equal(env.sheets[0].values.length, 3);
  assert.equal(api.initializeReviewNotifications().complete, true);
  assert.equal(downloads, 2); assert.equal(api.openInitializedReviewStore_().load().length, 2);
});

test('legacy initialization can backfill older reviews while preserving sent/pending state and leaving recent reviews to polling', () => {
  const env = gasEnvironment(() => response(200, {})), api = load(env.services);
  api.initializeReviewNotifications();
  const state = JSON.parse(env.properties.get('PLAY_REVIEW_STATE'));
  delete state.historyImport; delete state.initializing;
  env.properties.set('PLAY_REVIEW_STATE', JSON.stringify(state));
  const store = api.openInitializedReviewStore_();
  const sent = api.mergeReviewEntries_([], [api.playReviewEntry_(review('sent'))], true)[0];
  Object.assign(sent, { status: '通知済み', messageId: '456', notifiedAt: new Date() });
  const pending = api.mergeReviewEntries_([], [api.playReviewEntry_(review('pending'))], false)[0];
  store.saveAll([sent, pending]);
  const before = JSON.stringify(env.sheets[0].values);
  env.services.UrlFetchApp.fetch = reportServer(new Map([[reportName('202001'), () => reportResponse([
    reportRow('sent', { 'Review Last Update Millis Since Epoch': '1800000000000' }), reportRow('pending'),
    reportRow('old'), reportRow('recent', { 'Review Submit Millis Since Epoch': String(Date.now()) }),
  ])]]));
  const result = api.importHistoricalReviews();
  assert.equal(result.complete, true); assert.equal(result.initialized, 3); assert.equal(result.notified, 0);
  assert.equal(JSON.stringify(env.sheets[0].values.slice(0, 3)), before);
  assert.equal(store.load().some(record => record.id === 'recent'), false);
  assert.throws(() => api.importHistoricalReviews(), /完了済み/);
});

test('CSV-to-API hydration ignores formatting/precision differences but still notifies genuine edits', () => {
  const api = load();
  const entry = api.reviewReportEntries_([reportHeaders, reportRow('old', {
    'Review Title': '', 'Review Text': '使いやすいです', 'Review Last Update Millis Since Epoch': '1700000000123',
    'Star Rating': '5',
  })], reportName('202001'))[0];
  const initial = { ...entry, status: '過去取込', notifiedVersion: entry.version, notifiedAt: '', messageId: '' };
  const store = memoryStore([initial]);
  let current = review('old', { text: '使いやすい\nです', lastModified: { seconds: '1700000000', nanos: 123456789 } });
  const options = { store, hasTime: () => true, getEntries: () => [api.playReviewEntry_(current)],
    send: () => assert.fail('hydration must not notify') };
  assert.equal(api.processPlayReviews_(options).notified, 0); assert.equal(store.records[0].author, '投稿者');
  current = review('old', { text: '変更しました', lastModified: { seconds: '1700000001', nanos: 0 } });
  options.send = () => '987';
  assert.equal(api.processPlayReviews_(options).notified, 1);
  assert.equal(api.processPlayReviews_(options).notified, 0);
});

test('missing report configuration and denied report access fail before creating history; bucket URI is accepted', () => {
  const env = gasEnvironment(() => response(200, {})), api = load(env.services);
  env.properties.delete('PLAY_REPORT_BUCKET');
  assert.throws(() => api.initializeReviewNotifications(), /PLAY_REPORT_BUCKET/); assert.equal(env.sheets.length, 0);
  env.properties.set('PLAY_REPORT_BUCKET', 'gs://pubsite_prod_rev_123456/reviews/');
  assert.equal(api.reviewReportBucket_(), 'pubsite_prod_rev_123456');
  const denied = gasEnvironment(() => response(200, {}), () => response(403, {}));
  assert.throws(() => load(denied.services).initializeReviewNotifications(), /レポート: HTTP 403/);
  assert.equal(denied.sheets.length, 0);
});

test('changing the report bucket during initialization fails before any fetch or history mutation', () => {
  const env = gasEnvironment(() => response(200, {}), reportServer(new Map([
    [reportName('202001'), () => response(403, {})],
  ])));
  const api = load(env.services);
  assert.throws(() => api.initializeReviewNotifications(), /HTTP 403/);
  const before = JSON.stringify(env.sheets[0].values);
  env.properties.set('PLAY_REPORT_BUCKET', 'pubsite_prod_rev_different');
  env.services.UrlFetchApp.fetch = () => assert.fail('configuration must be checked before network access');
  assert.throws(() => api.initializeReviewNotifications(), /設定が変更・破損/);
  assert.equal(JSON.stringify(env.sheets[0].values), before);
});

test('unknown IDs preserve separate snapshots when an export is regenerated with a different review in the same row', () => {
  const api = load(), name = reportName('202001');
  const first = api.reviewReportEntries_([reportHeaders, reportRow('')], name)[0];
  const retry = api.reviewReportEntries_([reportHeaders, reportRow('')], name)[0];
  const changed = api.reviewReportEntries_([reportHeaders, reportRow('', { 'Review Text': '別の投稿者の感想' })], name)[0];
  assert.equal(first.id, retry.id); assert.notEqual(first.id, changed.id);
});

test('manual historical notifications send saved CSV and initial API reviews once while preserving the regular queue', () => {
  const env = gasEnvironment(() => response(200, { reviews: [review()] }), reportServer(new Map([
    [reportName('202001'), () => reportResponse([reportRow('old'), reportRow('', { 'Review Text': 'IDがない過去レビュー' })])],
  ])));
  const api = load(env.services);
  api.initializeReviewNotifications();
  const store = api.openInitializedReviewStore_();
  store.saveAll(api.mergeReviewEntries_(store.load(), [api.playReviewEntry_(review('new'))], false));
  const newBefore = structuredClone(store.load().find(record => record.id === 'new'));
  const messages = [];
  env.services.UrlFetchApp.fetch = (url, options) => {
    assert.ok(url.startsWith('https://discord.com/') && url.endsWith('?wait=true'));
    assert.equal(options.method, 'post', 'manual send must not request Play data');
    const message = JSON.parse(options.payload);
    assert.match(message.embeds[0].title, /過去レビュー$/); assert.equal(message.allowed_mentions.parse.length, 0);
    // The previously confirmed delivery must already be checkpointed before the next POST.
    assert.equal(store.load().filter(record => record.status === '過去通知済み').length, messages.length);
    messages.push(message);
    return response(200, { id: String(1000 + messages.length) });
  };
  const result = api.notifyHistoricalReviews();
  assert.equal(result.notified, 3); assert.equal(result.pending, 0);
  assert.ok(Date.parse(messages[0].embeds[0].timestamp) < Date.parse(messages[2].embeds[0].timestamp));
  assert.deepEqual(structuredClone(store.load().find(record => record.id === 'new')), newBefore);
  assert.ok(store.load().filter(record => record.id !== 'new').every(record =>
    record.status === '過去通知済み' && record.notifiedAt instanceof Date && record.messageId && record.notifiedVersion === record.version));
  assert.equal(api.notifyHistoricalReviews().notified, 0); assert.equal(messages.length, 3);
});

test('failed historical sends resume manually; polling does not automatically send the historical remainder', () => {
  let fail = true, attempts = 0;
  const existing = [review('first'), review('second'), review('third')];
  const env = gasEnvironment((_, options) => {
    if (options.method !== 'post') return response(200, { reviews: existing });
    if (++attempts === 2 && fail) return response(400, {});
    return response(200, { id: String(attempts) });
  });
  const api = load(env.services);
  api.initializeReviewNotifications();
  assert.throws(() => api.notifyHistoricalReviews(), /HTTP 400/);
  const rows = api.openInitializedReviewStore_().load();
  assert.equal(rows[0].status, '過去通知済み'); assert.equal(rows[1].status, '過去通知エラー');
  assert.equal(rows[1].notifiedAt, ''); assert.equal(rows[1].messageId, '');
  assert.equal(rows[2].status, '初回対象外');
  assert.equal(api.pollPlayReviews().notified, 0); assert.equal(attempts, 2);
  fail = false;
  assert.equal(api.notifyHistoricalReviews().notified, 2); assert.equal(attempts, 4);
  assert.equal(api.notifyHistoricalReviews().notified, 0);
});

test('manual historical sends stop at the time budget and preserve remaining reviews for the next run', () => {
  const api = load(), store = memoryStore(api.mergeReviewEntries_([], [
    api.playReviewEntry_(review('first')), api.playReviewEntry_(review('second')),
  ], true));
  let checks = 0;
  const sent = [];
  const options = { store, historical: true, getEntries: () => [], hasTime: () => ++checks < 3,
    send: record => { sent.push(record.id); return String(sent.length); } };
  const first = api.processPlayReviews_(options);
  assert.equal(first.notified, 1); assert.equal(first.pending, 1); assert.equal(first.deferred, true);
  options.hasTime = () => true;
  assert.equal(api.processPlayReviews_(options).notified, 1);
  assert.deepEqual(sent, ['first', 'second']);
});

test('historical send checkpoint failure stops later deliveries and unconfirmed responses remain retryable', () => {
  const api = load(), store = memoryStore(api.mergeReviewEntries_([], [
    api.playReviewEntry_(review('first')), api.playReviewEntry_(review('second')),
  ], true));
  let sent = 0;
  store.save = () => { throw new Error('checkpoint failed'); };
  assert.throws(() => api.processPlayReviews_({ store, historical: true, getEntries: () => [], hasTime: () => true,
    send: () => { sent++; return '1'; },
  }), /checkpoint failed/);
  assert.equal(sent, 1);
  const env = gasEnvironment((_, options) => response(200, options.method === 'post' ? {} : { reviews: [review()] }));
  const integrated = load(env.services);
  integrated.initializeReviewNotifications();
  assert.throws(() => integrated.notifyHistoricalReviews(), /送信完了の確認/);
  const row = integrated.openInitializedReviewStore_().load()[0];
  assert.equal(row.status, '過去通知エラー'); assert.equal(row.messageId, ''); assert.equal(row.notifiedAt, '');
});

test('after historical delivery, CSV-to-API hydration does not duplicate the message and genuine edits still notify', () => {
  const api = load();
  const entry = api.reviewReportEntries_([reportHeaders, reportRow('old', {
    'Review Title': '', 'Review Text': '使いやすいです', 'Review Last Update Millis Since Epoch': '1700000000123', 'Star Rating': '5',
  })], reportName('202001'))[0];
  const store = memoryStore([{ ...entry, status: '過去取込', notifiedVersion: entry.version, notifiedAt: '', messageId: '' }]);
  assert.equal(api.processPlayReviews_({ store, historical: true, getEntries: () => [], hasTime: () => true,
    send: () => '123',
  }).notified, 1);
  assert.equal(store.records[0].status, '過去通知済み');
  let current = review('old', { text: '使いやすい\nです', lastModified: { seconds: '1700000000', nanos: 123456789 } });
  const normal = { store, getEntries: () => [api.playReviewEntry_(current)], hasTime: () => true,
    send: () => assert.fail('formatting differences must not produce duplicate notifications') };
  assert.equal(api.processPlayReviews_(normal).notified, 0);
  assert.equal(store.records[0].messageId, '123'); assert.equal(store.records[0].author, '投稿者');
  current = review('old', { text: '更新しました', lastModified: { seconds: '1700000001', nanos: 0 } });
  normal.send = record => { assert.match(api.reviewDiscordPayload_(record).embeds[0].title, /レビュー更新$/); return '456'; };
  assert.equal(api.processPlayReviews_(normal).notified, 1);
  assert.equal(store.records[0].status, '通知済み');
  assert.equal(api.processPlayReviews_({ store, historical: true, getEntries: () => [], hasTime: () => true,
    send: () => assert.fail('already delivered'),
  }).notified, 0);
});

test('successful Discord headers delay the next POST after checkpointing the delivered review', () => {
  const clock = testClock();
  let posts = 0, firstPostedAt = 0;
  const env = gasEnvironment((_, options) => {
    if (options.method !== 'post') return response(200, { reviews: [review('first'), review('second')] });
    if (++posts === 1) {
      firstPostedAt = clock.now;
      return response(200, { id: '1' }, { 'x-ratelimit-remaining': '0', 'X-RateLimit-Reset-After': '1.5' });
    }
    assert.equal(clock.now - firstPostedAt, 1750);
    assert.equal(env.sheets[0].values[1][0], '過去通知済み');
    assert.equal(env.sheets[0].values[1][12], '1');
    return response(200, { id: '2' }, { 'X-RateLimit-Remaining': '4' });
  });
  const api = load({ ...env.services, ...clock.services });
  api.initializeReviewNotifications();
  assert.equal(api.notifyHistoricalReviews().notified, 2);
  assert.deepEqual(clock.delays, [1750]);
  assert.equal(api.notifyHistoricalReviews().notified, 0); assert.equal(posts, 2);
});

test('a long 429 defers remaining reviews and persists cooldown across executions without resending successful rows', () => {
  const clock = testClock(), startedAt = clock.now;
  let posts = 0;
  const env = gasEnvironment((_, options) => {
    if (options.method !== 'post') return response(200, { reviews: [review('first'), review('second'), review('third')] });
    if (++posts === 2) return response(429, { retry_after: 120, global: true }, { 'rEtRy-AfTeR': '123' });
    return response(200, { id: String(posts) });
  });
  let api = load({ ...env.services, ...clock.services });
  api.initializeReviewNotifications();
  const store = api.openInitializedReviewStore_(), initial = store.load();
  initial[1].status = '過去通知エラー'; // Resume a row left by the previous implementation.
  store.saveAll(initial);
  const result = api.notifyHistoricalReviews();
  assert.equal(result.notified, 1); assert.equal(result.pending, 2); assert.equal(result.deferred, true);
  assert.equal(result.reason, 'discord_rate_limit'); assert.equal(result.retryAfterSeconds, 124);
  assert.equal(Date.parse(result.retryAt), startedAt + 123250);
  assert.equal(Number(env.properties.get('PLAY_REVIEW_DISCORD_RETRY_AT')), Date.parse(result.retryAt));
  assert.equal(store.load()[0].messageId, '1'); assert.equal(store.load()[1].status, '過去通知エラー');
  assert.equal(store.load()[1].messageId, ''); assert.deepEqual(clock.delays, []);
  api = load({ ...env.services, ...clock.services }); // New GAS execution with no in-memory cooldown.
  const early = api.notifyHistoricalReviews();
  assert.equal(early.notified, 0); assert.equal(early.pending, 2); assert.equal(early.reason, 'discord_rate_limit');
  assert.equal(posts, 2);
  clock.advance(123251);
  assert.equal(api.notifyHistoricalReviews().notified, 2); assert.equal(posts, 4);
  assert.equal(api.notifyHistoricalReviews().notified, 0);
});

test('repeated short 429 responses exhaust bounded retries and return a resumable result', () => {
  const clock = testClock();
  let posts = 0;
  const env = gasEnvironment((_, options) => {
    if (options.method !== 'post') return response(200, { reviews: [review()] });
    posts++;
    return response(429, { retry_after: 0.5 });
  });
  const api = load({ ...env.services, ...clock.services });
  api.initializeReviewNotifications();
  const result = api.notifyHistoricalReviews();
  assert.equal(posts, 4); assert.deepEqual(clock.delays, [750, 750, 750]);
  assert.equal(result.notified, 0); assert.equal(result.pending, 1); assert.equal(result.reason, 'discord_rate_limit');
  assert.equal(api.openInitializedReviewStore_().load()[0].status, '初回対象外');
});

test('429 defers near the GAS deadline instead of sleeping beyond the remaining execution budget', () => {
  const clock = testClock();
  let posts = 0;
  const env = gasEnvironment((_, options) => {
    if (options.method !== 'post') return response(200, { reviews: [review()] });
    posts++; clock.advance(239000);
    return response(429, { retry_after: 5 });
  });
  const api = load({ ...env.services, ...clock.services });
  api.initializeReviewNotifications();
  const result = api.notifyHistoricalReviews();
  assert.equal(result.reason, 'discord_rate_limit'); assert.equal(result.pending, 1);
  assert.equal(posts, 1); assert.deepEqual(clock.delays, []);
});

test('non-JSON 429 responses honor headers; missing or malformed delays back off without leaking the response', () => {
  for (const scenario of [
    { headers: { 'RETRY-AFTER': '1.25' }, expectedSleep: 1500 },
    { headers: { 'Retry-After': new Date(1900000002000).toUTCString() }, expectedSleep: 2250 },
    { headers: {}, expectedSleep: null },
    { headers: { 'Retry-After': '-1' }, body: { retry_after: false }, expectedSleep: null },
  ]) {
    const clock = testClock(), logs = [];
    let posts = 0;
    const env = gasEnvironment((_, options) => {
      if (options.method !== 'post') return response(200, { reviews: [review()] });
      if (++posts > 1) return response(200, { id: '2' });
      return { ...response(429, scenario.body, scenario.headers), getContentText: () => scenario.body
        ? JSON.stringify(scenario.body) : '<html>secret-response</html>' };
    });
    const api = load({ ...env.services, ...clock.services, console: { log: value => logs.push(value) } });
    api.initializeReviewNotifications();
    const result = api.notifyHistoricalReviews();
    if (scenario.expectedSleep === null) {
      assert.equal(result.reason, 'discord_rate_limit'); assert.equal(result.retryAfterSeconds, 61);
      assert.equal(result.notified, 0); assert.equal(posts, 1); assert.deepEqual(clock.delays, []);
    } else {
      assert.equal(result.notified, 1); assert.equal(posts, 2); assert.deepEqual(clock.delays, [scenario.expectedSleep]);
    }
    assert.ok(logs.every(value => !value.includes('secret-response')));
  }
});

test('a long reset on a successful POST preserves its message ID before deferring the next review', () => {
  const clock = testClock();
  let posts = 0;
  const env = gasEnvironment((_, options) => {
    if (options.method !== 'post') return response(200, { reviews: [review('first'), review('second')] });
    posts++;
    return response(200, { id: '1' }, { 'X-RateLimit-Remaining': 0, 'X-RateLimit-Reset': (clock.now + 120000) / 1000 });
  });
  const api = load({ ...env.services, ...clock.services });
  api.initializeReviewNotifications();
  const result = api.notifyHistoricalReviews();
  assert.equal(result.notified, 1); assert.equal(result.pending, 1); assert.equal(result.reason, 'discord_rate_limit');
  assert.equal(posts, 1); assert.equal(api.openInitializedReviewStore_().load()[0].messageId, '1');
});

test('failure to save the cooldown cache does not discard a confirmed Discord delivery', () => {
  const clock = testClock();
  const env = gasEnvironment((_, options) => options.method === 'post'
    ? response(200, { id: '123' }, { 'X-RateLimit-Remaining': 0, 'X-RateLimit-Reset-After': 1 })
    : response(200, { reviews: [review()] }));
  const api = load({ ...env.services, ...clock.services });
  api.initializeReviewNotifications();
  const properties = env.services.PropertiesService.getScriptProperties();
  env.services.PropertiesService.getScriptProperties = () => ({ ...properties, setProperty: (key, value) => {
    if (key === 'PLAY_REVIEW_DISCORD_RETRY_AT') throw new Error('property-write-failed');
    return properties.setProperty(key, value);
  } });
  assert.equal(api.notifyHistoricalReviews().notified, 1);
  const row = api.openInitializedReviewStore_().load()[0];
  assert.equal(row.status, '過去通知済み'); assert.equal(row.messageId, '123');
  assert.equal(api.notifyHistoricalReviews().notified, 0);
});

test('regular review polling also preserves a rate-limited pending row and resumes after cooldown', () => {
  const clock = testClock();
  let initialized = false, posts = 0;
  const env = gasEnvironment((_, options) => {
    if (options.method !== 'post') return response(200, { reviews: initialized ? [review()] : [] });
    return ++posts === 1 ? response(429, { retry_after: 120 }) : response(200, { id: '2' });
  });
  const api = load({ ...env.services, ...clock.services });
  api.initializeReviewNotifications(); initialized = true;
  const result = api.pollPlayReviews();
  assert.equal(result.reason, 'discord_rate_limit'); assert.equal(result.pending, 1); assert.equal(result.notified, 0);
  const row = api.openInitializedReviewStore_().load()[0];
  assert.equal(row.status, '未通知'); assert.equal(row.notifiedVersion, ''); assert.equal(row.messageId, '');
  clock.advance(120251);
  assert.equal(api.pollPlayReviews().notified, 1);
  assert.equal(api.openInitializedReviewStore_().load()[0].status, '通知済み');
  assert.equal(api.pollPlayReviews().notified, 0); assert.equal(posts, 2);
});
