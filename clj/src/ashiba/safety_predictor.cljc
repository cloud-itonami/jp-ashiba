(ns ashiba.safety-predictor
  "Port of jp-ashiba py/safety_predictor/__init__.py (iter-51, Datomic-first).

   **Pull pattern**: BPMN receives site_id + iot_stream_cid (LoRaWAN snapshot) +
   run_graph; satellite-detector's image_cid is pulled via q() → EVO-X2 multimodal
   v7 inference → accident_risk + risk_factors transacted as Datoms.

   DID:  did:web:jp-ashiba.gftd.ai:actor:safety-predictor
   Tools: EVO-X2 multimodal RPC (画像 frames + LoRaWAN IoT stream)
   Edge: accident_risk > 0.8 → alert_escalation

   Ported: `route_by_risk` (pure) + the `transact_prediction` tx-edn assembly
   template. NOT ported (host-injected IO, each a `raise NotImplementedError` in
   the source): `pull_image_cid` (Datalog q() pull), `fuse_image_iot_on_evo_x2`
   (EVO-X2 multimodal RPC), the actual `conn.transact(...)` in
   `transact_prediction`, `emit_alert` (cross-actor `magatama.invoke`), and
   `predict_handler` (pyzeebe task entry)."
  (:require [clojure.string :as str]))

(def actor-did "did:web:jp-ashiba.gftd.ai:actor:safety-predictor")
(def task-type "ai.gftd.apps.jp-ashiba.safety-predictor.predict")
(def evo-x2-infer-url "http://evo-x2.tail-gad.ts.net/v7/multimodal")
(def iot-sources ["lorawan_accel" "lorawan_wind" "lorawan_vibration"])
(def scaffold-version "iter-51-datomic")
(def target-precision 0.80)

(def written-attributes
  [":ashiba/accident-risk"
   ":ashiba/risk-factors"
   ":ashiba/safety-decision"
   ":ashiba/predicted-by"])

(def pulls-from ["satellite-detector"])

(defn route-by-risk
  "Mirrors py `route_by_risk`."
  [accident-risk]
  (cond
    (>= accident-risk 0.8) "alert"
    (>= accident-risk 0.5) "log"
    :else "discard"))

(defn build-prediction-tx-edn
  "Pure assembly of the `transact_prediction` EDN tx body. Golden-parity ported
   from the f-string template (`factors_str = \",\".join(risk_factors)`) — see
   `ashiba.satellite-detector` docstring for why the actual transact call is not
   ported."
  [{:keys [site-id accident-risk risk-factors decision]}]
  (let [factors-str (str/join "," risk-factors)]
    (str "[\n"
         "  [:db/add \"" site-id "\" :ashiba/accident-risk  " accident-risk "]\n"
         "  [:db/add \"" site-id "\" :ashiba/risk-factors   \"" factors-str "\"]\n"
         "  [:db/add \"" site-id "\" :ashiba/safety-decision \"" decision "\"]\n"
         "  [:db/add \"" site-id "\" :ashiba/predicted-by    \"" actor-did "\"]\n"
         "]")))
