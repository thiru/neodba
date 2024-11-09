(ns neodba.main
  "Entry-point into the app."
  (:require
    [neodba.cli :as cli]
    [neodba.utils.common :as u]
    [specin.core :refer [apply-specs s>]])
  (:gen-class))

(set! *warn-on-reflection* true) ; for graalvm

(defn -main
  "Entry-point into the app.

  Returns 0 on success, otherwise a positive integer."
  {:ret (s> nat-int?)}
  [& args]
  (-> (or args [])
      cli/run
      u/exit!))

(apply-specs)
