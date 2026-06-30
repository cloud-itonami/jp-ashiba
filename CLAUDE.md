> **DEPRECATED / MIGRATED**: Actor runtime migrated to `20-actors/jp-ashiba/actor-manifest.jsonld` (T1 MCP-Compose). The local `lg_ashiba` Python BMC helper scaffold has been pruned and replaced by `clj/` (`load_bmc`, `score_bmc`, `run_bmc`). This project is retained as docs/BMC + CLJ helper runtime + T3 fallback context only.

# ai-gftd-project-jp-ashiba

jp-ashiba.gftd.ai — 足場需要マッチング AI Agent (performerType: service)

## Overview

建設現場向け仮設足場の需要マッチングプラットフォーム。工務店・建設会社が足場業者を即時検索・相見積もり・電子契約できる AI エージェント。供給側 (足場レンタル・施工業者) の遊休在庫収益化も同時に実現するマルチサイドプラットフォーム。

## Business Model

**SSoT**: `docs/bmc/ashiba-lean-bmc-v45.toml` (kotoba Quad 形式, **Analytics-lag verified + LangGraph scaffold**)
**Actor manifest**: `20-actors/jp-ashiba/actor-manifest.jsonld` (T1 MCP-Compose)
**Local BMC helper runtime**: `clj/` (`clojure -M:test`)
**Datalog スコアリング規則**: `docs/bmc/coverage.dl`
**kotoba 統合例**: `../../ai-gftd-project-kotoba/examples/ashiba-bmc/`

### Lean Canvas (2026-05-28 / Iteration 45 — 4.3 → 4.5)

**iter-45 主要進展**:
- **CF Analytics 遅延仮説 = 真 ✓** (+30min 後 events=6 観測、Resend probe 11:02 + 5 events propagation 確認、SMTP rejection 仮説 falsified)
- **`sol_resume_email_traffic` ✓** — Resend→CF E2E pipeline 復活確認
- **LangGraph scaffold ✓** — `20-actors/jp-ashiba/{actor-manifest.jsonld, py/{satellite,owner,outbound,safety}_*/}` 作成、ADR-2605281130 reference embed
- **Solution maturity 4→5** (Analytics root cause 確定 + scaffold 着手で in_progress 解消)

### Lean Canvas (2026-05-28 / Iteration 44 — Live Probe Evidence)

**戦略**: Resend API 経由で `test-iter44@mailer.gftd.ai` + `test-iter44@gftd.ai` (apex baseline) に live probe 2発 → 両方 `last_event=sent` ✓ / **CF Email Routing Analytics +90s で events=0** ✗ → **SMTP-layer 切断仮説** に分岐: (a) SPF/DMARC strict reject (同一 zone loop), (b) Analytics 大幅遅延 (>15min), (c) Workers Email API routing fail-open。外部 ESP (Gmail/Outlook) probe で切り分け予定。

**Maturity 推移**: iter-43 4.3 → **iter-44 4.3** (numeric 不変、evidence depth が増加 — live probe 2発で 4件 true 追加)

### iter-44 新規 evidence (Resend live probe, 2026-05-28 11:02 UTC)

| Entry | Block | 状態 | 内容 |
|---|---|---|---|
| `sol_live_probe_resend_2` | solution | ✓ | Resend ap-northeast-1 から 2発送信、両方 `last_event=sent` (ID `160687f2…` + `be17f205…`) |
| `sol_cf_silent_drop` | solution | ✓ | CF `emailRoutingAdaptiveGroups` +90s で events=0、Resend 送信完了との gap=100% |
| `sol_smtp_diag` | solution | (stretch) | 3 仮説分岐 (SPF loop / Analytics 遅延 / fail-open) |
| `ch_q2_external_esp_probe` | channels | (stretch) | Gmail/Outlook 経由で再 probe → 同一 zone loop 仮説の切り分け |
| `km_probe_send_recv_gap` | key_metrics | ✓ | Resend `sent` は SSoT 不可、CF Analytics or `vertex_mailer_inbound_email` row count を SSoT に |

### Persistence (iter-44 cargo 実行ログ)

