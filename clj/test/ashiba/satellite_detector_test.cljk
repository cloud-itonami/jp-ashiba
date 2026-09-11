(ns ashiba.satellite-detector-test
  "Golden parity vs `uv run --no-project python3 -c \"import satellite_detector ...\"`
   captured 2026-07-07 against 20-actors/jp-ashiba/py/satellite_detector/__init__.py."
  (:require [ashiba.satellite-detector :as sd]
            [clojure.test :refer [deftest is testing]]))

(deftest constants-test
  (is (= "did:web:jp-ashiba.gftd.ai:actor:satellite-detector" sd/actor-did))
  (is (= "ai.gftd.apps.jp-ashiba.satellite-detector.detect" sd/task-type))
  (is (= "did:web:jp-ashiba.gftd.ai:actor:owner-resolver" sd/next-actor))
  (is (= "iter-50-datomic" sd/scaffold-version))
  (is (= "http://evo-x2.tail-gad.ts.net/infer" sd/evo-x2-infer-url))
  (is (= 320 sd/expected-latency-ms))
  (is (= [":ashiba/site-id" ":ashiba/tile-coords" ":ashiba/epoch" ":ashiba/image-cid"
          ":ashiba/confidence" ":ashiba/segmentation-cid" ":ashiba/detected-by"]
         sd/written-attributes)))

(deftest route-by-confidence-test
  (testing "golden route_by_confidence table"
    (is (= "discard" (sd/route-by-confidence 0.0)))
    (is (= "discard" (sd/route-by-confidence 0.29)))
    (is (= "retry" (sd/route-by-confidence 0.3)))
    (is (= "retry" (sd/route-by-confidence 0.5)))
    (is (= "retry" (sd/route-by-confidence 0.69)))
    (is (= "dispatch" (sd/route-by-confidence 0.7)))
    (is (= "dispatch" (sd/route-by-confidence 1.0)))))

(deftest build-detection-tx-edn-test
  (testing "golden tx-edn body"
    (is (= (str "[\n"
                "  [:db/add \"site-1\" :ashiba/site-id       \"site-1\"]\n"
                "  [:db/add \"site-1\" :ashiba/tile-coords   \"tile-abc\"]\n"
                "  [:db/add \"site-1\" :ashiba/epoch         \"2026-05-28T00:00:00Z\"]\n"
                "  [:db/add \"site-1\" :ashiba/image-cid     \"cid-img-1\"]\n"
                "  [:db/add \"site-1\" :ashiba/confidence    0.82]\n"
                "  [:db/add \"site-1\" :ashiba/segmentation-cid \"cid-seg-1\"]\n"
                "  [:db/add \"site-1\" :ashiba/detected-by   \"did:web:jp-ashiba.gftd.ai:actor:satellite-detector\"]\n"
                "]")
           (sd/build-detection-tx-edn
            {:site-id "site-1" :tile-coords "tile-abc" :epoch "2026-05-28T00:00:00Z"
             :image-cid "cid-img-1" :confidence 0.82 :segmentation-cid "cid-seg-1"})))))
