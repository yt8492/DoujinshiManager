# GAS＋スプレッドシートでGoogle PlayレビューをDiscordに通知

「ベッドの下」(`com.yt8492.doujinshimanager`) の新着レビューと投稿者による更新を1時間ごとに確認する。

```text
初回：Playの月別CSV＋直近のレビューAPI → スプレッドシートに記録（通知なし）
GASの時間主導トリガー → Playレビュー取得 → スプレッドシートに未通知分を保存
  → Discord Webhookへ送信 → 送信できた行の通知済み情報を更新
```

実行にGitHub Actions、通知履歴用ブランチ、サービスアカウントの秘密鍵は使わない。リポジトリではGASのソースとテストを管理し、通知データはGoogleスプレッドシートに保存する。

## 初回の導入

### 1. スプレッドシートとGASを用意する

1. Play ConsoleでこのアプリにアクセスできるGoogleアカウントを使い、Googleスプレッドシートを新規作成する。名前の例は「ベッドの下 Playレビュー通知」。
2. スプレッドシートの「拡張機能 → Apps Script」を開く。
3. エディタの `Code.gs` を、このディレクトリの **Code.gs** 全体で置き換える。
4. GASの「プロジェクトの設定」で「appsscript.json マニフェスト ファイルをエディタで表示する」を有効にする。
5. エディタに表示された `appsscript.json` を、このディレクトリの **appsscript.json** 全体で置き換え、保存する。

初回関数が「Playレビュー」タブを作成する。他のタブはそのまま残す。スタンドアロンGASを使う場合のみ、後述の `SPREADSHEET_ID` に既存スプレッドシートのIDを設定する。

### 2. Google APIと実行者の権限を設定する

1. Google Cloud Consoleで通知用の標準Cloudプロジェクトを作成するか、設定変更が可能な既存プロジェクトを選ぶ。
2. そのプロジェクトで **Google Play Android Developer API** (`androidpublisher.googleapis.com`) を有効にする。
3. Google Auth PlatformのOAuth同意画面を設定する。個人アカウントの場合はExternal、適切なWorkspace組織内だけで使う場合はInternalを選び、アプリ名・サポートメール等の必須項目を設定する。
4. GASの「プロジェクトの設定 → Google Cloud Platform（GCP）プロジェクト → プロジェクトを変更」に、上記の**プロジェクト番号**を設定する。
5. Play Consoleの「ユーザーと権限」で、GASを実行するGoogleアカウントに対象アプリへのアクセスと「レビューへの返信」権限があることを確認する。オーナーアカウントは既存の権限を利用する。
6. 過去の月別レポートを読むため、同じアカウントに**アカウント全体（グローバル）の「アプリ情報の表示」**権限があることを確認する。オーナーは既存の権限を利用する。これはレビューAPIのアプリ単位の権限とは別に確認する。

認証には `ScriptApp.getOAuthToken()` を使用する。最初の実行時にGoogleの認可画面が表示されるので、使用するアカウントと要求スコープを確認して認可する。スコープはレビューAPI、Cloud Storageの読み取り、スプレッドシート、外部HTTP通信、トリガー管理の5つ。コードはPlayのレビュー読み取りだけを行い、返信・リリース操作は行わない。

ExternalのOAuth同意画面が **Testing** のままだと、このスコープ構成では認可が7日で期限切れになるため、継続運用時は **In production** に切り替える。これはOAuth同意画面の公開ステータスであり、GASのウェブアプリ公開ではない。個人利用でも未確認アプリの案内が表示される場合がある。自分が作成したプロジェクトとコードであることを確認する。

### 3. Discord Webhookとレポートの保存先を設定する

1. Discordの通知先テキストチャンネルで「チャンネルを編集 → 連携サービス → ウェブフック」からWebhookを作成し、URLをコピーする。
2. Play Consoleの **「レポートのダウンロード → レビュー」** を開き、**「Cloud Storage URIをコピー」**を押す。Google Playが用意したレポート用バケットを使う。
3. GASの「プロジェクトの設定 → スクリプト プロパティ」に次を登録する。

| プロパティ | 設定値 |
| --- | --- |
| `DISCORD_WEBHOOK_URL` | 通知先のWebhook URL（必須） |
| `PLAY_REPORT_BUCKET` | コピーした `gs://pubsite_prod_.../reviews/` 等のURI、またはバケット名（必須）。`pubsite_prod_...` と `pubsite_prod_rev_...` の両方に対応 |
| `SPREADSHEET_ID` | スタンドアロンGASの場合のみ必要。スプレッドシートURLの `/d/` と `/edit` の間のID |

