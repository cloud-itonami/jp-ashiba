(ns ashiba.owner-resolver
  "Port of jp-ashiba py/owner_resolver/__init__.py (iter-50, Datomic-first).

   DID:  did:web:jp-ashiba.gftd.ai:actor:owner-resolver
   Edge: match_rate > 0.75 → outbound-emailer (引き渡しは site_id + tx_cid のみ)

   Ported: `compute_match_rate` (pure heuristic) + `route_by_match_rate` (pure
   threshold routing) + the `transact_owner` tx-edn assembly template.
   NOT ported (host-injected IO boundaries, each a `raise NotImplementedError`
   in the source): `pull_detection` (Datalog q() pull), `query_gsi_parcel` /
   `fetch_houmu_registry` / `fetch_kenchiku_permit` (3 external gov API calls),
   `transact_owner`'s actual `conn.transact(...)`, and `resolve_handler` (the
   pyzeebe task entry)."
  (:require [clojure.string :as str]))

(def actor-did "did:web:jp-ashiba.gftd.ai:actor:owner-resolver")
(def task-type "ai.gftd.apps.jp-ashiba.owner-resolver.resolve")
(def next-actor "did:web:jp-ashiba.gftd.ai:actor:outbound-emailer")
(def scaffold-version "iter-50-datomic")
(def data-sources ["gsi.go.jp" "houmu.go.jp" "kenchiku-permit.go.jp"])

(def written-attributes
  [":ashiba/parcel-id"
   ":ashiba/registry"
   ":ashiba/permit-type"
   ":ashiba/match-rate"
   ":ashiba/resolved-by"])

(def pulls-from ["satellite-detector"])

(defn compute-match-rate
  "Heuristic from how many of the 3 sources resolved + cross-source agreement
   (placeholder; real impl adds agreement weighting — mirrors py comment).
   Mirrors py `compute_match_rate`."
  [parcel-id registry permit]
  (/ (count (remove nil? [parcel-id registry permit])) 3.0))

(defn route-by-match-rate
  "Mirrors py `route_by_match_rate`."
  [match-rate]
  (cond
    (>= match-rate 0.75) "dispatch"
    (< match-rate 0.4) "discard"
    :else "manual_review"))

(defn build-owner-tx-edn
  "Pure assembly of the `transact_owner` EDN tx body. Golden-parity ported from
   the f-string template (`parcel_id or 'unknown'`, `registry.get('owner_did',
   'unknown')`, `permit.get('type', 'unknown')`) — see `ashiba.satellite-detector`
   docstring for why the actual transact call is not ported."
  [{:keys [site-id parcel-id registry permit match-rate]}]
  (str "[\n"
       "  [:db/add \"" site-id "\" :ashiba/parcel-id    \"" (or parcel-id "unknown") "\"]\n"
       "  [:db/add \"" site-id "\" :ashiba/registry     \"" (get registry :owner_did "unknown") "\"]\n"
       "  [:db/add \"" site-id "\" :ashiba/permit-type  \"" (get permit :type "unknown") "\"]\n"
       "  [:db/add \"" site-id "\" :ashiba/match-rate   " match-rate "]\n"
       "  [:db/add \"" site-id "\" :ashiba/resolved-by  \"" actor-did "\"]\n"
       "]"))
