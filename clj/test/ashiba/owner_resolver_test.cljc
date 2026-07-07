(ns ashiba.owner-resolver-test
  "Golden parity vs `uv run --no-project python3 -c \"import owner_resolver ...\"`
   captured 2026-07-07 against 20-actors/jp-ashiba/py/owner_resolver/__init__.py."
  (:require [ashiba.owner-resolver :as orz]
            [clojure.test :refer [deftest is testing]]))

(deftest constants-test
  (is (= "did:web:jp-ashiba.gftd.ai:actor:owner-resolver" orz/actor-did))
  (is (= "ai.gftd.apps.jp-ashiba.owner-resolver.resolve" orz/task-type))
  (is (= "did:web:jp-ashiba.gftd.ai:actor:outbound-emailer" orz/next-actor))
  (is (= "iter-50-datomic" orz/scaffold-version))
  (is (= ["gsi.go.jp" "houmu.go.jp" "kenchiku-permit.go.jp"] orz/data-sources))
  (is (= [":ashiba/parcel-id" ":ashiba/registry" ":ashiba/permit-type"
          ":ashiba/match-rate" ":ashiba/resolved-by"]
         orz/written-attributes))
  (is (= ["satellite-detector"] orz/pulls-from)))

(deftest compute-match-rate-test
  (testing "golden compute_match_rate table"
    (is (= 0.0 (orz/compute-match-rate nil nil nil)))
    (is (= (/ 1.0 3.0) (orz/compute-match-rate "p" nil nil)))
    (is (= (/ 2.0 3.0) (orz/compute-match-rate "p" "r" nil)))
    (is (= 1.0 (orz/compute-match-rate "p" "r" "perm")))))

(deftest route-by-match-rate-test
  (testing "golden route_by_match_rate table"
    (is (= "discard" (orz/route-by-match-rate 0.0)))
    (is (= "discard" (orz/route-by-match-rate 0.39)))
    (is (= "manual_review" (orz/route-by-match-rate 0.4)))
    (is (= "manual_review" (orz/route-by-match-rate 0.5)))
    (is (= "manual_review" (orz/route-by-match-rate 0.74)))
    (is (= "dispatch" (orz/route-by-match-rate 0.75)))
    (is (= "dispatch" (orz/route-by-match-rate 1.0)))))

(deftest build-owner-tx-edn-test
  (testing "golden tx-edn body (full)"
    (is (= (str "[\n"
                "  [:db/add \"site-1\" :ashiba/parcel-id    \"parcel-9\"]\n"
                "  [:db/add \"site-1\" :ashiba/registry     \"did:web:owner1\"]\n"
                "  [:db/add \"site-1\" :ashiba/permit-type  \"residential\"]\n"
                "  [:db/add \"site-1\" :ashiba/match-rate   1.0]\n"
                "  [:db/add \"site-1\" :ashiba/resolved-by  \"did:web:jp-ashiba.gftd.ai:actor:owner-resolver\"]\n"
                "]")
           (orz/build-owner-tx-edn
            {:site-id "site-1" :parcel-id "parcel-9"
             :registry {:owner_did "did:web:owner1"} :permit {:type "residential"}
             :match-rate 1.0}))))
  (testing "golden tx-edn body (all missing -> 'unknown' fallback)"
    (is (= (str "[\n"
                "  [:db/add \"site-1\" :ashiba/parcel-id    \"unknown\"]\n"
                "  [:db/add \"site-1\" :ashiba/registry     \"unknown\"]\n"
                "  [:db/add \"site-1\" :ashiba/permit-type  \"unknown\"]\n"
                "  [:db/add \"site-1\" :ashiba/match-rate   0.0]\n"
                "  [:db/add \"site-1\" :ashiba/resolved-by  \"did:web:jp-ashiba.gftd.ai:actor:owner-resolver\"]\n"
                "]")
           (orz/build-owner-tx-edn
            {:site-id "site-1" :parcel-id nil :registry nil :permit nil :match-rate 0.0})))))
