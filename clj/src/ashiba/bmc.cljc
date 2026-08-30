(ns ashiba.bmc
  (:require [clojure.string :as str])
  (:import [org.tomlj Toml TomlArray TomlTable]))

(def compact-inline-re
  #";(?=(?:[^\"]*\"[^\"]*\")*[^\"]*$)")

(defn normalize-compact-inline [src]
  (->> (str/split-lines src)
       (mapcat
        (fn [line]
          (let [s (str/triml line)]
            (if (and (seq s)
                     (not (str/starts-with? s "#"))
                     (not (str/starts-with? s "["))
                     (str/includes? s "=")
                     (str/includes? s ";"))
              (let [indent (subs line 0 (- (count line) (count s)))]
                (map #(str indent (str/trim %))
                     (remove str/blank? (str/split line compact-inline-re))))
              [line]))))
       (str/join "\n")))

(defn- table->map [^TomlTable table]
  (into {}
        (map (fn [entry]
               [(keyword (.getKey entry)) (.getValue entry)]))
        (.entrySet table)))

(defn- array->vec [^TomlArray arr]
  (mapv (fn [idx]
          (let [v (.get arr idx)]
            (if (instance? TomlTable v) (table->map v) v)))
        (range (.size arr))))

(defn- blocks-map [^TomlTable blocks]
  (into {}
        (map
         (fn [entry]
           (let [block-name (.getKey entry)
                 ^TomlTable block (.getValue entry)
                 block-map (table->map block)
                 entries (when-let [arr (.getArray block "entries")]
                           (array->vec arr))]
             [block-name (assoc block-map :entries (or entries []))])))
        (.entrySet blocks)))

(defn parse-toml-file [path]
  (let [src (slurp path)
        normalized (normalize-compact-inline src)
        tmp (java.nio.file.Files/createTempFile "ashiba-bmc-" ".toml" (make-array java.nio.file.attribute.FileAttribute 0))]
    (try
      (spit (.toFile tmp) normalized)
      (let [parsed (Toml/parse tmp)
            errors (.errors parsed)]
        (when (seq errors)
          (throw (ex-info "TOML parse failed" {:errors (mapv str errors)})))
        {:meta (table->map (.getTable parsed "meta"))
         :blocks (blocks-map (.getTable parsed "blocks"))})
      (finally
        (java.nio.file.Files/deleteIfExists tmp)))))

(defn block-datoms [block-name block]
  (let [block-id (str "bmc:ashiba:block:" block-name)
        base [[:db/add block-id :kg/type "bmc:block"]
              [:db/add block-id :bmc/block_name (str block-name)]
              [:db/add block-id :bmc/maturity (int (or (:maturity block) 0))]
              [:db/add block-id :bmc/status (str (or (:status block) ""))]]
        note (when-let [n (:note block)]
               [[:db/add block-id :bmc/note (str n)]])
        entries (mapcat
                 (fn [entry]
                   (let [entry-id (str "bmc:ashiba:entry:" block-name ":" (:id entry))]
                     [[:db/add entry-id :kg/type "bmc:entry"]
                      [:db/add entry-id :bmc/hypothesis (str (or (:hypothesis entry) ""))]
                      [:db/add entry-id :bmc/validated (boolean (:validated entry))]
                      [:db/add entry-id :entry/block block-id]]))
                 (:entries block))]
    (vec (concat base note entries))))

(defn load-bmc [path]
  (let [{:keys [meta blocks]} (parse-toml-file path)
        datoms (mapcat (fn [[block-name block]]
                         (block-datoms block-name block))
                       blocks)
        label (str (or (:kotoba_quad_graph meta) "bmc:ashiba:unknown"))]
    {:bmc_path path
     :meta meta
     :blocks blocks
     :graph_id (str "local-cid:" label)
     :datoms (vec datoms)}))

(defn ingest-summary [state]
  (assoc state :ingest_result {:ok true
                               :datomCount (count (:datoms state))
                               :commits []
                               :lastCommit nil}))

(defn score [state]
  (let [blocks (:blocks state)
        block-maturity (into {}
                             (map (fn [[name block]]
                                    [name (int (or (:maturity block) 0))]))
                             blocks)
        at-risk (vec
                 (mapcat
                  (fn [[block-name block]]
                    (keep
                     (fn [entry]
                       (when-not (:validated entry)
                         {:entry (str "bmc:ashiba:entry:" block-name ":" (:id entry))
                          :block block-name
                          :hypothesis (str (or (:hypothesis entry) ""))}))
                     (:entries block)))
                  blocks))
        covered-blocks (set (keep (fn [[name block]]
                                    (when (seq (:entries block)) name))
                                  blocks))
        total (max (count block-maturity) 1)
        coverage-pct (quot (* (count covered-blocks) 100) total)]
    (assoc state
           :block_maturity block-maturity
           :at_risk at-risk
           :coverage_pct coverage-pct)))

(defn report [state]
  (let [meta (:meta state)
        maturity (:block_maturity state)
        at-risk (:at_risk state)
        ingest (:ingest_result state)
        total (max (count maturity) 1)
        avg (if (seq maturity)
              (/ (reduce + (vals maturity)) total)
              0.0)
        lines (concat
               ["jp-ashiba Lean BMC Scoring Report"
                (str "Iteration : " (or (:iteration meta) "?") " (" (or (:date meta) "?") ")")
                (str "Phase     : " (or (:phase meta) ""))
                (str "Graph     : " (:graph_id state))
                (str "Coverage  : " (count maturity) " blocks (coverage=" (:coverage_pct state) "%)")
                (format "Maturity  : %.1f / 5.0 (%d blocks scored)" (double avg) total)
                (str "At-Risk   : " (count at-risk) " unvalidated hypotheses")
                "Per-Block Maturity:"]
               (map (fn [[name m]]
                      (format "  %-22s %d/5%s"
                              name
                              m
                              (if (< m 5) " <- next" "")))
                    (sort-by key maturity))
               ["kotoba Datomic persistence:"
                (str "  datoms  : " (get ingest :datomCount 0))
                (str "  commits : " (count (get ingest :commits [])))
                (str "  last_tx : " (or (get ingest :lastCommit) "-"))])
        text (str/join "\n" lines)]
    (assoc state :report text)))

(defn run-bmc [path]
  (-> (load-bmc path)
      ingest-summary
      score
      report))
