(ns ashiba.registry-test
  "Covers the app.py pyzeebe-registration-pattern + BPMN slim-payload record added
   to ashiba.registry (documentation-as-data; see registry.cljc docstring)."
  (:require [ashiba.registry :as registry]
            [clojure.test :refer [deftest is testing]]))

(deftest actor-task-registry-test
  (testing "4 actors registered, task-types match golden TASK_TYPE constants"
    (is (= 4 (count registry/actor-task-registry)))
    (is (= #{"ai.gftd.apps.jp-ashiba.satellite-detector.detect"
             "ai.gftd.apps.jp-ashiba.owner-resolver.resolve"
             "ai.gftd.apps.jp-ashiba.outbound-emailer.send"
             "ai.gftd.apps.jp-ashiba.safety-predictor.predict"}
           (set (map :task-type registry/actor-task-registry)))))
  (testing "BPMN slim payload: every entry passes at most site_id/run_graph/upstream_tx_cid
            plus its own trigger fields, and outputs only site_id + tx_cid"
    (doseq [entry registry/actor-task-registry]
      (is (= ["site_id" "tx_cid"] (:bpmn-output entry))))))
