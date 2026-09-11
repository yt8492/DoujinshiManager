const PLAY_REVIEWS = Object.freeze({
  packageName: 'com.yt8492.doujinshimanager',
  appName: 'ベッドの下',
  sheetName: 'Playレビュー',
  stateKey: 'PLAY_REVIEW_STATE',
  discordRetryAtKey: 'PLAY_REVIEW_DISCORD_RETRY_AT',
  budgetMs: 240000, // Leave headroom below Apps Script's six-minute limit.
  headers: [
    '状態', '評価', 'レビュー本文', '投稿者', 'アプリバージョン',
    'レビュー更新日時', '最終通知日時', 'レビューURL', 'レビューID',
    '言語', '取得内容ハッシュ', '通知済みハッシュ', 'DiscordメッセージID',
  ],
});

class ReviewNotificationError extends Error {}

class DiscordRateLimitError extends ReviewNotificationError {
  constructor(retryAt) {
    super('Discordの送信回数制限で待機中です。' + new Date(retryAt).toISOString() + ' 以降に再実行してください。');
    this.retryAt = retryAt;
  }
}

let discordNextRequestAt_ = 0;

/** 初回は月別レポートも取り込む。中断時は同じ関数で再開。Discordには送信しない。 */
function initializeReviewNotifications() {
  return withReviewLock_(function () {
    const properties = PropertiesService.getScriptProperties();
    const deadline = Date.now() + PLAY_REVIEWS.budgetMs;
    if (properties.getProperty(PLAY_REVIEWS.stateKey)) {
      const state = readReviewState_();
      if (!state.initializing) {
        throw new ReviewNotificationError('初期化済みです。過去分の追加は importHistoricalReviews、通常の確認は pollPlayReviews を実行してください。');
      }
      assertReviewImportProgress_(state);
      const store = openInitializedReviewStore_(true);
      const reports = listReviewReports_(reviewReportBucket_(), deadline);
      store.saveAll(mergeReviewEntries_(store.load(), fetchPlayReviews_(deadline), true));
      return importReviewReports_(state, store, reports, deadline);
    }
    const spreadsheet = openReviewSpreadsheet_();
    if (spreadsheet.getSheetByName(PLAY_REVIEWS.sheetName)) {
      throw new ReviewNotificationError('同名の履歴シートが存在します。初期化で上書きはしません。');
    }
    const bucket = reviewReportBucket_();
    // Validate access before creating the sheet. GET never posts a test message.
    requestReviewJson_(discordWebhookUrl_(), { method: 'get' }, 'Discord', deadline);
    const reports = listReviewReports_(bucket, deadline);
    const entries = fetchPlayReviews_(deadline);
    assertReviewTime_(deadline);
    const sheet = spreadsheet.insertSheet(PLAY_REVIEWS.sheetName);
    const store = createReviewSheetStore_(sheet);
    const records = mergeReviewEntries_([], entries, true);
    configureReviewSheet_(sheet);
    store.saveAll(records);
    const state = {
      schemaVersion: 1, packageName: PLAY_REVIEWS.packageName,
      spreadsheetId: spreadsheet.getId(), sheetId: sheet.getSheetId(),
      initializing: true,
      historyImport: { bucket: bucket, lastReport: '', completed: false },
    };
    saveReviewState_(state);
    return importReviewReports_(state, store, reports, deadline);
  });
}

/** 旧版で初期化済みの場合の追加取込。既存行・通知状態は保持し、直近1週間は通常通知に任せる。 */
function importHistoricalReviews() {
  return withReviewLock_(function () {
    const deadline = Date.now() + PLAY_REVIEWS.budgetMs;
    const state = readReviewState_();
    const store = openInitializedReviewStore_();
    const bucket = reviewReportBucket_();
    if (state.historyImport) assertReviewImportProgress_(state);
    if (state.historyImport && state.historyImport.completed) {
      throw new ReviewNotificationError('過去レビューの取り込みは完了済みです。');
    }
    const reports = listReviewReports_(bucket, deadline);
    if (!state.historyImport) {
      state.historyImport = {
        bucket: bucket, lastReport: '', completed: false,
        beforeMillis: Date.now() - 7 * 24 * 60 * 60 * 1000,
      };
      saveReviewState_(state);
    }
    return importReviewReports_(state, store, reports, deadline);
  });
}

/** 時間主導トリガーから実行。未通知分を保存してから順にDiscordへ送信する。 */
function pollPlayReviews() {
  return withReviewLock_(function () {
    const deadline = Date.now() + PLAY_REVIEWS.budgetMs;
    const store = openInitializedReviewStore_();
    const webhook = discordWebhookUrl_();
    const result = processPlayReviews_({
      store: store,
      getEntries: function () { return fetchPlayReviews_(deadline); },
      hasTime: function () { return Date.now() < deadline; },
      send: function (record) {
        return sendReviewToDiscord_(record, webhook, deadline, false);
      },
    });
    console.log(JSON.stringify(result));
    return result;
  });
}

