(ns neodba.writer
  "Write database results to screen, file, etc. in various formats."
  (:require
    [clojure.string :as str]
    [neodba.config :as cfg]
    [neodba.utils.common :as u]
    [neodba.utils.results :as r]))

(set! *warn-on-reflection* true) ; for graalvm

(defn output-header
  [config result]
  (let [active-db-spec (cfg/get-active-db-spec config)]
    (str
      (str/trim
        (str
          (when (:print-config-info config)
            (format " **%s**: *%s* / *%s* / *%s* "
                    (:name active-db-spec)
                    (:host active-db-spec)
                    (:dbname active-db-spec)
                    (:schema active-db-spec)))
          (when (:print-table-counts config)
            (format "`%d x %d` "
              (or (:row-count result) 0)
              (or (:col-count result) 0)))))
      "\n")))

(defn print-r
  [config result]
  (let [result (r/prepend-msg result (output-header config result))]
    (when (not (str/blank? (:write-to-file config)))
      (spit (:write-to-file config) (:message result)))
    (r/print-msg result)
    result))

(defn format-query-res
  "Format query result as a markdown table and capture row count."
  [output-fmt query-res]
  (if (empty? query-res)
    (r/r :warn "\n*NO RESULTS*" {:row-count 0, :col-count 0})
    (let [row-count (-> query-res count)
          all-keys (-> query-res first keys)
          col-count (-> all-keys count)]
      (cond
        (= :sql output-fmt)
        (let [rows (map #(format "```sql\n%s\n```"
                                 (-> (get % (first all-keys))
                                     (str/trim)))
                        query-res)]
          (r/r :success (str/join "\n\n" rows)))

        (= :plain output-fmt)
        (r/r :success
             (->> query-res
                  (map #(get % (first all-keys)))
                  (str/join "\n")))

        :else
        (-> (mapv #(update-keys % name) query-res)
            (u/as-markdown-table)
            (as-> $ (r/r :success $ {:row-count row-count
                                     :col-count col-count})))))))

(defn print-sql-res
  [sql-res config & {:keys [output-fmt]
                     :or {output-fmt :markdown}}]
  (try
    (let [res (r/while-success->> (format-query-res output-fmt sql-res)
                                  (print-r config))]
      (when (r/failed? res)
        (print-r config res)))
    (catch Exception ex
      (binding [*out* *err*]
        (let [msg (format "An error occurred while running `%s`\n\n%s"
                          (u/pretty-demunge :TODO #_sql-exec)
                          (.toString ex))]
          (print-r config (r/r :error msg)))))))

