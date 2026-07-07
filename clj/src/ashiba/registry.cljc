(ns ashiba.registry
  #?(:clj (:require [ashiba.bmc :as bmc])))

(def task-types #{"health" "load_bmc" "score_bmc" "run_bmc"})

(defn dispatch [task-type payload]
  #?(:cljs
     {:error "cljs_runtime_not_implemented"}
     :clj
     (case task-type
       "health" {:status "ok" :profile "jp-ashiba"}
       "load_bmc" (bmc/load-bmc (:bmc_path payload))
       "score_bmc" (bmc/report (bmc/score (bmc/ingest-summary (bmc/load-bmc (:bmc_path payload)))))
       "run_bmc" (bmc/run-bmc (:bmc_path payload))
       {:error "unknown_task"
        :task_type task-type
        :known_tasks (vec (sort task-types))})))

;; ─── 4-actor pyzeebe registration pattern (ported from 20-actors/jp-ashiba/py/app.py) ──
;;
;; py/app.py's `bootstrap_zeebe_worker` connects to a Zeebe broker
;; (`ZEEBE_BROKER`, default `zeebe.bpmn-dispatcher.svc.cluster.local:26500`) and
;; registers 4 pyzeebe task primitives (satellite-detector / owner-resolver /
;; outbound-emailer / safety-predictor), each via that module's
;; `register_zeebe_tasks(worker)`. This is a host-injected wiring step (gRPC
;; channel + ZeebeWorker lifecycle) with no pure-logic content to port — recorded
;; here as data so the BPMN contract stays discoverable from the pure-logic repo
;; even though the actual worker registration lives in whatever runtime hosts
;; these actors next (see CLAUDE.md follow-up: "LangGraph/pyzeebe/BPMN 配線").
;;
;; **BPMN slim-payload convention** (kept by all 4 actors, per their docstrings):
;; a BPMN ServiceTask only ever passes `site_id` + `upstream_tx_cid` (+ whatever
;; that actor's own trigger fields are, e.g. `tile_coords`/`epoch`/`image_cid` for
;; satellite-detector, `iot_stream_cid` for safety-predictor) — never the full
;; entity body. Downstream actors pull the body back from kotoba-datomic via
;; Datalog `q()`, joining on `site_id` against the upstream actor's `run_graph`.
;; This keeps BPMN XML payloads small and makes the Datomic graph (not the BPMN
;; token) the single source of truth for actor-to-actor state hand-off.
(def actor-task-registry
  [{:task-type "ai.gftd.apps.jp-ashiba.satellite-detector.detect"
    :actor-did "did:web:jp-ashiba.gftd.ai:actor:satellite-detector"
    :bpmn-input ["tile_coords" "epoch" "image_cid" "run_graph"]
    :bpmn-output ["site_id" "tx_cid"]
    :pulls-from []
    :next-actor "did:web:jp-ashiba.gftd.ai:actor:owner-resolver"}
   {:task-type "ai.gftd.apps.jp-ashiba.owner-resolver.resolve"
    :actor-did "did:web:jp-ashiba.gftd.ai:actor:owner-resolver"
    :bpmn-input ["site_id" "run_graph" "upstream_tx_cid"]
    :bpmn-output ["site_id" "tx_cid"]
    :pulls-from ["satellite-detector"]
    :next-actor "did:web:jp-ashiba.gftd.ai:actor:outbound-emailer"}
   {:task-type "ai.gftd.apps.jp-ashiba.outbound-emailer.send"
    :actor-did "did:web:jp-ashiba.gftd.ai:actor:outbound-emailer"
    :bpmn-input ["site_id" "run_graph" "upstream_tx_cid"]
    :bpmn-output ["site_id" "tx_cid"]
    :pulls-from ["satellite-detector" "owner-resolver"]
    :next-actor "did:web:jp-ashiba.gftd.ai:actor:conversion-tracker"}
   {:task-type "ai.gftd.apps.jp-ashiba.safety-predictor.predict"
    :actor-did "did:web:jp-ashiba.gftd.ai:actor:safety-predictor"
    :bpmn-input ["site_id" "run_graph" "iot_stream_cid" "upstream_tx_cid"]
    :bpmn-output ["site_id" "tx_cid"]
    :pulls-from ["satellite-detector"]
    :next-actor "did:web:jp-ashiba.gftd.ai:actor:alert-escalator"}])