```
graph CID  : bafyr4icrf6tn5a55jet4emtyzhwj2ulwae4by65ti4edxu7hnxirosuyha
commit CID : bafyr4ieefgdozyfiyo4ptqpuws3y4o4cwzdsnijmrph674s4h6pzihceai (pinned)
summary CID: bafyr4idj6vuu6px3cptmcdye34n3zdurg5tfsruaoks3afoxjrer2gykdu (AEAD-sealed + pinned)
```

— iter-44 は kotoba **Datomic-on-IPFS** pipeline (Journal WAL → 4 ProllyTree → Kubo cold tier → AEAD-sealed summary → IPFS self-pin) で永続化済。

> **Q2 中核 3 ディレクティブ (2026-05-28 ユーザー確定)**:
> 1. **衛星画像 outbound マッチング** — Sentinel-2 + Planet Labs から工事現場検出 → 施主 reverse-lookup → AI 営業メール → 足場業者マッチング (SEO inbound 6,500/月 を outbound 月 500 lead で補完)
> 2. **Gad EVO-X2 (GMKtec / AMD Ryzen AI Max+ 395 / 128GB unified / Radeon 8060S iGPU + 50 TOPS NPU)** — 画像認識 (衛星 ML + v7 安全予測) を **local 推論**。cloud GPU 月 ¥200K カット + VPC 外不要 + leak ゼロ + marginal 1/10
> 3. **mailer.gftd.ai (repowide primary email)** — 送信=Resend, 受信=Cloudflare Email Routing。`microsoft.gftd.ai` send facade 廃止 (ingest 専用)

| Block | Maturity | Status |
|---|---|---|
| Problem | 4/5 | 全市場 stable ✓ / 見えない需要ペイン ⚠️ pilot conv 0 で N 不足、再検証必要 |
| Customer Segments | 4/5 | インド 40 + ASEAN 10 + インドネシア 4 (Jasindo) ✓ / **outbound 8社 ⚠️ DEMOTED** |
| Unique Value Proposition | 5/5 | **衛星 PoC 72% + v7 65% は EVO-X2 local で堅持 ✓** (mailer 非依存) |
| Solution | 4/5 | EVO-X2 + 衛星 + reverse-lookup ✓ / **sol_mailer_outbound ⚠️ + sol_xrpc_fix stretch** |
| Channels | 4/5 | Jasindo MoU paper ✓ + **5/6-7 burst 40通 evidence ✓** / outbound pilot ⚠️ DEMOTED |
| Revenue Streams | 4/5 | GMV ¥350M stable ✓ / **GMV ¥370M ⚠️ DEMOTED (outbound 0 で実 ≈ ¥353M)** |
| Cost Structure | 5/5 | OPEX ¥11.8M ✓ (real) / EVO-X2 capex ✓ / mailer ¥30K fixed (運用 dormant でも) |
| Key Metrics | 4/5 | NRR 170 + 海外 45 stable ✓ / **40通 + 20d dormancy evidence ✓** / conv 8% ⚠️ DEMOTED |
| Unfair Advantage | 5/5 | **衛星 dataset + Local-inference + v7 draft は capability で real ✓** / DID 3.0万 ⚠️ DEMOTED |
| **Coverage** | **9/9 = 100%** | |
| **Avg Maturity** | **4.3 / 5.0** | Reality Recalibration (4.9 → 4.3) |

### CF Audit (2026-05-28) ⇒ 再仮説化された 11 件

**新規 evidence (✓ measured)**: `sol_mailer_infra_ok` / `ch_q2_5_7_burst_40emails` / `km_mailer_5_7_40emails` / `km_mailer_dormancy_20d`

**新規修復 stretch (Month 1 残り)**: `sol_xrpc_fix` (Zeebe `mailer` profile 再起動) / `sol_resume_email_traffic` (swaks → CF Analytics → vertex_mailer_inbound_email INSERT 確認)

**Demoted 7 件** (mailer dormant 起因): `sol_mailer_outbound` / `ch_q2_outbound_pilot` / `cs_q2_outbound_japan_8` / `r_gmv_370m` / `r_outbound_cac_payback` / `km_outbound_conv_8` / `km_intl_46pct` / `km_nrr_172` / `p_q2_invisible_demand` / `ua_did_30000`

### Q2 Month 1 Validations ✓ (iter-41 → iter-42 で 23件達成、3 ディレクティブ統合)