/** 手動実行。保存済みの初回対象外・過去取込をDiscordに送る。送信済みの行は再送しない。 */
function notifyHistoricalReviews() {
  return withReviewLock_(function () {
    const deadline = Date.now() + PLAY_REVIEWS.budgetMs;
    const store = openInitializedReviewStore_();
    const webhook = discordWebhookUrl_();
    const result = processPlayReviews_({
      store: store, historical: true,
      getEntries: function () { return []; }, // Send the saved snapshot without refreshing Play data.
      hasTime: function () { return Date.now() < deadline; },
      send: function (record) {
        return sendReviewToDiscord_(record, webhook, deadline, true);
      },
    });
    console.log(JSON.stringify(result));
    if (result.reason === 'discord_rate_limit') {
      console.log('Discordの送信回数制限で一時停止しました。' + result.retryAfterSeconds
        + '秒後以降に notifyHistoricalReviews を再実行してください。送信済みの行は保持しています。');
    } else if (result.deferred) {
      console.log('残りの過去レビューは notifyHistoricalReviews を再実行すると続きから通知します。');
    }
    return result;
  });
}

function sendReviewToDiscord_(record, webhook, deadline, historical) {
  const message = requestReviewJson_(webhook, {
    method: 'post', contentType: 'application/json',
    payload: JSON.stringify(reviewDiscordPayload_(record, historical)),
  }, 'Discord', deadline);
  if (!message || typeof message.id !== 'string' || !/^\d+$/.test(message.id)) {
    throw new ReviewNotificationError('Discordから送信完了の確認を取得できませんでした。');
  }
  return message.id;
}

/** 手動の接続テスト。Discordへテスト文を1件送る。レビュー履歴は変更しない。 */
function sendTestNotification() {
  return withReviewLock_(function () {
    openInitializedReviewStore_().load();
    const message = requestReviewJson_(discordWebhookUrl_(), {
      method: 'post', contentType: 'application/json',
      payload: JSON.stringify({
        content: PLAY_REVIEWS.appName + '：Playレビュー通知の接続テストです。',
        allowed_mentions: { parse: [] },
      }),
    }, 'Discord', Date.now() + PLAY_REVIEWS.budgetMs);
    if (!message || typeof message.id !== 'string' || !/^\d+$/.test(message.id)) {
      throw new ReviewNotificationError('Discordから送信完了の確認を取得できませんでした。');
    }
    console.log('接続テストを送信しました。Discordで到着を確認してください。');
    return { messageId: message.id };
  });
}

/** 初期化後に一度実行。同じアカウントの同名トリガーを重複作成しない。 */
function installHourlyTrigger() {
  return withReviewLock_(function () {
    openInitializedReviewStore_().load();
    const existing = ScriptApp.getProjectTriggers().filter(function (trigger) {
      return trigger.getHandlerFunction() === 'pollPlayReviews';
    });
    if (existing.length) {
      console.log('pollPlayReviews のトリガーは既に存在します。');
      return;
    }
    ScriptApp.newTrigger('pollPlayReviews').timeBased().everyHours(1).create();
    console.log('1時間ごとのレビュー確認を開始しました。');
  });
}

/** このアカウントが作成した通知トリガーのみ停止。履歴は保持する。 */
function removeReviewTriggers() {
  return withReviewLock_(function () {
    ScriptApp.getProjectTriggers().forEach(function (trigger) {
      if (trigger.getHandlerFunction() === 'pollPlayReviews') ScriptApp.deleteTrigger(trigger);
    });
    console.log('このアカウントのレビュー確認トリガーを停止しました。');
  });
}

function withReviewLock_(operation) {
  const lock = LockService.getScriptLock();
  if (!lock.tryLock(1000)) {
    console.log('別のレビュー処理が実行中のため、今回はスキップしました。');
    return { skipped: true };
  }
  try {
    return operation();
  } catch (error) {
    // UrlFetch/auth errors can include credentials or a webhook URL. Never log them.
    if (error instanceof ReviewNotificationError) throw error;
    throw new ReviewNotificationError('レビュー処理に失敗しました。Googleの認可、シートのアクセス権と構成を確認してください。');
  } finally {
    lock.releaseLock();
  }
}

function openReviewSpreadsheet_() {
  const id = PropertiesService.getScriptProperties().getProperty('SPREADSHEET_ID');
  const spreadsheet = id ? SpreadsheetApp.openById(id) : SpreadsheetApp.getActiveSpreadsheet();
  if (!spreadsheet) {
    throw new ReviewNotificationError('スプレッドシートからGASを開くか、SPREADSHEET_IDを設定してください。');
  }
  return spreadsheet;
}

