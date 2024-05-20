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


(defn get-db-spec
  "Get file specifying database connection string from the CWD."
  []
  (let [file (u/slurp-file db-spec-file)]
    (if (r/failed? file)
      (r/r :error
           (format "File specifying database connection (%s) was not found in CWD (%s)"
                   db-spec-file
                   (System/getProperty "user.dir")))
      (edn/read-string file))))

(defn execute-sql
  [db-spec sql]
  (if (str/blank? sql)
    []
    (with-open [con (jdbc/get-connection db-spec)]
      (jdbc/execute! con
                     [sql]
                     {:builder-fn jdbc-rs/as-unqualified-maps}))))

(defn get-catalogs
  [db-spec]
  (with-open [con (jdbc/get-connection db-spec)]
    (-> (.getMetaData con)
        (.getCatalogs)
        (jdbc-rs/datafiable-result-set con {:builder-fn jdbc-rs/as-unqualified-lower-maps})
        (->> (map (fn [x] {:name (:table_cat x)}))))))

(defn get-schemas
  [db-spec]
  (with-open [con (jdbc/get-connection db-spec)]
    (-> (.getMetaData con)
        (.getSchemas)
        (jdbc-rs/datafiable-result-set con {:builder-fn jdbc-rs/as-unqualified-lower-maps})
        (->> (map (fn [x] {:name (:table_schem x)}))))))

(defn get-tables
  [db-spec]
  (with-open [con (jdbc/get-connection db-spec)]
    (-> (.getMetaData con)
        (.getTables nil nil nil (into-array ["TABLE"]))
        (jdbc-rs/datafiable-result-set con {:builder-fn jdbc-rs/as-unqualified-lower-maps})
        (->> (map (fn [x] {:name (:table_name x)}))))))

(defn get-views
  [db-spec]
  (with-open [con (jdbc/get-connection db-spec)]
    (-> (.getMetaData con)
        (.getTables nil nil nil (into-array ["VIEW"]))
        (jdbc-rs/datafiable-result-set con {:builder-fn jdbc-rs/as-unqualified-lower-maps})
        (->> (map (fn [x] {:name (:table_name x)}))))))

(defn print-rs
  [query-res]
  (let [rows (mapv #(update-keys % name) query-res)]
    (if (empty? rows)
      (println "NO RESULTS")
      (pp/print-table rows)))
  (r/r :success ""))

(defn print-with-db-spec
  [sql-exec]
  (let [res (r/while-success-> (get-db-spec)
                               (sql-exec)
                               (print-rs))]
    (when (r/failed? res)
      (r/print-msg res))))

(comment
  ;; Sample databases taken from here: https://github.com/lerocha/chinook-database
  (print-with-db-spec #(execute-sql % "select * from artist limit 5"))
  (print-with-db-spec get-schemas)
  (print-with-db-spec get-tables))
