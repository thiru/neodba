(ns neodba.writer
  "Write database results to screen, file, etc. in various formats."
  (:require
    [clojure.string :as str]
    [neodba.config :as cfg]
    [neodba.utils.common :as u]
    [neodba.utils.results :as r]))

(set! *warn-on-reflection* true) ; for graalvm

(defn format-header
  [config user-input result]
  (let [active-db-spec (cfg/get-active-db-spec config)]
    (str
      (str/trim
        (str
          (when (:print-config-info config)
            (format "**%s**: *%s* / *%s* / *%s*"
                    (-> active-db-spec :name name str/upper-case)
                    (:host active-db-spec)
                    (:dbname active-db-spec)
                    (:schema active-db-spec)))
          (when (:print-table-counts config)
            (format " `%d x %d`"
              (or (:col-count result) 0)
              (or (:row-count result) 0)))
          (when (:print-user-input config)
            (format "\n```sql\n%s\n```" (str/replace user-input #"\n+" " ")))))
      "\n")))

(defn print-r
  [result user-input config output-fmt]
  (let [result (if (= :plain output-fmt)
                 result
                 (r/prepend-msg result (format-header config user-input result)))]
    (when (not (str/blank? (:write-to-file config)))
      (spit (:write-to-file config) (:message result)))
    (r/print-msg result)
    result))

(defn format-query-res
  "Format query result as a markdown table and capture row count."
  [query-res output-fmt]
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
  [sql-res user-input config & {:keys [output-fmt]
                                :or {output-fmt :markdown}}]
  (try
    (let [res (r/while-success-> (format-query-res sql-res output-fmt)
                                 (print-r user-input config output-fmt))]
      (when (r/failed? res)
        (print-r res user-input config output-fmt)))
    (catch Exception ex
      (binding [*out* *err*]
        (let [msg (format "An error occurred while running `%s`\n\n%s"
                          user-input
                          (.toString ex))]
          (print-r (r/r :error msg) user-input config output-fmt))))))

