(ns neodba.dba
  "TODO"
  (:require
    [clojure.edn :as edn]
    [clojure.pprint :as pp]
    [next.jdbc :as jdbc]
    [neodba.utils.common :as u]))


(set! *warn-on-reflection* true) ; for graalvm


(defn get-connection-map []
  (-> (slurp "db-conn.edn")
      (edn/read-string)))

(defn execute [sql]
  (let [db (get-connection-map)]
    (with-open [con (jdbc/get-connection db)]
      (jdbc/execute! con [sql]))))

(comment
  (pp/pprint (execute "select * from users")))
