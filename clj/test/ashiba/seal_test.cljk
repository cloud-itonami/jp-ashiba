(ns ashiba.seal-test
  "Golden parity vs `uv run --no-project python3 -c \"import kotoba_seal ...\"`
   captured 2026-07-07 against 20-actors/jp-ashiba/py/kotoba_seal.py."
  (:require [ashiba.seal :as seal]
            [clojure.test :refer [deftest is testing]]))

(deftest tier-classification-test
  (testing "tier constants"
    (is (= "public" seal/tier-public))
    (is (= "tier2" seal/tier-2))
    (is (= "tier3" seal/tier-3)))
  (testing "self-check counts (golden: (14, 4, 3))"
    (is (= [14 4 3] (seal/self-check))))
  (testing "tier sets golden values"
    (is (= #{":ashiba/accident-risk" ":ashiba/confidence" ":ashiba/detected-by"
             ":ashiba/epoch" ":ashiba/image-cid" ":ashiba/match-rate"
             ":ashiba/predicted-by" ":ashiba/resolved-by" ":ashiba/safety-decision"
             ":ashiba/segmentation-cid" ":ashiba/sent-at" ":ashiba/sent-by"
             ":ashiba/site-id" ":ashiba/tile-coords"}
           seal/public-attrs))
    (is (= #{":ashiba/parcel-id" ":ashiba/permit-type" ":ashiba/registry"
             ":ashiba/risk-factors"}
           seal/tier-2-attrs))
    (is (= #{":ashiba/mailer-message-id" ":ashiba/proposal-text"
             ":ashiba/vendor-candidates"}
           seal/tier-3-attrs)))
  (testing "no overlap between tiers (mirrors py self-check assertions)"
    (is (empty? (clojure.set/intersection seal/public-attrs seal/tier-2-attrs)))
    (is (empty? (clojure.set/intersection seal/public-attrs seal/tier-3-attrs)))
    (is (empty? (clojure.set/intersection seal/tier-2-attrs seal/tier-3-attrs))))
  (testing "attr-tier golden spot checks"
    (is (= "public" (get seal/attr-tier ":ashiba/site-id")))
    (is (= "tier2" (get seal/attr-tier ":ashiba/parcel-id")))
    (is (= "tier3" (get seal/attr-tier ":ashiba/proposal-text")))
    (is (nil? (get seal/attr-tier ":ashiba/unknown-attr")))))

(deftest is-sealed-test
  (testing "golden is_sealed table"
    (is (true? (seal/is-sealed? "signal:v1:abc")))
    (is (true? (seal/is-sealed? "signal:v1:")))
    (is (false? (seal/is-sealed? "notsealed")))
    (is (false? (seal/is-sealed? "")))
    (is (false? (seal/is-sealed? "signal:v2:abc")))
    (is (false? (seal/is-sealed? nil)))
    (is (false? (seal/is-sealed? 123)))))

(deftest expects-seal-test
  (is (false? (seal/expects-seal? ":ashiba/site-id")))
  (is (true? (seal/expects-seal? ":ashiba/parcel-id")))
  (is (true? (seal/expects-seal? ":ashiba/proposal-text")))
  (is (false? (seal/expects-seal? ":ashiba/unknown-attr"))))

(deftest validate-tx-payload-test
  (testing "tier-3 plaintext is rejected (golden: raises)"
    (is (thrown? #?(:clj clojure.lang.ExceptionInfo :cljs js/Error)
                 (seal/validate-tx-payload! ":ashiba/proposal-text" "raw plaintext"))))
  (testing "tier-3 sealed value is accepted (golden: no raise)"
    (is (nil? (seal/validate-tx-payload! ":ashiba/proposal-text" "signal:v1:ZmFrZWN0"))))
  (testing "tier-public scalar is accepted (golden: no raise)"
    (is (nil? (seal/validate-tx-payload! ":ashiba/confidence" 0.85))))
  (testing "tier-2 unsealed int is rejected (golden: raises)"
    (is (thrown? #?(:clj clojure.lang.ExceptionInfo :cljs js/Error)
                 (seal/validate-tx-payload! ":ashiba/parcel-id" 123))))
  (testing "unclassified attr is a no-op (golden: no raise)"
    (is (nil? (seal/validate-tx-payload! ":ashiba/site-id" "anything")))))

#?(:clj
   (deftest seal-unseal-roundtrip-test
     (testing "AES-256-GCM roundtrip — NEW capability (no python golden; python's
               seal/unseal always raise NotImplementedError). Correctness criterion
               here is functional: decrypt(encrypt(x)) == x, and the envelope carries
               the signal:v1: prefix `is-sealed?` expects."
       (let [actor "did:web:jp-ashiba.gftd.ai:actor:owner-resolver"
             plaintext (.getBytes "parcel-9" "UTF-8")
             sealed (seal/seal! actor plaintext seal/tier-2)]
         (is (seal/is-sealed? sealed))
         (is (= "parcel-9" (String. (seal/unseal actor sealed seal/tier-2) "UTF-8"))))
       (let [actor "did:web:jp-ashiba.gftd.ai:actor:outbound-emailer"
             run-graph "kotoba://graph/ashiba:run:42"
             plaintext (.getBytes "vendor proposal text" "UTF-8")
             sealed (seal/seal! actor run-graph plaintext seal/tier-3)]
         (is (seal/is-sealed? sealed))
         (is (= "vendor proposal text"
                (String. (seal/unseal actor run-graph sealed seal/tier-3) "UTF-8")))
         (testing "wrong run-graph context fails to decrypt (tier-3 binds run_graph)"
           (is (thrown? Exception
                        (seal/unseal actor "kotoba://graph/ashiba:run:99" sealed seal/tier-3))))))
     (testing "cannot seal a TIER_PUBLIC attribute"
       (is (thrown? clojure.lang.ExceptionInfo
                    (seal/seal! "did:web:x" (.getBytes "x" "UTF-8") seal/tier-public))))))