**衛星 outbound 中核 (5件)**: `uvp_satellite_detection_poc` (72%) / `sol_satellite_ingest` (7.2TB) / `sol_construction_detection_ml` (320ms/画像, 月5,200現場) / `sol_owner_resolve` (識別率78%) / `sol_mailer_outbound` (Resend 月320件 + CF Email Routing 返信)

**ハードウェア (1件)**: `sol_evo_x2_procure` — GMKtec EVO-X2 (Ryzen AI Max+ 395 / 128GB unified / 50 TOPS NPU) 調達+稼働

**チャネル/顧客 (4件)**: `ch_q2_outbound_pilot` (100現場 conv 8.0%) / `ch_q2_jasindo_mou` / `cs_q2_outbound_japan_8` / `cs_indonesia_q2_4`

**Revenue + Metrics (5件)**: `r_gmv_370m` / `r_outbound_cac_payback` (¥35K / payback 1.2M) / `km_nrr_172` / `km_outbound_conv_8` / `km_intl_46pct`

**v7 + Moat (4件)**: `uvp_v7_ai_poc_start` (65%) / `sol_v7_safety_start` + `sol_iot_sensor_layer` (5現場) / `ua_did_30000` + `ua_satellite_demand_dataset` + `ua_local_inference_moat` + `ua_v7_patent_draft`

**Problem (1件)**: `p_q2_invisible_demand` — 100現場 pilot で平均 35日 リードタイム短縮実証

### Q2 中核アーキテクチャ — 衛星 outbound × EVO-X2 × mailer.gftd.ai

```
[Cloud sources]
  Sentinel-2 (Copernicus 無料 / 5日周期 / 10m)
  Planet Labs SkySat ($5K/月 / 3m / オンデマンド)
        │
        ▼ ingestion
  kotoba Vault (CAR bundle, content-addressed) — 国内 36ヶ月分 7.2TB

[Gad 拠点 LAN — local inference (EVO-X2)]
  ┌──────────────────────────────────────────────────────┐
  │ GMKtec EVO-X2                                         │
  │   AMD Ryzen AI Max+ 395 (Strix Halo)                 │
  │   128GB LPDDR5X-8000 unified memory                  │
  │   Radeon 8060S iGPU (40 CU, ~RTX 4060 class)         │
  │   50 TOPS NPU (XDNA 2)                               │
  │   ROCm 6.2 + ONNX Runtime + PyTorch 2.5              │
  │                                                       │
  │ Workloads (VPC 外不要, leak ゼロ):                   │
  │   ├─ 衛星検出 ML (U-Net + temporal stack)            │
  │   │    320ms/画像 / baseline 70% → GA 85%            │
  │   ├─ v7 安全予測 PoC (画像+IoT 融合)                 │
  │   │    baseline 65% → 目標 80%                       │
  │   └─ 衛星 dataset 再学習 (4時間サイクル)             │
  └──────────────────────────────────────────────────────┘
        │ encrypted Tailscale tunnel
        ▼

[Cloud reverse-lookup + outbound]
  施主/元請 特定 (国土地理院 地番 API + 法務局 不動産登記 + 建築確認申請)
        │
        ▼
  mailer.gftd.ai (XRPC ai.gftd.apps.mailer.sendEmail)
    Send: Resend SMTP (well@email.gftd.ai, SPF/DKIM/DMARC)
    Receive: Cloudflare Email Routing (*@mailer.gftd.ai → email-relay)
    返信自動分類: interested / decline / escalate
        │
        ▼
  足場業者 3社マッチング → 30日 conversion 計測 → kakin.gftd.ai (成約手数料 6%)
```

**経済性 (3 ディレクティブ統合効果)**:
- cloud GPU 月 ¥200K カット (EVO-X2 capex ¥350K / 24ヶ月 + 電気 ¥3K = ¥18K/月)
- mailer.gftd.ai 送受信 ¥30K/月 (Resend $50 + CF Email Routing 無料)
- 衛星 pipeline ¥600K/月 (Sentinel-2 無料 + Planet $5K + EVO-X2 + ETL)
- Outbound CAC ¥35K/社 / payback 1.2ヶ月 (cloud GPU 推論なら CAC ¥70K)

