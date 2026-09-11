(ns ashiba.outbound-emailer-test
  "Golden parity vs `uv run --no-project python3 -c \"import outbound_emailer ...\"`
   captured 2026-07-07 against 20-actors/jp-ashiba/py/outbound_emailer/__init__.py."
  (:require [ashiba.outbound-emailer :as oe]
            [clojure.test :refer [deftest is testing]]))

(deftest constants-test
  (is (= "did:web:jp-ashiba.gftd.ai:actor:outbound-emailer" oe/actor-did))
  (is (= "ai.gftd.apps.jp-ashiba.outbound-emailer.send" oe/task-type))
  (is (= "did:web:jp-ashiba.gftd.ai:actor:conversion-tracker" oe/next-actor))
  (is (nil? oe/mailer-send-xrpc))
  (is (= "ashiba@mailer.gftd.ai" oe/from-address))
  (is (= "https://api.murakumo.cloud/v1" oe/murakumo-url))
  (is (= "iter-51-datomic" oe/scaffold-version))
  (is (= [":ashiba/proposal-text" ":ashiba/vendor-candidates" ":ashiba/mailer-message-id"
          ":ashiba/sent-at" ":ashiba/sent-by"]
         oe/written-attributes))
  (is (= ["satellite-detector" "owner-resolver"] oe/pulls-from)))

(deftest route-by-send-status-test
  (testing "golden route_by_send_status table"
    (is (= "failed" (oe/route-by-send-status nil)))
    (is (= "failed" (oe/route-by-send-status "")))
    (is (= "sent" (oe/route-by-send-status "msg-123")))))

(deftest build-send-tx-edn-test
  (testing "golden tx-edn body (embedded quotes rewritten to single quotes)"
    (is (= (str "[\n"
                "  [:db/add \"site-1\" :ashiba/proposal-text  \"Proposal with 'quotes' inside and more text\"]\n"
                "  [:db/add \"site-1\" :ashiba/vendor-candidates \"did:web:v1,did:web:v2\"]\n"
                "  [:db/add \"site-1\" :ashiba/mailer-message-id \"msg-1\"]\n"
                "  [:db/add \"site-1\" :ashiba/sent-at         \"2026-05-28T00:00:00.000000Z\"]\n"
                "  [:db/add \"site-1\" :ashiba/sent-by         \"did:web:jp-ashiba.gftd.ai:actor:outbound-emailer\"]\n"
                "]")
           (oe/build-send-tx-edn
            {:site-id "site-1"
             :proposal "Proposal with \"quotes\" inside and more text"
             :vendors ["did:web:v1" "did:web:v2"]
             :message-id "msg-1"
             :sent-at "2026-05-28T00:00:00.000000"}))))
  (testing "golden tx-edn body (no message-id -> 'failed', proposal truncated to 200 chars, empty vendors)"
    (let [long-proposal (apply str (repeat 250 "A"))
          expected-proposal (apply str (repeat 200 "A"))]
      (is (= (str "[\n"
                  "  [:db/add \"site-1\" :ashiba/proposal-text  \"" expected-proposal "\"]\n"
                  "  [:db/add \"site-1\" :ashiba/vendor-candidates \"\"]\n"
                  "  [:db/add \"site-1\" :ashiba/mailer-message-id \"failed\"]\n"
                  "  [:db/add \"site-1\" :ashiba/sent-at         \"2026-05-28T00:00:00.000000Z\"]\n"
                  "  [:db/add \"site-1\" :ashiba/sent-by         \"did:web:jp-ashiba.gftd.ai:actor:outbound-emailer\"]\n"
                  "]")
             (oe/build-send-tx-edn
              {:site-id "site-1" :proposal long-proposal :vendors [] :message-id nil
               :sent-at "2026-05-28T00:00:00.000000"}))))))
