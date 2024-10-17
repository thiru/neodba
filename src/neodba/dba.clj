(ns neodba.dba
  "Database access layer."
  (:require
    [clojure.edn :as edn]
    [clojure.string :as str]
    [next.jdbc :as jdbc]
    [next.jdbc.result-set :as jdbc-rs]
    [neodba.postgresql :as pg]
    [neodba.utils.common :as u]
    [neodba.utils.results :as r]))


(set! *warn-on-reflection* true) ; for graalvm


(def config-file "neodba.edn")


(defn read-config-file
  "Read EDN file specifying database connections from the CWD."
  []
  (let [file (u/slurp-file config-file)]
    (if (r/failed? file)
      (r/r :error
           (format "Config file, %s was not found in CWD: %s"
                   config-file
                   (System/getProperty "user.dir")))
      (edn/read-string file))))

(defn get-active-db-spec
  "Get the active database spec from the given config.
  I.e. the first one with `:active` being truthy."
  [config]
  (let [db-specs (:db-specs config)]
    (if (empty? db-specs)
      (first db-specs)
      (or (some #(when (get % :active)
                   %)
                db-specs)
          (first db-specs)))))

(defn get-connection
  "Get a connection object for the given database spec and set the schema (if applicable)"
  ^java.sql.Connection [db-spec]
  (let [con (jdbc/get-connection db-spec)
        schema (:schema db-spec)]
    (when (not (str/blank? schema))
      (.setSchema con schema))
    con))

(defn execute-sql
  [db-spec sql]
  (if (str/blank? sql)
    []
    (with-open [con (get-connection db-spec)]
      (jdbc/execute! con
                     [sql]
                     {:builder-fn jdbc-rs/as-unqualified-maps}))))

(defn get-database-info
  [db-spec]
  (with-open [con (get-connection db-spec)]
    (let [md (.getMetaData con)]
      [{:name "database" :value (.getDatabaseProductName md)}
       {:name "version" :value (.getDatabaseProductVersion md)}
       {:name "url" :value (.getURL md)}
       {:name "catalog" :value (.getCatalog con)}
       {:name "schema" :value (.getSchema con)}])))

(defn get-catalogs
  [db-spec]
  (with-open [con (get-connection db-spec)]
    (-> (.getMetaData con)
        (.getCatalogs)
        (jdbc-rs/datafiable-result-set con {:builder-fn jdbc-rs/as-unqualified-lower-maps})
        (->> (map (fn [x] {:name (:table_cat x)}))))))

(defn get-schemas
  [db-spec]
  (with-open [con (get-connection db-spec)]
    (-> (.getMetaData con)
        (.getSchemas)
        (jdbc-rs/datafiable-result-set con {:builder-fn jdbc-rs/as-unqualified-lower-maps})
        (->> (map (fn [x] {:name (:table_schem x)}))))))

(defn get-tables
  [db-spec]
  (with-open [con (get-connection db-spec)]
    (-> (.getMetaData con)
        (.getTables nil (:schema db-spec) nil (into-array ["TABLE"]))
        (jdbc-rs/datafiable-result-set con {:builder-fn jdbc-rs/as-unqualified-lower-maps})
        (->> (map (fn [x] {:name (:table_name x)}))))))

(defn get-views
  [db-spec]
  (with-open [con (get-connection db-spec)]
    (-> (.getMetaData con)
        (.getTables nil (:schema db-spec) nil (into-array ["VIEW"]))
        (jdbc-rs/datafiable-result-set con {:builder-fn jdbc-rs/as-unqualified-lower-maps})
        (->> (map (fn [x] {:name (:table_name x)}))))))

(defn get-view-defn
  [db-spec view-name]
  (let [sql (condp = (:dbtype db-spec)
              "postgres"
              (pg/get-view-defn view-name)
              (throw (ex-info (str "Don't know how to get view definition for dbtype: " (:dbtype db-spec)) {})))]
    (with-open [con (get-connection db-spec)]
      (jdbc/execute! con
                     [sql]
                     {:builder-fn jdbc-rs/as-unqualified-maps}))))

(defn get-columns
  [db-spec table-name & {:keys [verbose?]}]
  (with-open [con (get-connection db-spec)]
    (-> (.getMetaData con)
        (.getColumns nil (:schema db-spec) table-name nil)
        (jdbc-rs/datafiable-result-set con {:builder-fn jdbc-rs/as-unqualified-lower-maps})
        (->> (map (fn [x]
                    (if verbose?
                      x
                      {:name (:column_name x)
                       :type (:type_name x)
                       :size (:column_size x)
                       :nullable? (:is_nullable x)})))))))

