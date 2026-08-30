# jp-ashiba

**「足場」（あしば）— 建設現場の仮設足場のこと。** この repo の名前は機能を示さないので
先に名乗る: **建設現場向け仮設足場の需給マッチング事業の、意思決定ロジックと
Lean BMC コーパスを持つ repo** である。

`cloud-itonami/jp-ashiba`。

---

## この repo に「今」あるもの

3 つある。それだけである。

| # | 何 | 場所 |
|---|---|---|
| 1 | **4 actor の純粋な判断関数**（衛星検出 / 施主特定 / 営業メール / 安全予測） | `clj/src/ashiba/` |
| 2 | **Lean BMC の版管理コーパスと採点エンジン**（59 版 + Datalog 規則） | `docs/bmc/`, `clj/src/ashiba/bmc.cljc` |
| 3 | **上の 2 つを叩く最小 HTTP dispatcher** | `clj/src/ashiba/server.cljc` |

動かし方は **[`docs/operator-quickstart.md`](docs/operator-quickstart.md)**。

## この repo に「無い」もの（重要）

**稼働している actor runtime はここには無い。** 4 つの actor は *判断関数だけ* が
移植されており、外界に触る辺は全て未配線である。これを誤解すると「動いているはず
のものが動いていない」という探し方をすることになる。

未配線なのは:

- kotoba-datomic の `Connection` / `q()` / `transact` — tx を組み立てる **本体は
  移植済み・テスト済み**だが、実際に発行する呼び出しが無い
- EVO-X2 への推論 RPC（`unet_infer_on_evo_x2`, `fuse_image_iot_on_evo_x2`）
- 国土地理院 / 法務局 / 建築確認申請 の外部 API 呼び出し
- 営業メール送信（host-injected; Resend）
- Murakumo LLM 呼び出し（提案文生成）
- Zeebe broker への接続と 4 task の worker 登録 — 契約は
  `ashiba.registry/actor-task-registry` に**データとして**記録してあるだけ

つまりこの repo は、**外界に触る辺を全部剥がして、判断だけを残したもの**である。
剥がした辺の一覧は `CLAUDE.md` の "NOT ported" 節が正本。

## 4 actor と、その判断

各 actor が持つのは「境界値でどちらに分岐するか」という決定と、Datomic tx の
EDN を**組み立てる**純関数の 2 つだけ。

| ns | 判断関数 | 分岐（実際に返る文字列） |
|---|---|---|
| `ashiba.satellite-detector` | `route-by-confidence` | 検出信頼度 `<0.3` → `discard` / `<0.7` → `retry` / `>=0.7` → `dispatch` |
| `ashiba.owner-resolver` | `compute-match-rate` + `route-by-match-rate` | 特定率 `<0.4` → `discard` / `<0.75` → `manual_review` / `>=0.75` → `dispatch` |
| `ashiba.outbound-emailer` | `route-by-send-status` | message-id が空か → `failed` / 非空 → `sent` |
| `ashiba.safety-predictor` | `route-by-risk` | 事故予兆 `<0.5` → `discard` / `<0.8` → `log` / `>=0.8` → `alert` |
| `ashiba.seal` | `attr-tier`, `validate-tx-payload!` | 属性の機密 tier 判定 + AES-256-GCM seal |
| `ashiba.registry` | `dispatch` | task-type → 上記 + BPMN 契約テーブル |

`compute-match-rate` は 3 情報源（地番 / 登記 / 建確）のうち非 nil の割合を返すだけの
仮実装で、元実装のコメントどおり**情報源間の一致度による重み付けは未実装**。

これらは **Python からの移植であり、境界値ごとに元実装の出力を捕獲した golden 値に
対してテストされている**（`clj/test/ashiba/*_test.cljc`、各ファイルの docstring に
捕獲日 2026-07-07 を記録）。移植元 `py/` は削除済み。

`ashiba.seal` の AES-256-GCM だけは golden が存在しない — Python 側が
`raise NotImplementedError` のスタブだったため。ここは round-trip
（`unseal(seal(x)) == x`）でしか検証されておらず、**鍵導出は本番の vault 鍵ではなく
明示的な仮置き**である（`derive-key` の docstring 参照）。本番利用の前に
kotoba-py SecureVault と突き合わせること。

## Lean BMC コーパス

`docs/bmc/ashiba-lean-bmc-v{1..60}.toml`（v49 は欠番、実在 59 版）。
1 版 = 1 反復の Lean Canvas スナップショットで、9 block × 各 entry に
`validated` フラグを持つ。

`ashiba.bmc` がこれを読んで、Datomic datom 列・block 別成熟度・未検証仮説
（at-risk）・カバレッジ率・テキストレポートを**決定論的に**生成する。
`docs/bmc/coverage.dl` は同じ判定を Datalog 規則として書いたもの（kotoba-kqe 向け、
このリポジトリのコードからは実行されない）。

最新 v60 の実測値は quickstart に載せてある。

## ディレクトリ

```
clj/            判断関数 + BMC エンジン + HTTP dispatcher（唯一の実行可能面）
  src/ashiba/   8 ns / 684 行
  test/ashiba/  8 テストファイル / 23 tests / 122 assertions
docs/
  bmc/          Lean BMC 59 版 + Datalog 規則
  adr/          この repo の決定記録（EDN）
  operator-quickstart.md
  PILOT-2-design.md.edn    設計メモ（EDN 化済み）
  RUNBOOK-deploy.md.edn    ※下記注意
actor/          actor-manifest.jsonld（T1 MCP-Compose）+ 出自 SOURCE.edn
schema.edn      生成物（edn-datomize）。手編集禁止
CLAUDE.md       設計・移植の歴史記録。※下記注意
```

## 既知の陳腐化（読む前に知っておくこと）

**`CLAUDE.md` と `docs/RUNBOOK-deploy.md.edn` は、この repo が
`ai-gftd-apps-gftdcojp` のサブディレクトリだった時代に書かれたもの**で、そのまま
辿れない記述を含む。歴史記録としては有効なので消していないが、次を承知して読む:

| 記述 | 実際 |
|---|---|
| `cargo run -p ashiba-bmc` | **Rust は存在しない**。採点は `clj/` にある（quickstart 参照）。workspace 全体で Rust の新規追加は禁止 |
| `20-actors/jp-ashiba/actor-manifest.jsonld` | この repo では `actor/actor-manifest.jsonld` |
| SSoT は `ashiba-lean-bmc-v45.toml` | 現在の最新は v60 |
| RUNBOOK の `preflight.sh` / `deploy_jp_ashiba.sh` / `verify_1tile.sh` / `zbctl` | **この repo に無い**。RUNBOOK は「配線が終わった後の姿」を記した文書であって、今日踏める手順ではない |
| `clj/bb.edn`（babashka タスク） | bb は ADR-2607173000 で退役。`clojure -M:test` を直接使う |

今日実際に踏める手順は `docs/operator-quickstart.md` **だけ**である。

## ライセンス / 所有

`cloud-itonami` org。出自は `ai-gftd-apps-gftdcojp/60-apps/ai-gftd-project-jp-ashiba`
からの分離（`README.md.edn` の `:readme/note`）。
actor identity（DID シェル）の正しい所有者は**この repo ではなく `jp-ashiba-actor`**
——過去に複製が持ち込まれ、撤去済み（`3f440b4`）。
