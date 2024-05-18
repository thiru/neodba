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


(defn get-connection-spec []
  (let [file (u/slurp-file db-spec-file)]
    (if (r/failed? file)
      (r/print-msg
        (r/r :error
             (format "File specifying database connection (%s) was not found in CWD"
                     db-spec-file)))
      (edn/read-string file))))

(defn execute [sql]
  (if (str/blank? sql)
    []
    (when-let [db (get-connection-spec)]
      (with-open [con (jdbc/get-connection db)]
        (jdbc/execute! con
                       [sql]
                       {:builder-fn jdbc-rs/as-unqualified-maps})))))

(defn get-catalogs []
  (when-let [db (get-connection-spec)]
    (with-open [con (jdbc/get-connection db)]
      (-> (.getMetaData con) ; produces java.sql.DatabaseMetaData
          ;; return a java.sql.ResultSet describing all tables and views:
          (.getCatalogs)
          (jdbc-rs/datafiable-result-set con {:builder-fn jdbc-rs/as-unqualified-lower-maps})
          (->> (map (fn [x] {:name (:table_cat x)})))))))

(defn get-schemas []
  (when-let [db (get-connection-spec)]
    (with-open [con (jdbc/get-connection db)]
      (-> (.getMetaData con) ; produces java.sql.DatabaseMetaData
          ;; return a java.sql.ResultSet describing all tables and views:
          (.getSchemas)
          (jdbc-rs/datafiable-result-set con {:builder-fn jdbc-rs/as-unqualified-lower-maps})
          (->> (map (fn [x] {:name (:table_schem x)})))))))

(defn get-tables []
  (when-let [db (get-connection-spec)]
    (with-open [con (jdbc/get-connection db)]
      (-> (.getMetaData con) ; produces java.sql.DatabaseMetaData
          ;; return a java.sql.ResultSet describing all tables:
          (.getTables nil nil nil (into-array ["TABLE"]))
          (jdbc-rs/datafiable-result-set con {:builder-fn jdbc-rs/as-unqualified-lower-maps})
          (->> (map (fn [x] {:name (:table_name x)})))))))

(defn get-views []
  (when-let [db (get-connection-spec)]
    (with-open [con (jdbc/get-connection db)]
      (-> (.getMetaData con) ; produces java.sql.DatabaseMetaData
          ;; return a java.sql.ResultSet describing all views:
          (.getTables nil nil nil (into-array ["VIEW"]))
          (jdbc-rs/datafiable-result-set con {:builder-fn jdbc-rs/as-unqualified-lower-maps})
          (->> (map (fn [x] {:name (:table_name x)})))))))

(defn print-rs [query-res]
  (let [rows (mapv #(update-keys % name) query-res)]
    (if (empty? rows)
      (println "NO RESULTS")
      (pp/print-table rows))))

(comment
  ;; Sample databases taken from here: https://github.com/lerocha/chinook-database
  ;; Start Postgres instance in docker: docker run --rm -P -p 127.0.0.1:5432:5432 -e POSTGRES_PASSWORD="postgres" --name pg postgres
  (print-rs (execute "select * from Artist limit 5"))
  (print-rs (get-schemas))
  (print-rs (get-tables)))