Webhook URLはコードやシートのセルには書かない。スクリプトプロパティはスクリプトの編集者から参照できるため、シートとスクリプトの編集共有先は管理者に限定する。フォーラムの既存投稿を使う場合はWebhook URLに `?thread_id=投稿ID` を付ける。

`PLAY_REPORT_BUCKET` はGoogle Cloudのプロジェクト番号とは異なる。コピーしたURIにディレクトリやファイル名が含まれていても、バケット名を取り出して対象アプリの月別CSVを自動列挙する。新しいバケットの作成やCSVの手動アップロードは不要。

### 4. 初期化して定期実行を開始する

GASエディタ上部の関数選択から、次の順に実行する。

1. **`initializeReviewNotifications`** — Googleへの認可後、月別CSVの一覧と直近のレビューAPIを取得する。「Playレビュー」タブに直近分を「初回対象外」、過去分を「過去取込」として保存し、Discordには投稿しない。**ログの `"complete":true` を確認する。** `false` の場合は時間制限に備えて保存した状態なので、同じ関数を再実行する。
2. **`sendTestNotification`** — Discordへ接続テスト文を1件送信する。通知先のチャンネルで到着を確認する。レビュー履歴は変更しない。
3. **`pollPlayReviews`** — 通常処理を手動実行する。新着・更新がなければ `{"notified":0,"pending":0}`。新着・更新があればDiscordへ実際に送信し、シートの通知日時とメッセージIDを更新する。
4. **`installHourlyTrigger`** — 1時間ごとの定期実行を登録する。GASの「トリガー」画面に `pollPlayReviews` が1つあることを確認する。

時刻はGAS側で決まるため、毎時17分固定ではない。ブラウザを閉じても動く。ウェブアプリとしてのデプロイ操作は不要。

接続テストはDiscordへの投稿権限と到着先の確認用。初めて実レビューの `notified` が1以上になった際には、Discordのレビュー通知とシートの「通知済み」を確認する。

初期化ログの例：

```json
{"initialized":42,"reportsProcessed":12,"remainingReports":0,"unidentified":0,"complete":true,"notified":0}
```

`initialized` はシートの総レビュー行数、`reportsProcessed` はその実行で保存した月数、`remainingReports` は残り月数。`unidentified` はレビューIDを復元できず区別して保存した行数。対象アプリのCSVがまだなければ月数は0になるため、期待より少ない場合はPlay Consoleのレポート一覧も確認する。

### すでに旧版で初期化している場合

1. GASの `Code.gs` と `appsscript.json` を両方とも最新版に置き換える。
2. `PLAY_REPORT_BUCKET` とレポートの閲覧権限を設定する。
3. **`importHistoricalReviews`** を手動実行し、追加された `devstorage.read_only` スコープを認可する。`complete:true` になるまで同じ関数を再実行する。

既存のタブや `PLAY_REVIEW_STATE` は削除しない。この関数は既存のAPI履歴・通知済み情報・未通知行を上書きしない。追加するのは、取込開始時点で更新日時が1週間より古いレビュー。直近1週間の新着を誤って通知対象外にしないため、その分は `pollPlayReviews` に任せる。取り込み完了後の再取り込みは行わない。

### 取り込んだ過去レビューをDiscordに通知する

過去取込が完了した後、必要な場合に **`notifyHistoricalReviews`** を手動実行する。すでに導入済みなら、GASの `Code.gs` を最新版に差し替える。追加のスクリプトプロパティやスコープ設定は不要。

この関数を実行すると、保存済みの「初回対象外」「過去取込」「過去取込（ID不明）」のうち、まだDiscordに送っていない行を**更新日時の古い順に、1レビュー1メッセージ**で送信する。Discordではタイトルに「過去レビュー」と表示する。Play APIやCSVの再取得はせず、シートに保存した内容を使う。

送信確認を取得できた行は「過去通知済み」とし、通知日時・DiscordメッセージIDを保存する。同じ関数を再実行しても、送信済みの行はスキップする。通常の新着・更新の未通知行はこの関数では送らず、`pollPlayReviews` が処理する。

ログの `notified` は今回送った件数、`pending` は過去分の残件数。`pending` が1以上、または `deferred:true` の場合は、**同じ `notifyHistoricalReviews` を再実行**する。`reason:"discord_rate_limit"` の場合は、ログの **`retryAfterSeconds` 秒以上待ってから**再実行する。`retryAt` は再開可能時刻をUTC（末尾 `Z`）で表したもの。短い待機は実行中に処理し、長い待機や再試行上限への到達はエラーにせず一時停止する。

