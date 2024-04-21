(ns neodba.server
  "Web server intialisation, etc."
  (:refer-clojure :exclude [defn])
  (:require
    [neodba.utils.common :as u]
    [neodba.utils.log :refer [log]]
    [neodba.utils.specin :refer [defn]]
    [neodba.utils.results :as r]))

(set! *warn-on-reflection* true) ; for graalvm


(def default-port 5555)
(defonce server-obj (atom nil))


(defn start! [port]
  (let [port (or port default-port)]
    (reset! server-obj :TODO)
    (log (r/r :info (str "Server started on port " port)) :force? true)))

(defn stop! []
  (if (nil? @server-obj)
    (log (r/r :warn "Server isn't running"))
    ;; graceful shutdown: wait 100ms for existing requests to be finished
    ;; :timeout is optional, when no timeout, stop immediately
    (do
      (@server-obj :timeout 100)
      (reset! server-obj nil)
      (log (r/r :info "Server stopped") :force? true))))