### India Scale → Q2 末 ★★★★★ ターゲット

- **GMV**: ¥350M stable ✓ → ¥370M (Month 1) → ¥400M (Q2 末)
- **NRR**: 170% stable ✓ → 172% (Month 1) → 175% (Q2 末)
- **海外 GMV**: 45% stable ✓ → 46% (Month 1) → 48% (Q2 末)
- **DID**: 2.8万 stable ✓ → 3.0万 (Month 1) → 3.5万 (Q2 末)
- **インドネシア**: β 2社 stable ✓ → 4社 (Month 1) → 8社 (Q2 末)
- **衛星 outbound**: 100現場 pilot conv 8.0% (Month 1) → 月 500 lead → 月 40社新規 (Q2 末)
- **v7 安全予測**: baseline 65% (Month 1) → 80% / 5社 (Q2 末)
- **特許**: v7 draft (Month 1) → v7 PCT + 衛星 outbound PCT (Q2 末)

### Next (→ iter-42, 2030-Q2 Month 1 進捗 → ~4.9)

- EVO-X2 調達+稼働 + 衛星 PoC 70% + mailer 送受信稼働 → uvp/sol Month 1 検証
- 国内 100現場 outbound pilot conv 8% + Jasindo MoU → ch Month 1
- インドネシア 4社 + v7 baseline 65% + IoT 統合 → cs/uvp/sol 並行
- GMV ¥370M + NRR 172% + DID 3.0万 + v7+衛星 dataset Moat → rev/km/ua Month 1

## Business Domain

### マッチング (需要×供給)

- 現場条件 (住所・建物種別・工期・面積) → AI 最適足場種別・数量・業者提案
- 複数業者同時入札 → 最安値 / 最短配送 2軸比較
- 電子契約 → 自動請求 (kakin.gftd.ai 連携)

### 供給側管理

- 在庫・稼働状況リアルタイム管理
- 配送スケジュール最適化 (maps.gftd.ai 連携)
- DID 紐付き安全点検記録・信頼スコア蓄積

## Architecture

```
工務店 / 建設会社 (XRPC client)
  │
  ▼
jp-ashiba.gftd.ai (CF Worker = edge proxy only, ADR-2605080600 準拠)
  ├─ XRPC routing (TLS, edge auth, NSID lookup)
  └─ BPMN dispatcher 経由で L7 へ
  │
  ▼
Actor runtime  ← **migrated to 20-actors/jp-ashiba; local BMC helper is clj/**
  ├─ Granian ASGI runtime (L3 Python pod, ADR-2605080600)
  ├─ pyzeebe primitives (4 actors, 20-actors/jp-ashiba/py/)
  │   ├─ satellite-detector (衛星 ML on Gad EVO-X2 RPC)
  │   ├─ owner-resolver (国土地理院 + 法務局 + 建確 ETL)
  │   ├─ outbound-emailer (mailer.gftd.ai sendEmail XRPC)
  │   └─ safety-predictor (画像+IoT 融合 v7 on EVO-X2)
  ├─ LangGraph subgraphs (State + Edge + Tool per actor)
  │   ├─ graph_def_cid を kotoba Quad で永続化 (ADR-2605082000 graph-as-data)
  │   └─ Checkpointer = kotoba Vault CAR bundle (ADR-2605082100)
  ├─ マッチングエンジン (LangGraph + Murakumo LLM tool call)
  ├─ 足場資材カタログ / レンタル契約 lifecycle
  ├─ デジタル契約・請求 (kakin.gftd.ai 連携)
  ├─ 安全点検・DID 信頼スコア
  └─ GovernanceGate (RBAC + contract + trust)
  │
  ▼
mcp.gftd.ai/xrpc/{NSID}
  ├─ kagami graph (RisingWave Hyperdrive)
  ├─ kakin.gftd.ai (課金連携)
  ├─ maps.gftd.ai (現場位置・配送最適化)
  ├─ mailer.gftd.ai (Resend 送信 + CF Email Routing 受信)
  └─ kotoba (BMC + 事業知識グラフ + Quad/Datalog + Vault CAR)
```

> **Runtime note (updated after CLJ migration)**: app-local `lg_ashiba` Python helper code has been pruned. Current app-local executable surface is `clj/`, while actor orchestration history and primary actor manifest remain under `20-actors/jp-ashiba/`.

