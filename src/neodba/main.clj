(ns neodba.main
  "Entry-point into the app."
  (:refer-clojure :exclude [defn])
  (:require
    [neodba.cli :as cli]
    [neodba.utils.common :as u]
    [neodba.utils.specin :refer [defn]])
  (:gen-class))


(set! *warn-on-reflection* true) ; for graalvm


(defn -main
  "Entry-point into the app.

  Returns 0 on success, otherwise a positive integer."
  {:ret nat-int?}
  [& args]
  (-> (or args [])
      cli/run
      u/exit!))
