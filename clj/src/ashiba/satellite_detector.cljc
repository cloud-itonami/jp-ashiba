(ns ashiba.satellite-detector
  "Port of jp-ashiba py/satellite_detector/__init__.py (iter-50, Datomic-first).

   DID:  did:web:jp-ashiba.gftd.ai:actor:satellite-detector
   Edge: confidence > 0.7 → owner-resolver (cross-actor 引き渡しは site_id + tx_cid だけ)

   Only the pure decision logic and the tx-edn assembly template are ported here.
   Everything IO-shaped in the python source — `ingest_tile` (Vault fetch),
   `unet_infer_on_evo_x2` (EVO-X2 RPC over Tailscale), `assign_site_id` (content-hash
   of epoch+tile via a kotoba CID helper not available on the JVM classpath here),
   `transact_detection`'s actual `conn.transact(...)` call, and `detect_handler`
   (the pyzeebe task entry) — is intentionally NOT ported: each `raise
   NotImplementedError(\"wired at Granian pod runtime\")` in the source marks a
   host-injected boundary (kotoba-datomic Connection, EVO-X2 RPC client) that belongs
   to whatever runtime eventually hosts this actor, not to this pure-logic library.
   See CLAUDE.md follow-up list."
  (:require [clojure.string :as str]))

(def actor-did "did:web:jp-ashiba.gftd.ai:actor:satellite-detector")
(def task-type "ai.gftd.apps.jp-ashiba.satellite-detector.detect")
(def next-actor "did:web:jp-ashiba.gftd.ai:actor:owner-resolver")
(def scaffold-version "iter-50-datomic")
(def evo-x2-infer-url "http://evo-x2.tail-gad.ts.net/infer")
(def expected-latency-ms 320)

;; kotoba-datomic schema attributes this actor writes (for upstream contract docs):
(def written-attributes
  [":ashiba/site-id"
   ":ashiba/tile-coords"
   ":ashiba/epoch"
   ":ashiba/image-cid"
   ":ashiba/confidence"
   ":ashiba/segmentation-cid"
   ":ashiba/detected-by"])

(defn route-by-confidence
  "Mirrors py `route_by_confidence`."
  [confidence]
  (cond
    (>= confidence 0.7) "dispatch"
    (< confidence 0.3) "discard"
    :else "retry"))

(defn build-detection-tx-edn
  "Pure assembly of the `transact_detection` EDN tx body. Golden-parity ported
   from the f-string template in py `transact_detection` (which builds this exact
   string then unconditionally `raise NotImplementedError` — this fn extracts the
   template so it is actually exercisable/testable; the real `conn.transact(...)`
   call remains host-injected)."
  [{:keys [site-id tile-coords epoch image-cid confidence segmentation-cid]}]
  (str "[\n"
       "  [:db/add \"" site-id "\" :ashiba/site-id       \"" site-id "\"]\n"
       "  [:db/add \"" site-id "\" :ashiba/tile-coords   \"" tile-coords "\"]\n"
       "  [:db/add \"" site-id "\" :ashiba/epoch         \"" epoch "\"]\n"
       "  [:db/add \"" site-id "\" :ashiba/image-cid     \"" image-cid "\"]\n"
       "  [:db/add \"" site-id "\" :ashiba/confidence    " confidence "]\n"
       "  [:db/add \"" site-id "\" :ashiba/segmentation-cid \"" segmentation-cid "\"]\n"
       "  [:db/add \"" site-id "\" :ashiba/detected-by   \"" actor-did "\"]\n"
       "]"))