function readReviewState_() {
  const raw = PropertiesService.getScriptProperties().getProperty(PLAY_REVIEWS.stateKey);
  let state;
  try { state = JSON.parse(raw); } catch (_) { /* Validated below. */ }
  if (!state || state.schemaVersion !== 1 || state.packageName !== PLAY_REVIEWS.packageName
    || !state.spreadsheetId || !Number.isInteger(state.sheetId)) {
    throw new ReviewNotificationError('初期化情報がありません、または破損しています。初回は initializeReviewNotifications を実行してください。');
  }
  return state;
}

function saveReviewState_(state) {
  PropertiesService.getScriptProperties().setProperty(PLAY_REVIEWS.stateKey, JSON.stringify(state));
}

function openInitializedReviewStore_(allowInitializing) {
  const state = readReviewState_();
  if (state.initializing && !allowInitializing) {
    throw new ReviewNotificationError('過去レビューの初回取り込み中です。initializeReviewNotifications を再実行して完了させてください。');
  }
  const configuredId = PropertiesService.getScriptProperties().getProperty('SPREADSHEET_ID');
  if (configuredId && configuredId !== state.spreadsheetId) {
    throw new ReviewNotificationError('SPREADSHEET_IDが初期化したシートと異なります。元の設定へ戻してください。');
  }
  const spreadsheet = SpreadsheetApp.openById(state.spreadsheetId);
  const sheet = spreadsheet.getSheets().find(function (candidate) {
    return candidate.getSheetId() === state.sheetId;
  });
  if (!sheet) throw new ReviewNotificationError('通知履歴シートがありません。履歴を復元してください。');
  return createReviewSheetStore_(sheet);
}

function assertReviewTime_(deadline) {
  if (Date.now() >= deadline) {
    throw new ReviewNotificationError('実行時間の上限に近づいたため停止しました。未通知分は次回確認します。');
  }
}

function requestReviewJson_(url, options, service, deadline) {
  const response = requestReviewResponse_(url, options, service, deadline);
  let body;
  try { body = JSON.parse(response.getContentText()); } catch (_) { /* Validated below. */ }
  if (!body) throw new ReviewNotificationError(service + ': JSON応答が不正です。');
  return body;
}

function requestReviewResponse_(url, options, service, deadline) {
  for (let attempt = 0; attempt < 4; attempt++) {
    assertReviewTime_(deadline);
    if (service === 'Discord') waitForDiscordRequest_(deadline);
    let response;
    try {
      response = UrlFetchApp.fetch(url, Object.assign({}, options, {
        muteHttpExceptions: true, followRedirects: false,
      }));
    } catch (_) {
      throw new ReviewNotificationError(service + ': 通信に失敗しました。');
    }
    const status = response.getResponseCode();
    const headers = normalizedReviewHeaders_(response.getHeaders());
    if (status >= 200 && status < 300) {
      if (service === 'Discord' && reviewDelaySeconds_(headers['x-ratelimit-remaining']) === 0) {
        const resetAfter = discordResetAfter_(headers);
        rememberDiscordRetryAt_(Date.now() + Math.ceil((resetAfter === null ? 1 : resetAfter) * 1000) + 250);
      }
      return response;
    }
    let body;
    try { body = JSON.parse(response.getContentText()); } catch (_) { /* Validated below. */ }
    if (service === 'Discord' && status === 429) {
      const seconds = discordRetryAfter_(body, headers);
      rememberDiscordRetryAt_(Date.now() + Math.ceil(seconds * 1000) + 250);
      if (attempt < 3) continue; // The next attempt waits, or defers if the delay exceeds this run's budget.
      throw new DiscordRateLimitError(discordNextRequestAt_);
    }
    // A Discord 5xx or timeout may have occurred after delivery; only retry rejected POSTs (429).
    const retryable = status === 429 || (options.method === 'get' && status >= 500);
    if (retryable && attempt < 3) {
      const seconds = Number(body && body.retry_after != null ? body.retry_after
        : headers['retry-after'] || Math.pow(2, attempt));
      const delay = Math.ceil(seconds * 1000) + 250;
      if (Number.isFinite(seconds) && seconds >= 0 && delay <= 60000 && Date.now() + delay < deadline) {
        Utilities.sleep(delay);
        continue;
      }
    }
    throw new ReviewNotificationError(service + ': HTTP ' + status + '。権限・設定またはサービスの状態を確認してください。');
  }
}

function normalizedReviewHeaders_(headers) {
  const normalized = {};
  Object.keys(headers).forEach(function (key) {
    normalized[key.toLowerCase()] = Array.isArray(headers[key]) ? headers[key][0] : headers[key];
  });
  return normalized;
}

function reviewDelaySeconds_(value) {
  if ((typeof value !== 'number' && typeof value !== 'string') || String(value).trim() === '') return null;
  const number = Number(value);
  return Number.isFinite(number) && number >= 0 && number <= 8640000000 ? number : null;
}

