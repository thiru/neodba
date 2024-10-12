(ns neodba.dba
  "Database access layer."
  (:require
    [clojure.edn :as edn]
    [clojure.pprint :as pp]
    [clojure.string :as str]
    [next.jdbc :as jdbc]
    [next.jdbc.result-set :as jdbc-rs]
    [neodba.utils.common :as u]
    [neodba.utils.results :as r]))


(set! *warn-on-reflection* true) ; for graalvm


(def db-spec-file "db-spec.edn")


(defn read-db-specs
  "Read EDN file specifying database connections from the CWD."
  []
  (let [file (u/slurp-file db-spec-file)]
    (if (r/failed? file)
      (r/r :error
           (format "File specifying database connection (%s) was not found in CWD (%s)"
                   db-spec-file
                   (System/getProperty "user.dir")))
      (edn/read-string file))))

(defn get-active-db-spec
  "Get the active database spec from all defined in `db-specs`.
  I.e. the first one with `:active` being truthy."
  [db-specs]
  (if (empty? db-specs)
    (first db-specs)
    (or (some #(when (get % :active)
                 %)
              db-specs)
        (first db-specs))))

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

(defn print-rs
  [query-res]
  (let [rows (mapv #(update-keys % name) query-res)]
    (if (empty? rows)
      (println "NO RESULTS")
      (pp/print-table rows)))
  (r/r :success ""))

(defn print-with-db-spec
  [sql-exec]
  (let [res (r/while-success-> (read-db-specs)
                               (get-active-db-spec)
                               (sql-exec)
                               (print-rs))]
    (when (r/failed? res)
      (r/print-msg res))))

(comment
  ;; Sample databases taken from here: https://github.com/lerocha/chinook-database
  (print-with-db-spec #(execute-sql % "select * from artist limit 5"))
  (print-with-db-spec get-database-info)
  (print-with-db-spec get-catalogs)
  (print-with-db-spec get-schemas)
  (print-with-db-spec get-tables)
  (print-with-db-spec get-views)
  (print-with-db-spec get-functions)
  (print-with-db-spec #(get-function-columns % "add"))
  (print-with-db-spec get-procedures)
  (print-with-db-spec #(get-procedure-columns % "insert_data"))
  (print-with-db-spec #(get-columns % "artist"))
  (print-with-db-spec #(get-columns % "artist" :verbose? true)))
