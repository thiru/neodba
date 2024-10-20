(ns neodba.config
  "Config file functionality."
  (:require
    [clojure.edn :as edn]
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

