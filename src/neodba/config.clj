(ns neodba.config
  "Config file functionality."
  (:require
    [clojure.edn :as edn]
    [neodba.utils.common :as u]
    [neodba.utils.results :as r]))

(set! *warn-on-reflection* true) ; for graalvm

(def config-file "neodba.edn")

(defn process-config
  "Apply any necessary transformations to the config such as merging connection maps."
  [config]
  (update config
          :connections
          #(reduce
             (fn [acc [k v]]
               (assoc acc
                      k
                      (assoc (if (nil? (:merge-with v))
                               v
                               (merge (get % (:merge-with v)) v))
                             :name k)))
             {}
             %)))

(defn load-config-file
  "Load EDN file specifying database connections and other settings from the CWD."
  []
  (let [file (u/slurp-file config-file)]
    (if (r/failed? file)
      (r/r :error
           (format "Config file, %s was not found in CWD: %s"
                   config-file
                   (System/getProperty "user.dir")))
      (-> (edn/read-string file)
          (process-config)))))

(defn get-active-db-spec
  "Get the active database spec from the given config."
  [config]
  (let [active-db-spec (:active-connection config)]
    (loop [db-specs (:connections config)]
      (let [[conn-name db-spec] (first db-specs)]
        (if (or (nil? conn-name)
                (= active-db-spec conn-name))
          db-spec
          (recur (next db-specs)))))))

(comment
  (-> (load-config-file) (process-config) (get-active-db-spec)))
