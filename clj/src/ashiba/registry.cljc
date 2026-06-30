(ns ashiba.registry
  #?(:clj (:require [ashiba.bmc :as bmc])))

(def task-types #{"health" "load_bmc" "score_bmc" "run_bmc"})

(defn dispatch [task-type payload]
  #?(:cljs
     {:error "cljs_runtime_not_implemented"}
     :clj
     (case task-type
       "health" {:status "ok" :profile "jp-ashiba"}
       "load_bmc" (bmc/load-bmc (:bmc_path payload))
       "score_bmc" (bmc/report (bmc/score (bmc/ingest-summary (bmc/load-bmc (:bmc_path payload)))))
       "run_bmc" (bmc/run-bmc (:bmc_path payload))
       {:error "unknown_task"
        :task_type task-type
        :known_tasks (vec (sort task-types))})))
