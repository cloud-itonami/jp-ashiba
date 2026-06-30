(ns ashiba.server-test
  (:require [ashiba.server :as server]
            [clojure.test :refer [deftest is]]
            [jsonista.core :as json])
  (:import [java.net ServerSocket URI]
           [java.net.http HttpClient HttpRequest HttpRequest$BodyPublishers
            HttpResponse$BodyHandlers]))

(def mapper (json/object-mapper {:decode-key-fn keyword :encode-key-fn name}))
(def fixture "../docs/bmc/ashiba-lean-bmc-v45.toml")

(defn free-port []
  (with-open [socket (ServerSocket. 0)]
    (.getLocalPort socket)))

(defn request [port method path body]
  (let [builder (-> (HttpRequest/newBuilder)
                    (.uri (URI/create (str "http://127.0.0.1:" port path))))
        builder (case method
                  :get (.GET builder)
                  :post (.POST builder (HttpRequest$BodyPublishers/ofString
                                        (json/write-value-as-string body mapper))))]
    (-> (HttpClient/newHttpClient)
        (.send (.build (.header builder "content-type" "application/json"))
               (HttpResponse$BodyHandlers/ofString)))))

(deftest health-and-run-test
  (let [port (free-port)
        srv (server/create-server port)]
    (try
      (.start srv)
      (let [health (request port :get "/health" nil)
            run (request port :post "/run" {:task_type "run_bmc"
                                            :payload {:bmc_path fixture}})
            missing (request port :post "/invoke" {:task_type "missing"
                                                   :payload {}})]
        (is (= 200 (.statusCode health)))
        (is (= {:status "ok" :profile "jp-ashiba"}
               (json/read-value (.body health) mapper)))
        (is (= 200 (.statusCode run)))
        (is (= 100 (:coverage_pct (json/read-value (.body run) mapper))))
        (is (= 404 (.statusCode missing))))
      (finally
        (.stop srv 0)))))