function discordResetAfter_(headers) {
  const seconds = reviewDelaySeconds_(headers['x-ratelimit-reset-after']);
  if (seconds !== null) return seconds;
  const epoch = reviewDelaySeconds_(headers['x-ratelimit-reset']);
  return epoch === null ? null : Math.max(0, epoch - Date.now() / 1000);
}

function discordRetryAfter_(body, headers) {
  let headerDelay = reviewDelaySeconds_(headers['retry-after']);
  if (headerDelay === null && /^[A-Za-z]{3}, /.test(String(headers['retry-after']))) {
    const date = Date.parse(String(headers['retry-after']));
    if (Number.isFinite(date)) headerDelay = Math.max(0, (date - Date.now()) / 1000);
  }
  const candidates = [reviewDelaySeconds_(body ? body.retry_after : undefined), headerDelay, discordResetAfter_(headers)]
    .filter(function (seconds) { return seconds !== null; });
  // A non-JSON/undocumented 429 still requires backing off; never treat missing retry_after as zero.
  return candidates.length ? Math.max.apply(null, candidates) : 60;
}

function rememberDiscordRetryAt_(retryAt) {
  discordNextRequestAt_ = Math.max(discordNextRequestAt_, retryAt);
  try {
    PropertiesService.getScriptProperties().setProperty(PLAY_REVIEWS.discordRetryAtKey, String(discordNextRequestAt_));
  } catch (_) {
    // A delivered POST must still be returned and checkpointed if this optional cooldown cache fails.
    console.log('Discordの待機時刻を保存できませんでした。この実行中は待機時刻を保持します。');
  }
}

function waitForDiscordRequest_(deadline) {
  const raw = PropertiesService.getScriptProperties().getProperty(PLAY_REVIEWS.discordRetryAtKey);
  const saved = raw === null ? 0 : Number(raw);
  if (!Number.isSafeInteger(saved) || saved < 0 || !Number.isFinite(new Date(saved).getTime())) {
    throw new ReviewNotificationError('Discordの待機時刻の内部設定が不正です。');
  }
  discordNextRequestAt_ = Math.max(discordNextRequestAt_, saved);
  const delay = discordNextRequestAt_ - Date.now();
  if (delay <= 0) return;
  if (delay > 60000 || Date.now() + delay + 1000 >= deadline) {
    throw new DiscordRateLimitError(discordNextRequestAt_);
  }
  Utilities.sleep(delay);
  assertReviewTime_(deadline);
}

