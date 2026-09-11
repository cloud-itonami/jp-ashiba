(ns ashiba.safety-predictor-test
  "Golden parity vs `uv run --no-project python3 -c \"import safety_predictor ...\"`
   captured 2026-07-07 against 20-actors/jp-ashiba/py/safety_predictor/__init__.py."
  (:require [ashiba.safety-predictor :as sp]
            [clojure.test :refer [deftest is testing]]))

(deftest constants-test
  (is (= "did:web:jp-ashiba.gftd.ai:actor:safety-predictor" sp/actor-did))
  (is (= "ai.gftd.apps.jp-ashiba.safety-predictor.predict" sp/task-type))
  (is (= "http://evo-x2.tail-gad.ts.net/v7/multimodal" sp/evo-x2-infer-url))
  (is (= ["lorawan_accel" "lorawan_wind" "lorawan_vibration"] sp/iot-sources))
  (is (= "iter-51-datomic" sp/scaffold-version))
  (is (= 0.8 sp/target-precision))
  (is (= [":ashiba/accident-risk" ":ashiba/risk-factors" ":ashiba/safety-decision"
          ":ashiba/predicted-by"]
         sp/written-attributes))
  (is (= ["satellite-detector"] sp/pulls-from)))

(deftest route-by-risk-test
  (testing "golden route_by_risk table"
    (is (= "discard" (sp/route-by-risk 0.0)))
    (is (= "discard" (sp/route-by-risk 0.49)))
    (is (= "log" (sp/route-by-risk 0.5)))
    (is (= "log" (sp/route-by-risk 0.79)))
    (is (= "alert" (sp/route-by-risk 0.8)))
    (is (= "alert" (sp/route-by-risk 1.0)))))

(deftest build-prediction-tx-edn-test
  (testing "golden tx-edn body"
    (is (= (str "[\n"
                "  [:db/add \"site-1\" :ashiba/accident-risk  0.85]\n"
                "  [:db/add \"site-1\" :ashiba/risk-factors   \"wind_high,accel_spike\"]\n"
                "  [:db/add \"site-1\" :ashiba/safety-decision \"alert\"]\n"
                "  [:db/add \"site-1\" :ashiba/predicted-by    \"did:web:jp-ashiba.gftd.ai:actor:safety-predictor\"]\n"
                "]")
           (sp/build-prediction-tx-edn
            {:site-id "site-1" :accident-risk 0.85
             :risk-factors ["wind_high" "accel_spike"] :decision "alert"})))))
