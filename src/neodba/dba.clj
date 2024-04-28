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

(comment
  (print-rs (execute "select * from users")))