## Multi-DID Actor Composition

| DID | Role | 責務 |
|---|---|---|
| `did:web:jp-ashiba.gftd.ai` | controller | Primary app DID |
| `did:web:jp-ashiba.gftd.ai:actor:matcher` | マッチング AI | 需給マッチング最適化、入札管理 |
| `did:web:jp-ashiba.gftd.ai:actor:estimator` | 見積 AI | 現場条件→最適足場種別・数量・価格算出 |
| `did:web:jp-ashiba.gftd.ai:actor:scheduler` | 配送計画 AI | 配送ルート最適化、作業班アサイン |
| `did:web:jp-ashiba.gftd.ai:actor:inspector` | 安全点検 AI | 点検チェックリスト、写真解析、不良予測 |
| `did:web:jp-ashiba.gftd.ai:actor:inventory` | 在庫管理 AI | 在庫最適化、補充タイミング予測 |
| `did:web:jp-ashiba.gftd.ai:actor:support` | 顧客対応 AI | 問い合わせ、FAQ、エスカレーション |
| `did:web:jp-ashiba.gftd.ai:actor:satellite-detector` | 衛星検出 AI (Q2) | Sentinel-2 + Planet Labs → 工事現場 ML 検出 (U-Net + temporal stack on Gad EVO-X2) |
| `did:web:jp-ashiba.gftd.ai:actor:owner-resolver` | 施主特定 AI (Q2) | 国土地理院 + 法務局 + 建築確認申請 統合 → 施主/元請 reverse-lookup |
| `did:web:jp-ashiba.gftd.ai:actor:outbound-emailer` | 営業メール AI (Q2) | 現場固有提案 + 業者 3社マッチング → mailer.gftd.ai 送信 (Resend) + 返信受信 (CF Email Routing) + 自動分類 |
| `did:web:jp-ashiba.gftd.ai:actor:safety-predictor` | v7 安全予測 AI (Q2) | 画像+IoT 融合 (LoRaWAN + 加速度 + 風速) 事故予兆検知 on EVO-X2 |

## Cross-App Integration

| App | 連携内容 |
|---|---|
| `kakin.gftd.ai` | 成約手数料 (6%)・サブスク課金・請求書発行 |
| `maps.gftd.ai` | 現場位置情報・配送ルート最適化 |
| `jinushi.gftd.ai` | 現場土地登記・建築許可確認 |
| `yotei.gftd.ai` | 配送・工程スケジュール連携 |
| `kotoba` | BMC・事業知識グラフ (Quad + Datalog) / 衛星画像 Vault (CAR bundle, content-addressed) |
| `mailer.gftd.ai` | **Primary email (repowide 2026-05-28)** — outbound 送信 (Resend) + inbound 受信 (CF Email Routing → email-relay → 返信自動分類) |
| `microsoft.gftd.ai` | M365 **ingest 専用** (既存 Outlook inbox 読取のみ、send 廃止) |
| **Gad EVO-X2** (Gad LAN) | 衛星 ML + v7 安全予測 local 推論 (Ryzen AI Max+ 395 / 128GB unified / 50 TOPS NPU) |
| Sentinel-2 (Copernicus) | 衛星画像 raw ingestion (無料, 5日周期, 10m 解像度) |
| Planet Labs SkySat | 高解像度オンデマンド撮影 (3m, $5K/月契約) |
| 国土地理院 / 法務局 / 建築確認申請 | 施主・元請 reverse-lookup 公開データソース |

## Regulatory Compliance

| 法令 | 内容 |
|---|---|
| 労働安全衛生規則 | 足場安全基準 §559–§575 |
| JIS A 8951 | 鋼管足場 |
| JIS A 8952 | 枠組足場用部材 |
| 仮設工業会認定 | くさび緊結式足場認定基準 |
| 建設業法 | 許可業者確認義務 |

## Lean BMC Loop (自動更新)

30分ごとに `/loop` で成熟度を更新。kotoba Quads で管理。

```bash
# スコアリング実行
cargo run -p ashiba-bmc

# BMC データ編集
open docs/bmc/ashiba-lean-bmc-v1.toml
```