429以外の送信エラーは「過去通知エラー」として残り、原因を解消してから再実行すると続きから送る。旧版で429により「過去通知エラー」になった行も再実行の対象。定期トリガーは過去通知の残件を自動送信しない。

通知履歴をリセットする必要はない。ただし、Discord送信後からシートへの記録までの間に異常終了した場合の重複可能性は、通常通知と同じ。ID不明などで同じレビューが複数行ある場合も、それぞれの行を送信する。

## 過去レビューの取得範囲

Google Playが保存している `reviews/reviews_com.yt8492.doujinshimanager_YYYYMM.csv` をすべて列挙し、古い月から読み込む。開始月の指定は不要。初回に直近のAPIも取得し、同じレビューIDがある場合はAPIの内容を優先する。月別CSV同士では同じIDの最新の内容を残す。

CSVは日次収集から反映まで3〜7日かかるため、通常の通知には直近のAPIを使う。取得できるのはGoogle側に残っているレポートの範囲で、削除済みデータや全改訂履歴の復元は保証しない。[Googleの月次レポート仕様](https://support.google.com/googleplay/android-developer/answer/6135870?hl=ja)

CSVには投稿者名がなく、タイトル・本文の改行も除去されている。投稿者は「不明（CSVに記載なし）」として保存し、APIで取得できた際に補う。本文はCSVにあるタイトルと本文をタブでつないで保存する。

レビューリンクからIDが取れない行も捨てず、状態を「過去取込（ID不明）」にして、CSV名・行番号・内容から作った内部IDで記録する。この行はAPIとの同一性を判定できないため、同じレビューが別行として残る場合がある。IDが分かる行については、CSVの改行・日時精度の差だけで更新通知しないよう照合する。

## 履歴の構成

「Playレビュー」タブは、IDが分かるレビューについてはレビューIDごとに1行。本文や評価が更新されたら同じ行を更新する。過去の本文の全改訂履歴を追記する方式ではない。

| 列 | 内容 |
| --- | --- |
| A | 状態：初回対象外／過去取込／過去取込（ID不明）／過去通知済み／過去通知エラー／未通知／通知済み／送信エラー |
| B〜E | 評価、本文、投稿者、アプリバージョン |
| F〜G | レビュー更新日時、最終通知日時 |
| H | レビューへのストアリンク |
| I | レビューID（通常は非表示） |
| J | 言語 |
| K〜M | 取得内容ハッシュ、通知済みハッシュ、DiscordメッセージID（通常は非表示） |

更新日時・評価・本文のハッシュで投稿者による変更を判定する。開発者の返信と「役に立った」の増減は通知しない。初回対象外・過去取込の行は通知済みハッシュのみ設定し、最終通知日時とメッセージIDは空欄にする。日時はGoogle Sheetsの日時値として保存する。表示タイムゾーンはスプレッドシートの「ファイル → 設定」で日本に設定する。

本文付きの星1〜5レビューが対象。本文が `=` で始まっても数式として実行しない。Discordのメンションは無効にし、長文はDiscordの上限に合わせて省略する。スプレッドシートには本文全体を保持する。

履歴は自動削除しない。APIの取得期間から外れたレビューや未送信のレビューもシートに残る。列・ID・ハッシュを手動変更したり行を削除すると重複判定に影響するため、閲覧はフィルタ表示を使い、処理中の並べ替えは避ける。停止中に行全体を並べ替えることは可能。

`PLAY_REVIEW_STATE` というスクリプトプロパティは、初期化したスプレッドシートID・タブID・スキーマと、過去取込のバケット名・最後に保存した月のファイル名・完了状態を記録する内部設定。本文や通知履歴は入らない。このプロパティや取り込み途中のバケット設定を変更・削除しない。

`PLAY_REVIEW_DISCORD_RETRY_AT` はDiscordへ次にアクセスできる時刻を保存する内部設定。自動で作成されるので手動設定は不要。成功応答で送信枠を使い切った場合と429を受けた場合に更新し、再実行時や通常通知でも待機時刻を守るために使う。

## 失敗時・停止・再開

- スクリプトロックで同時実行を防ぐ。トリガーは1つのGoogleアカウントだけで作成する。別アカウントのトリガーは一覧から見えないため、自動重複除去は行わない。
- 全APIページの取得成功後、未通知の本文を先にシートへ保存する。1件送信するごとに送信確認・メッセージID・通知済みハッシュを保存する。次回は保存済みの残りを処理する。
- 6分の制限に備え、開始から約4分で新しい送信を開始しなくなり、残件を次回に回す。APIページ取得中に時間を超えた場合は、その回の取得結果を使わず失敗とする。取得だけで上限に達し続ける規模では、取得ページの継続処理が別途必要。
- 過去取込は月単位で保存してから進捗を更新する。エラー時も同じ関数で続きから再開できる。初回取り込み中は通常通知とトリガー登録を停止する。1か月のCSVだけで実行時間やUrlFetchの応答サイズ上限（50MB）に達する規模は、月内の分割処理が別途必要。
- Discordは成功時の `X-RateLimit-Remaining` とリセット時刻を読み、送信枠が0なら次の送信前に待つ。429では `retry_after`・`Retry-After` 等を読み、最大3回再試行する。待機時間が60秒を超える場合、実行予算内に収まらない場合、再試行上限に達した場合は、レビュー通知を `deferred:true` として一時停止し、残件数と再開可能時刻を返す。待機時間が応答から分からない429は約60秒の待機を設定する。
- レート制限による一時停止では、その行を通知済みにせず、送信済み行の記録を保持する。Discordのタイムアウト・5xxは投稿済みの可能性があるため、その場で再送しない。
- **送信直後の異常終了や、Discordへの送信後にシート保存が失敗した場合は、次回に重複通知する可能性がある。** Discordとシートの通知日時・メッセージIDを照合する。
- Playの403は実行者の権限・OAuthスコープ・API有効化、Discordの404はWebhook URL・削除、シートの失敗はGoogle認可・編集権限・列構成を確認する。生のHTTP応答や認証情報はログに出さない。
- 「Google Playレポート: HTTP 403」の場合は、`appsscript.json` の追加スコープで再認可したか、バケットが正しいか、実行者にアカウント全体の「アプリ情報の表示」があるかを確認する。
- 停止は **`removeReviewTriggers`**、再開は **`installHourlyTrigger`** を実行する。履歴を保持するため再初期化は不要。
- 初期化済みの履歴は再初期化できない。削除・破損時はSheetsの変更履歴等から元の履歴とタブを復元する。初期化途中で失敗し `PLAY_REVIEW_STATE` が未作成のまま部分的なタブだけが残った場合は、その初期化途中の「Playレビュー」タブを削除してから初期化を再実行する。

Play APIの対象は本番公開アプリの、直近1週間に投稿・更新された本文付きレビュー。星だけの評価とテストトラックのフィードバックは取得できない。未取得のまま1週間以上停止した期間のレビューは、Play Console側で確認する。

## ローカルテスト

Node.js 22以上で、外部依存のインストールなしに実行できる。

```sh
cd scripts/play-reviews
npm test
```

実際の `Code.gs` を読み込み、GASサービスを模した環境で初期化、月別CSVのデコードと項目変換、APIとの照合、既存履歴への追加、過去レビューの手動通知と再実行、差分判定、未通知保存、月単位の再開、時間制限、排他、数式対策、ページング、HTTPエラーを確認する。CSVパーサー自体はGASの `Utilities.parseCsv` を使用し、テストでは既知のCSVと行配列の対応を模す。Googleアカウントでの認可、実際のGAS・Sheets操作、Discordへの送信はGoogle側への導入後に別途確認する。

## 公式資料

- [Google Play Reviews API](https://developers.google.com/android-publisher/reply-to-reviews)
- [Playの月次レポート・権限・CSV形式](https://support.google.com/googleplay/android-developer/answer/6135870?hl=ja)
- [Cloud Storageのオブジェクト一覧](https://cloud.google.com/storage/docs/json_api/v1/objects/list)
- [Cloud Storageのファイル取得](https://cloud.google.com/storage/docs/json_api/v1/objects/get)
- [GASのOAuthスコープ](https://developers.google.com/apps-script/concepts/scopes)
- [GASのOAuthトークン](https://developers.google.com/apps-script/reference/script/script-app#getOAuthToken())
- [GASの標準Cloudプロジェクト](https://developers.google.com/apps-script/guides/cloud-platform-projects)
- [OAuth同意画面のTestingの制限](https://developers.google.com/identity/protocols/oauth2#expiration)
- [時間主導トリガーと実行者の権限](https://developers.google.com/apps-script/guides/triggers/installable)
- [GASの実行時間制限](https://developers.google.com/apps-script/guides/services/quotas)
- [Discord Webhook](https://docs.discord.com/developers/resources/webhook#execute-webhook)
- [Discordのレート制限と待機時間](https://docs.discord.com/developers/topics/rate-limits)