function reviewReportBucket_() {
  const raw = (PropertiesService.getScriptProperties().getProperty('PLAY_REPORT_BUCKET') || '').trim();
  const match = raw.match(/^(?:gs:\/\/)?(pubsite_prod_[a-zA-Z0-9_-]+)(?:\/[^?#]*)?$/);
  if (!match) {
    throw new ReviewNotificationError('PLAY_REPORT_BUCKETにPlay Consoleのレポート用バケット名、またはコピーしたCloud Storage URIを設定してください。');
  }
  return match[1];
}

function reviewReportPrefix_() {
  return 'reviews/reviews_' + PLAY_REVIEWS.packageName + '_';
}

function isReviewReportName_(name) {
  const prefix = reviewReportPrefix_();
  return typeof name === 'string' && name.indexOf(prefix) === 0
    && /^\d{4}(0[1-9]|1[0-2])\.csv$/.test(name.slice(prefix.length));
}

function listReviewReports_(bucket, deadline) {
  const names = new Set(), tokens = new Set();
  let token = '';
  do {
    let url = 'https://storage.googleapis.com/storage/v1/b/' + encodeURIComponent(bucket)
      + '/o?prefix=' + encodeURIComponent(reviewReportPrefix_())
      + '&fields=' + encodeURIComponent('items(name),nextPageToken') + '&maxResults=1000';
    if (token) url += '&pageToken=' + encodeURIComponent(token);
    const page = requestReviewJson_(url, {
      method: 'get', headers: { Authorization: 'Bearer ' + ScriptApp.getOAuthToken() },
    }, 'Google Playレポート', deadline);
    if (page.items !== undefined && !Array.isArray(page.items)) {
      throw new ReviewNotificationError('Google Playレポートの一覧が不正です。');
    }
    (page.items || []).forEach(function (item) {
      if (isReviewReportName_(item.name)) names.add(item.name);
    });
    token = page.nextPageToken;
    if (token && (typeof token !== 'string' || tokens.has(token))) {
      throw new ReviewNotificationError('Google Playレポートのページトークンが不正です。');
    }
    tokens.add(token);
  } while (token);
  return Array.from(names).sort();
}

function fetchReviewReport_(bucket, name, deadline) {
  const url = 'https://storage.googleapis.com/storage/v1/b/' + encodeURIComponent(bucket)
    + '/o/' + encodeURIComponent(name) + '?alt=media';
  const response = requestReviewResponse_(url, {
    method: 'get', headers: { Authorization: 'Bearer ' + ScriptApp.getOAuthToken() },
  }, 'Google Playレポート', deadline);
  const blob = response.getBlob(), bytes = blob.getBytes();
  // Official exports are UTF-16. Detect BOM and allow UTF-8 exports as well.
  const first = bytes[0] & 255, second = bytes[1] & 255;
  const charset = first === 255 && second === 254 ? 'UTF-16LE'
    : first === 254 && second === 255 ? 'UTF-16BE'
      : second === 0 ? 'UTF-16LE' : first === 0 ? 'UTF-16BE' : 'UTF-8';
  const csv = blob.getDataAsString(charset).replace(/^\uFEFF/, '');
  let rows;
  try { rows = Utilities.parseCsv(csv); } catch (_) {
    throw new ReviewNotificationError('Google PlayレポートのCSVを解析できませんでした。');
  }
  return reviewReportEntries_(rows, name);
}

function reviewHash_(value) {
  return Utilities.computeDigest(Utilities.DigestAlgorithm.SHA_256,
    JSON.stringify(value), Utilities.Charset.UTF_8)
    .map(function (byte) { return ('0' + (byte & 255).toString(16)).slice(-2); }).join('');
}

function reviewReportEntries_(rows, name) {
  const headers = rows[0] || [];
  if (new Set(headers).size !== headers.length
    || ['Package Name', 'Review Submit Millis Since Epoch', 'Star Rating'].some(function (key) {
      return headers.indexOf(key) < 0;
    })) {
    throw new ReviewNotificationError('Google PlayレポートのCSV見出しが不正です。英語の月別レビューレポートを確認してください。');
  }
  const entries = [];
  rows.slice(1).forEach(function (row, index) {
    if (row.every(function (cell) { return cell === ''; })) return;
    const cell = function (key) { const position = headers.indexOf(key); return position < 0 ? '' : row[position]; };
    const badRow = function () {
      return new ReviewNotificationError('Google PlayレポートのCSV ' + (index + 2) + '行目の項目・日時・評価が不正です。');
    };
    if (row.length !== headers.length || cell('Package Name') !== PLAY_REVIEWS.packageName) throw badRow();
    const millisText = cell('Review Last Update Millis Since Epoch') || cell('Review Submit Millis Since Epoch');
    const millis = Number(millisText), rating = Number(cell('Star Rating'));
    if (!/^\d+$/.test(millisText) || !Number.isSafeInteger(millis) || millis <= 0
      || !Number.isFinite(new Date(millis).getTime())
      || !Number.isInteger(rating) || rating < 1 || rating > 5) throw badRow();
    const text = [cell('Review Title'), cell('Review Text')].filter(Boolean).join('\t');
    if (!text.trim()) return; // Ratings without a written review are outside the notification scope.
    const link = cell('Review Link');
    const safeLink = /^https:\/\/(?:play\.google\.com|playconsole\.google\.com)\//.test(link) ? link : '';
    const match = safeLink.match(/[?&#]reviewId=([^&#]+)/i);
    let id = '';
    try { id = match ? decodeURIComponent(match[1].replace(/\+/g, ' ')) : ''; } catch (_) { /* Archive below. */ }
    // Missing IDs must not cause distinct users' similar reviews to collapse into one row.
    if (!id || /\s/.test(id)) id = 'csv:' + reviewHash_([name, index + 2, row]);
    const entry = playReviewEntry_({ reviewId: id, authorName: '不明（CSVに記載なし）', comments: [{ userComment: {
      starRating: rating, text: text, lastModified: {
        seconds: Math.floor(millis / 1000), nanos: (millis % 1000) * 1e6,
      }, appVersionName: cell('App Version Name'), reviewerLanguage: cell('Reviewer Language'),
    } }] });
    if (id.indexOf('csv:') === 0) entry.url = safeLink || 'https://play.google.com/store/apps/details?id=' + PLAY_REVIEWS.packageName;
    entries.push(entry);
  });
  return entries;
}

function assertReviewImportProgress_(state) {
  const progress = state.historyImport;
  if (!progress || progress.bucket !== reviewReportBucket_()
    || typeof progress.completed !== 'boolean' || (state.initializing && progress.completed)
    || typeof progress.lastReport !== 'string' || (progress.lastReport && !isReviewReportName_(progress.lastReport))
    || (progress.beforeMillis !== undefined && (!Number.isSafeInteger(progress.beforeMillis) || progress.beforeMillis <= 0))) {
    throw new ReviewNotificationError('過去レビューの取り込み設定が変更・破損しています。元の設定へ戻してください。');
  }
}

function importReviewReports_(state, store, reports, deadline) {
  assertReviewImportProgress_(state);
  const progress = state.historyImport;
  let records = store.load(), processed = 0;
  const remaining = reports.filter(function (name) { return name > progress.lastReport; });
  for (const name of remaining) {
    // Keep time for parsing, saving the month and checkpointing its name.
    if (Date.now() + 45000 >= deadline) break;
    const entries = fetchReviewReport_(progress.bucket, name, deadline);
    const byId = new Map(records.map(function (record) { return [record.id, record]; }));
    entries.forEach(function (entry) {
      if (progress.beforeMillis !== undefined && entry.modifiedAt.getTime() >= progress.beforeMillis) return;
      const previous = byId.get(entry.id);
      // Never replace API content or an existing notification checkpoint with lagging CSV data.
      if (previous && (!isHistoricalReview_(previous) || previous.version !== previous.notifiedVersion
        || previous.modifiedAt.getTime() >= entry.modifiedAt.getTime())) return;
      byId.set(entry.id, Object.assign({}, entry, {
        status: entry.id.indexOf('csv:') === 0 ? '過去取込（ID不明）' : '過去取込',
        notifiedVersion: entry.version, notifiedAt: '', messageId: '',
      }));
    });
    assertReviewTime_(deadline);
    records = Array.from(byId.values());
    store.saveAll(records);
    progress.lastReport = name;
    saveReviewState_(state); // Advance only after the month is durably saved; retries are idempotent.
    processed++;
  }
  const complete = processed === remaining.length;
  if (complete) {
    progress.completed = true;
    state.initializing = false;
    saveReviewState_(state);
  }
  const result = {
    initialized: records.length, reportsProcessed: processed, remainingReports: remaining.length - processed,
    unidentified: records.filter(function (record) { return record.id.indexOf('csv:') === 0; }).length,
    complete: complete, notified: 0,
  };
  console.log(JSON.stringify(result));
  if (!complete) console.log('時間制限に備えて保存しました。同じ関数を再実行すると続きから取り込みます。');
  return result;
}

function isHistoricalReview_(record) {
  return record.status === '過去取込' || record.status === '過去取込（ID不明）';
}

function fetchPlayReviews_(deadline) {
  const accessToken = ScriptApp.getOAuthToken();
  const entries = new Map();
  const tokens = new Set();
  let token = '';
  do {
    let url = 'https://androidpublisher.googleapis.com/androidpublisher/v3/applications/'
      + PLAY_REVIEWS.packageName + '/reviews?maxResults=100';
    if (token) url += '&token=' + encodeURIComponent(token);
    const page = requestReviewJson_(url, {
      method: 'get', headers: { Authorization: 'Bearer ' + accessToken },
    }, 'Google Play', deadline);
    if (page.reviews !== undefined && !Array.isArray(page.reviews)) {
      throw new ReviewNotificationError('Google Playのレビュー一覧が不正です。');
    }
    (page.reviews || []).forEach(function (review) {
      const entry = playReviewEntry_(review);
      if (!entry) return;
      const previous = entries.get(entry.id);
      if (!previous || compareReviewTime_(entry, previous) > 0) entries.set(entry.id, entry);
    });
    token = page.tokenPagination && page.tokenPagination.nextPageToken;
    if (token && tokens.has(token)) throw new ReviewNotificationError('Google Playのページトークンが重複しました。');
    tokens.add(token);
  } while (token);
  return Array.from(entries.values()).sort(compareReviewTime_);
}

function compareReviewTime_(a, b) {
  return a.seconds - b.seconds || a.nanos - b.nanos;
}

function playReviewEntry_(review) {
  const users = (review.comments || []).filter(function (comment) { return comment.userComment; })
    .map(function (comment) { return comment.userComment; });
  if (!users.length) return null;
  const user = users.sort(function (a, b) {
    return Number(b.lastModified && b.lastModified.seconds) - Number(a.lastModified && a.lastModified.seconds)
      || Number(b.lastModified && b.lastModified.nanos || 0) - Number(a.lastModified && a.lastModified.nanos || 0);
  })[0];
  const seconds = Number(user.lastModified && user.lastModified.seconds);
  const nanos = Number(user.lastModified && user.lastModified.nanos || 0);
  if (typeof review.reviewId !== 'string' || !review.reviewId
    || !Number.isSafeInteger(seconds) || seconds <= 0 || !Number.isInteger(nanos) || nanos < 0 || nanos >= 1e9
    || !Number.isInteger(user.starRating) || user.starRating < 1 || user.starRating > 5
    || typeof user.text !== 'string') {
    throw new ReviewNotificationError('Google Playのレビュー内容が不正です。');
  }
  return {
    id: review.reviewId, rating: user.starRating, text: user.text,
    author: review.authorName || '匿名', appVersion: user.appVersionName || '不明',
    language: user.reviewerLanguage || '不明', seconds: seconds, nanos: nanos,
    modifiedAt: new Date(seconds * 1000 + Math.floor(nanos / 1e6)),
    version: reviewHash_([seconds, nanos, user.starRating, user.text]),
    url: 'https://play.google.com/store/apps/details?id=' + PLAY_REVIEWS.packageName
      + '&reviewId=' + encodeURIComponent(review.reviewId),
  };
}

function mergeReviewEntries_(records, entries, initialize) {
  const byId = new Map(records.map(function (record) { return [record.id, record]; }));
  entries.forEach(function (entry) {
    const previous = byId.get(entry.id);
    // Keep queued reviews even after they leave the Play API's one-week window.
    if (previous && previous.modifiedAt.getTime() > entry.modifiedAt.getTime()) return;
    // CSV strips newlines and truncates timestamps to milliseconds; first API hydration is not an edit.
    const sameImported = previous && (isHistoricalReview_(previous)
      || previous.status === '過去通知エラー' || previous.status === '過去通知済み')
      && previous.version === previous.notifiedVersion && previous.modifiedAt.getTime() === entry.modifiedAt.getTime()
      && previous.rating === entry.rating && previous.text.replace(/\s/g, '') === entry.text.replace(/\s/g, '');
    byId.set(entry.id, Object.assign({}, previous, entry, {
      notifiedVersion: initialize || sameImported ? entry.version : previous && previous.notifiedVersion || '',
      notifiedAt: previous && previous.notifiedAt || '',
      messageId: previous && previous.messageId || '',
      status: initialize ? '初回対象外'
        : previous && (sameImported || previous.notifiedVersion === entry.version) ? previous.status : '未通知',
    }));
  });
  return Array.from(byId.values());
}

function processPlayReviews_(options) {
  const previous = options.store.load();
  const entries = options.getEntries(); // Do not mutate history after only a partial API response.
  const records = mergeReviewEntries_(previous, entries, false);
  if (!options.hasTime()) return { notified: 0, deferred: true };
  options.store.saveAll(records); // Persist pending content before any external send.
  let notified = 0;
  const pending = records.filter(function (record) {
    if (!options.historical) return record.version !== record.notifiedVersion;
    return (record.status === '初回対象外' || isHistoricalReview_(record) || record.status === '過去通知エラー')
      && record.version === record.notifiedVersion && !record.notifiedAt && !record.messageId;
  })
    .sort(function (a, b) { return a.modifiedAt.getTime() - b.modifiedAt.getTime(); });
  for (const record of pending) {
    if (!options.hasTime()) return { notified: notified, pending: pending.length - notified, deferred: true };
    let messageId;
    try {
      messageId = options.send(record);
    } catch (error) {
      if (error instanceof DiscordRateLimitError) {
        // No delivery was accepted. Keep this row eligible and retain all prior successful checkpoints.
        return {
          notified: notified, pending: pending.length - notified, deferred: true, reason: 'discord_rate_limit',
          retryAfterSeconds: Math.max(0, Math.ceil((error.retryAt - Date.now()) / 1000)),
          retryAt: new Date(error.retryAt).toISOString(),
        };
      }
      record.status = options.historical ? '過去通知エラー' : '送信エラー';
      options.store.save(record);
      throw error;
    }
    record.notifiedVersion = record.version;
    record.notifiedAt = new Date();
    record.messageId = messageId;
    record.status = options.historical ? '過去通知済み' : '通知済み';
    options.store.save(record); // Stop on checkpoint failure before sending later reviews.
    notified++;
  }
  return { notified: notified, pending: 0 };
}

function sheetLiteral_(value) {
  // Sheets interprets '=' as a formula and a leading apostrophe as an escape.
  const text = String(value);
  return /^[=']/.test(text) ? "'" + text : text;
}

function reviewRecordRow_(record) {
  return [record.status, record.rating, record.text, record.author, record.appVersion,
    record.modifiedAt, record.notifiedAt, record.url, record.id, record.language,
    record.version, record.notifiedVersion, record.messageId]
    .map(function (value) { return typeof value === 'string' ? sheetLiteral_(value) : value; });
}

function createReviewSheetStore_(sheet) {
  let rowById = new Map();
  return {
    load: function () {
      if (sheet.getLastRow() < 1 || JSON.stringify(sheet.getRange(1, 1, 1, 13).getValues()[0])
        !== JSON.stringify(PLAY_REVIEWS.headers)) {
        throw new ReviewNotificationError('通知履歴の見出しが不正です。列構成を復元してください。');
      }
      rowById = new Map();
      if (sheet.getLastRow() === 1) return [];
      const range = sheet.getRange(2, 1, sheet.getLastRow() - 1, 13);
      if (range.getFormulas().some(function (row) { return row.some(Boolean); })) {
        throw new ReviewNotificationError('通知履歴に数式があります。元の値に復元してください。');
      }
      return range.getValues().map(function (row, index) {
        if (!row[8] || typeof row[8] !== 'string' || rowById.has(row[8])
          || !(row[5] instanceof Date) || !Number.isFinite(row[5].getTime())
          || !Number.isInteger(row[1]) || row[1] < 1 || row[1] > 5
          || !/^[a-f0-9]{64}$/.test(row[10]) || (row[11] !== '' && !/^[a-f0-9]{64}$/.test(row[11]))) {
          throw new ReviewNotificationError('通知履歴が破損しています。空行・重複ID・日付・ハッシュを確認してください。');
        }
        rowById.set(row[8], index + 2);
        return {
          status: row[0], rating: row[1], text: String(row[2]), author: String(row[3]), appVersion: String(row[4]),
          modifiedAt: row[5], notifiedAt: row[6], url: String(row[7]), id: row[8], language: String(row[9]),
          version: row[10], notifiedVersion: row[11], messageId: String(row[12]),
        };
      });
    },
    saveAll: function (records) {
      if (records.length + 1 > sheet.getMaxRows()) {
        sheet.insertRowsAfter(sheet.getMaxRows(), records.length + 1 - sheet.getMaxRows());
      }
      if (records.length) sheet.getRange(2, 1, records.length, 13).setValues(records.map(reviewRecordRow_));
      SpreadsheetApp.flush();
      rowById = new Map(records.map(function (record, index) { return [record.id, index + 2]; }));
    },
    save: function (record) {
      const row = rowById.get(record.id);
      // Detect a manual sort during processing before writing another review's checkpoint.
      if (!row || sheet.getRange(row, 9).getValue() !== record.id) {
        throw new ReviewNotificationError('通知中に履歴の行が移動しました。処理中の並べ替えは避けてください。');
      }
      sheet.getRange(row, 1, 1, 13).setValues([reviewRecordRow_(record)]);
      SpreadsheetApp.flush();
    },
  };
}

function configureReviewSheet_(sheet) {
  sheet.getRange(1, 1, 1, 13).setValues([PLAY_REVIEWS.headers])
    .setBackground('#1f4e78').setFontColor('#ffffff').setFontWeight('bold');
  sheet.setFrozenRows(1);
  sheet.setColumnWidths(1, 13, 130);
  sheet.setColumnWidth(2, 60);
  sheet.setColumnWidth(3, 480);
  sheet.setColumnWidths(6, 2, 170);
  sheet.setColumnWidth(8, 280);
  sheet.getRange('C:C').setWrap(true);
  sheet.getRange('F:G').setNumberFormat('yyyy/MM/dd HH:mm:ss');
  sheet.getRange('C:E').setNumberFormat('@');
  sheet.getRange('H:H').setNumberFormat('@');
  sheet.getRange('I:M').setNumberFormat('@');
  sheet.hideColumns(9);
  sheet.hideColumns(11, 3);
}

function discordWebhookUrl_() {
  const value = PropertiesService.getScriptProperties().getProperty('DISCORD_WEBHOOK_URL') || '';
  // Apps Script has no WHATWG URL/URLSearchParams globals. Accept only known Discord endpoints.
  const match = value.trim().match(/^(https:\/\/(?:discord\.com|discordapp\.com|canary\.discord\.com|ptb\.discord\.com)\/api(?:\/v\d+)?\/webhooks\/\d+\/[A-Za-z0-9_-]+)\/?(?:\?([^#]*))?$/);
  if (!match) throw new ReviewNotificationError('スクリプトプロパティ DISCORD_WEBHOOK_URL を正しいDiscord Webhook URLに設定してください。');
  const thread = (match[2] || '').match(/(?:^|&)thread_id=(\d+)(?:&|$)/);
  return match[1] + '?wait=true' + (thread ? '&thread_id=' + thread[1] : '');
}

function truncateReviewText_(value, limit) {
  const text = String(value);
  return text.length <= limit ? text : text.slice(0, limit - 1).replace(/[\uD800-\uDBFF]$/, '') + '…';
}

function reviewDiscordPayload_(record, historical) {
  return {
    allowed_mentions: { parse: [] },
    embeds: [{
      title: PLAY_REVIEWS.appName + ' · ' + (historical ? '過去レビュー'
        : record.notifiedVersion ? 'レビュー更新' : '新着レビュー'),
      url: record.url,
      color: record.rating <= 2 ? 0xe74c3c : record.rating === 3 ? 0xf1c40f : 0x2ecc71,
      author: { name: truncateReviewText_(record.author, 256) },
      description: truncateReviewText_(record.text.replace(/\t/g, '\n').trim() || '（本文なし）', 4096),
      fields: [
        { name: '評価', value: '★'.repeat(record.rating) + '☆'.repeat(5 - record.rating) + ' (' + record.rating + '/5)', inline: true },
        { name: 'アプリバージョン', value: truncateReviewText_(record.appVersion, 256), inline: true },
        { name: '言語', value: truncateReviewText_(record.language, 64), inline: true },
      ],
      timestamp: record.modifiedAt.toISOString(),
      footer: { text: 'Google Play · レビュー投稿／更新日時' },
    }],
  };
}
