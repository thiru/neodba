(ns neodba.dba
  "Database access layer."
  (:require
    [clojure.edn :as edn]
    [clojure.pprint :as pp]
    [clojure.string :as str]
    [next.jdbc :as jdbc]
    [next.jdbc.result-set :as jdbc-rs]
    [neodba.utils.common :as u]))


(set! *warn-on-reflection* true) ; for graalvm


(defn get-connection-map []
  (-> (slurp "db-spec.edn")
      (edn/read-string)))

(defn execute [sql]
  (if (str/blank? sql)
    []
    (let [db (get-connection-map)]
      (with-open [con (jdbc/get-connection db)]
        (jdbc/execute! con
                       [sql]
                       {:builder-fn jdbc-rs/as-unqualified-lower-maps})))))

(defn print-rs [query-res]
  (pp/print-table query-res))

(defn get-schemas []
  (let [db (get-connection-map)]
    (with-open [con (jdbc/get-connection db)]
      (-> (.getMetaData con) ; produces java.sql.DatabaseMetaData
          ;; return a java.sql.ResultSet describing all tables and views:
          (.getSchemas)
          (jdbc-rs/datafiable-result-set db)))))

(defn get-tables []
  (let [db (get-connection-map)]
    (with-open [con (jdbc/get-connection db)]
      (-> (.getMetaData con) ; produces java.sql.DatabaseMetaData
          ;; return a java.sql.ResultSet describing all tables and views:
          (.getTables nil nil nil (into-array ["TABLE" "VIEW"]))
          (jdbc-rs/datafiable-result-set db)))))

(comment
  ;; Sample databases taken from here: https://github.com/lerocha/chinook-database
  ;; Start Postgres instance in docker: docker run --rm -P -p 127.0.0.1:5432:5432 -e POSTGRES_PASSWORD="postgres" --name pg postgres
  (print-rs (execute "select * from Artist limit 5"))
  (print-rs (get-schemas))
  (print-rs (get-tables)))
