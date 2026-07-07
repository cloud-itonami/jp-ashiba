(ns ashiba.outbound-emailer
  "Port of jp-ashiba py/outbound_emailer/__init__.py (iter-51, Datomic-first).

   **Pull pattern**: BPMN receives only site_id + upstream_tx_cid; the body
   (parcel/registry/permit/match_rate) is pulled from kotoba-datomic via q(),
   joining satellite-detector + owner-resolver's Datoms.

   DID:  did:web:jp-ashiba.gftd.ai:actor:outbound-emailer
   Tools: Murakumo LLM (現場固有提案生成) + mailer.gftd.ai XRPC sendEmail (Resend)
   Edge: send_success → conversion_tracker

   Ported: `route_by_send_status` (pure) + the `transact_send` tx-edn assembly
   template. NOT ported (host-injected IO, each a `raise NotImplementedError` in
   the source): `pull_site_state` (Datalog q() pull), `generate_site_proposal`
   (Murakumo LLM chat completion call), `select_vendor_candidates` (Datalog q() +
   ranking), `send_via_mailer_xrpc` (mailer.gftd.ai HTTP POST / Resend), the actual
   `conn.transact(...)` in `transact_send`, and `send_handler` (pyzeebe task entry).
   The `:ashiba/sent-at` timestamp is a host-injected clock boundary — `build-send
   -tx-edn` takes it as an explicit `sent-at` argument rather than calling
   `(Instant/now)` internally, so the builder stays pure/deterministic and testable."
  (:require [clojure.string :as str]))

(def actor-did "did:web:jp-ashiba.gftd.ai:actor:outbound-emailer")
(def task-type "ai.gftd.apps.jp-ashiba.outbound-emailer.send")
(def next-actor "did:web:jp-ashiba.gftd.ai:actor:conversion-tracker")
(def mailer-send-xrpc "https://mailer.gftd.ai/xrpc/ai.gftd.apps.mailer.sendEmail")
(def from-address "ashiba@mailer.gftd.ai")
(def murakumo-url "https://murakumo.gftd.ai/v1")
(def scaffold-version "iter-51-datomic")

(def written-attributes
  [":ashiba/proposal-text"
   ":ashiba/vendor-candidates"
   ":ashiba/mailer-message-id"
   ":ashiba/sent-at"
   ":ashiba/sent-by"])

(def pulls-from ["satellite-detector" "owner-resolver"])

;; Pull upstream State via Datalog q() — joins 2 actor writes (documented,
;; matches py PULL_QUERY_EDN):
;; [:find ?img ?conf ?parcel ?owner ?permit ?match
;;  :in $ ?sid
;;  :where [?sid :ashiba/image-cid ?img]
;;         [?sid :ashiba/confidence ?conf]
;;         [?sid :ashiba/parcel-id ?parcel]
;;         [?sid :ashiba/registry ?owner]
;;         [?sid :ashiba/permit-type ?permit]
;;         [?sid :ashiba/match-rate ?match]]

(defn route-by-send-status
  "Mirrors py `route_by_send_status`."
  [message-id]
  (if (seq message-id) "sent" "failed"))

(defn build-send-tx-edn
  "Pure assembly of the `transact_send` EDN tx body. Golden-parity ported from
   the f-string template: `proposal[:200].replace('\"', \"'\")`, `','.join(vendors)`,
   `message_id or 'failed'`. `sent-at` is caller-supplied (see namespace docstring)."
  [{:keys [site-id proposal vendors message-id sent-at]}]
  (let [truncated (subs proposal 0 (min 200 (count proposal)))
        escaped (str/replace truncated "\"" "'")]
    (str "[\n"
         "  [:db/add \"" site-id "\" :ashiba/proposal-text  \"" escaped "\"]\n"
         "  [:db/add \"" site-id "\" :ashiba/vendor-candidates \"" (str/join "," vendors) "\"]\n"
         "  [:db/add \"" site-id "\" :ashiba/mailer-message-id \"" (or message-id "failed") "\"]\n"
         "  [:db/add \"" site-id "\" :ashiba/sent-at         \"" sent-at "Z\"]\n"
         "  [:db/add \"" site-id "\" :ashiba/sent-by         \"" actor-did "\"]\n"
         "]")))
