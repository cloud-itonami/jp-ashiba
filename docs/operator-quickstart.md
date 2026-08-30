# jp-ashiba operator quickstart

**この文書に書いてある手順は、書いた時点（2026-08-09）で全部実際に踏んで出力を
確認したものだけである。** 踏めなかったものは載せていない。載せる価値のある
「踏めないもの」は末尾の「ここでは踏めないこと」にまとめた。

`docs/RUNBOOK-deploy.md.edn` と混同しないこと。あれは配線が終わった後の K8s /
Zeebe デプロイを記した文書で、そこに出てくるスクリプトはこの repo に存在しない。

## 前提

- **JDK** と **Clojure CLI**（`clojure`）。それだけ。
- 初回は Maven 依存（tomlj / jsonista / slf4j / test-runner）を取りに行くので
  ネットワークが要る。2 回目以降は不要。
- babashka（`bb`）は**要らない**。`clj/bb.edn` は残っているが退役済み
  （ADR-2607173000）。以下は全部 `clojure` を直接叩く。

作業ディレクトリは**常に `clj/`**。BMC の TOML を `../docs/bmc/...` と相対参照する
ので、ここを外すとパスが解決しない。

```sh
cd clj
```

## 1. テストを通す（まずこれ）

```sh
clojure -M:test
```

期待する出力の最後の 2 行:

```
Ran 23 tests containing 122 assertions.
0 failures, 0 errors.
```

**この 23 tests は、Python 実装の出力を境界値ごとに捕獲した golden 値に対する
突き合わせ**である（`clj/test/ashiba/*_test.cljc` の docstring に捕獲日
2026-07-07 を記録）。ここが赤いなら、それは移植の退行であって環境の問題ではない
——先に読むべきは移植元ではなく golden の期待値。

## 2. Lean BMC を採点する

最新版は `v60`（`docs/bmc/` は v1..v60、**v49 は欠番**で実在 59 版）。

```sh
clojure -M -e '(require (quote [ashiba.bmc :as bmc]))
               (println (:report (bmc/run-bmc "../docs/bmc/ashiba-lean-bmc-v60.toml")))'
```

2026-08-09 に実際に出た出力:

```
jp-ashiba Lean BMC Scoring Report
Iteration : 60 (2026-05-28)
Phase     : India Scale 2030-Q2 Month 1 — All-actor seal+validate wired
Graph     : local-cid:bmc:ashiba:v60
Coverage  : 9 blocks (coverage=100%)
Maturity  : 4.6 / 5.0 (9 blocks scored)
At-Risk   : 29 unvalidated hypotheses
Per-Block Maturity:
  channels               5/5
  cost_structure         5/5
  customer_segments      4/5 <- next
  key_metrics            4/5 <- next
  problem                4/5 <- next
  revenue                4/5 <- next
  solution               5/5
  unfair_advantage       5/5
  uvp                    5/5
kotoba Datomic persistence:
  datoms  : 509
  commits : 0
  last_tx : -
```

読み方で 1 つだけ注意: **`commits : 0` / `last_tx : -` は障害ではない。**
`ashiba.bmc/ingest-summary` は kotoba への書き込みを*ローカルの要約に置き換えて
いる*ので、常にこの値になる。実際に永続化したいなら、それは未配線の辺
（README の「無いもの」）。

版を変えれば過去の反復も同じように採点できる（`v1` から `v60` まで全部読める）。

## 3. HTTP dispatcher を起動して叩く

```sh
LANGSERVER_PORT=18771 clojure -M -m ashiba.server
```

別のシェルから。`/health` は GET、それ以外は `/run` に POST する。

```sh
curl -s http://127.0.0.1:18771/health
# => {"status":"ok","profile":"jp-ashiba"}

curl -s -X POST http://127.0.0.1:18771/run \
  -d '{"task_type":"score_bmc","payload":{"bmc_path":"../docs/bmc/ashiba-lean-bmc-v60.toml"}}'
# => 200。JSON のキーは
#    bmc_path, graph_id, report, meta, at_risk, block_maturity,
#    coverage_pct, ingest_result, blocks, datoms
#    v60 では coverage_pct=100 / at_risk=29 件 / datoms=509 件
```

`bmc_path` は**サーバの cwd から相対**に解決される。`clj/` から起動した前提。

task_type は 4 つだけ（`ashiba.registry/task-types`）:
`health` / `load_bmc` / `score_bmc` / `run_bmc`。

### 失敗する側も確認しておく

未知の task は **404** を返し、`known_tasks` を教える:

```sh
curl -s -o /dev/null -w '%{http_code}\n' -X POST http://127.0.0.1:18771/run \
  -d '{"task_type":"nope","payload":{}}'
# => 404
# body: {"error":"unknown_task","task_type":"nope",
#        "known_tasks":["health","load_bmc","run_bmc","score_bmc"]}
```

未知のパスも 404:

```sh
curl -s -o /dev/null -w '%{http_code}\n' http://127.0.0.1:18771/nope
# => 404
```

止めるときは起動シェルで Ctrl-C（`-main` は `@(promise)` で待つだけなので
シグナルで落ちる）。

## 4. 判断関数を単体で叩く

actor の判断だけを見たいならサーバは要らない。全部純関数である。

```sh
clojure -M -e '(require (quote [ashiba.satellite-detector :as sd]))
               (println (mapv sd/route-by-confidence [0.0 0.29 0.3 0.69 0.7 1.0]))'
```

この 6 点は golden test が使っているのと同じ境界値。返る値が
`clj/test/ashiba/satellite_detector_test.cljc` の期待値と一致する。

## ここでは踏めないこと

以下は**この repo では動かない**。動かそうとして時間を溶かさないこと。理由は
「壊れている」ではなく「そもそも辺が無い」。

| やりたいこと | なぜ踏めないか |
|---|---|
| 実際に衛星画像から現場を検出する | EVO-X2 への推論 RPC が未配線 |
| 施主を特定する | 国土地理院 / 法務局 / 建築確認申請 API 呼び出しが未配線 |
| 営業メールを送る | host-injected mail send が未配線 |
| Datomic に書く | tx EDN の**組み立て**はあるが `conn.transact` が無い |
| BPMN から 4 actor を回す | Zeebe broker 接続と worker 登録が無い（契約は `ashiba.registry/actor-task-registry` にデータとしてあるだけ） |
| `cargo run -p ashiba-bmc` | **Rust は存在しない**。CLAUDE.md の該当行は陳腐化。§2 を使う |
| RUNBOOK の `preflight.sh` 等 | この repo に無い |

配線が終わっていない辺の完全な一覧は `CLAUDE.md` の "NOT ported" 節が正本。