(defn get-function-columns
  [db-spec func-name]
  (with-open [con (get-connection db-spec)]
    (-> (.getMetaData con)
        (.getFunctionColumns nil (:schema db-spec) func-name nil)
        (jdbc-rs/datafiable-result-set con {:builder-fn jdbc-rs/as-unqualified-lower-maps})
        (->> (map (fn [x] {:name (:column_name x)
                           :type (:type_name x)}))))))

(defn get-functions
  [db-spec]
  (with-open [con (get-connection db-spec)]
    (-> (.getMetaData con)
        (.getFunctions nil (:schema db-spec) nil)
        (jdbc-rs/datafiable-result-set con {:builder-fn jdbc-rs/as-unqualified-lower-maps})
        (->> (map (fn [x] {:name (:function_name x)
                           :type (:function_type x)
                           :columns (->> (get-function-columns db-spec (:function_name x))
                                         (mapv #(str (:name %) " " (:type %)))
                                         (str/join ", "))}))))))

(defn get-function-defn
  [db-spec func-name]
  (let [sql (condp = (:dbtype db-spec)
              "postgres"
              (pg/get-function-defn func-name (:schema db-spec))
              (throw (ex-info (str "Don't know how to get function definition for dbtype: " (:dbtype db-spec)) {})))]
    (with-open [con (get-connection db-spec)]
      (jdbc/execute! con
                     [sql]
                     {:builder-fn jdbc-rs/as-unqualified-maps}))))

(defn get-procedure-columns
  [db-spec proc-name]
  (with-open [con (get-connection db-spec)]
    (-> (.getMetaData con)
        (.getFunctionColumns nil (:schema db-spec) proc-name nil)
        (jdbc-rs/datafiable-result-set con {:builder-fn jdbc-rs/as-unqualified-lower-maps})
        (->> (map (fn [x] {:name (:column_name x)
                           :type (:type_name x)}))))))

(defn get-procedures
  [db-spec]
  (with-open [con (get-connection db-spec)]
    (-> (.getMetaData con)
        (.getProcedures nil (:schema db-spec) nil)
        (jdbc-rs/datafiable-result-set con {:builder-fn jdbc-rs/as-unqualified-lower-maps})
        (->> (map (fn [x] {:name (:procedure_name x)
                           :type (:procedure_type x)
                           :columns (->> (get-procedure-columns db-spec (:procedure_name x))
                                         (mapv #(str (:name %) " " (:type %)))
                                         (str/join ", "))}))))))

(defn process-query-res
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

(defn output-header
  [config result]
  (let [active-db-spec (get-active-db-spec config)]
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

(defn print-result
  [config result]
  (let [result (r/prepend-msg result (output-header config result))]
    (when (not (str/blank? (:write-to-file config)))
      (spit (:write-to-file config) (:message result)))
    (r/print-msg result)
    result))

(defn print-with-config
  [sql-exec & {:keys [output-fmt]
               :or {output-fmt :markdown}}]
  (let [config (read-config-file)]
    (try
      (let [res (r/while-success->> config
                                    (get-active-db-spec)
                                    (sql-exec)
                                    (process-query-res output-fmt)
                                    (print-result config))]
        (when (r/failed? res)
          (print-result config res)))
      (catch Exception ex
        (binding [*out* *err*]
          (let [msg (format "An error occurred while running `%s`\n\n%s"
                            (u/pretty-demunge sql-exec)
                            (.toString ex))]
            (print-result config (r/r :error msg))))))))

(comment
  ;; Sample databases taken from here: https://github.com/lerocha/chinook-database
  (print-with-config #(execute-sql % "select * from artist limit 5"))
  (print-with-config #(execute-sql % "bad-query"))
  (print-with-config get-database-info)
  (print-with-config get-catalogs)
  (print-with-config get-schemas)
  (print-with-config get-tables)
  (print-with-config get-views)
  (print-with-config get-functions)
  (print-with-config #(get-function-columns % "add"))
  (print-with-config #(get-function-defn % "add") :output-fmt :sql)
  (print-with-config get-procedures)
  (print-with-config #(get-procedure-columns % "insert_data"))
  (print-with-config #(get-columns % "artist"))
  (print-with-config #(get-columns % "artist" :verbose? true)))
