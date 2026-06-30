(ns ashiba.bmc-test
  (:require [ashiba.bmc :as bmc]
            [ashiba.registry :as registry]
            [clojure.java.io :as io]
            [clojure.test :refer [deftest is testing]]))

(def fixture
  "../docs/bmc/ashiba-lean-bmc-v45.toml")

(deftest load-bmc-test
  (let [state (bmc/load-bmc fixture)]
    (is (= 45 (get-in state [:meta :iteration])))
    (is (= "bmc:ashiba:v45" (get-in state [:meta :kotoba_quad_graph])))
    (is (seq (:blocks state)))
    (is (seq (:datoms state)))
    (is (some #(= [:db/add "bmc:ashiba:block:problem" :bmc/maturity 4] %)
              (:datoms state)))))

(deftest run-bmc-test
  (let [state (bmc/run-bmc fixture)]
    (is (= 100 (:coverage_pct state)))
    (is (pos? (count (:at_risk state))))
    (is (re-find #"ashiba.gftd.ai Lean BMC Scoring Report" (:report state)))
    (is (re-find #"Iteration : 45" (:report state)))))

(deftest registry-test
  (is (= {:status "ok" :profile "jp-ashiba"}
         (registry/dispatch "health" {})))
  (is (= "unknown_task" (:error (registry/dispatch "missing" {})))))
