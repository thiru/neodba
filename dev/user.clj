(ns user
  "Initial namespace loaded when using a REPL (e.g. using `clj`)."
  {:clj-kondo/config '{:linters {:unused-namespace {:level :off}
                                 :unused-referred-var {:level :off}}}}
  (:require
    [clojure.edn :as edn]
    [clojure.java.io :as io]
    [clojure.reflect :as reflect]
    [clojure.repl :refer [doc]]
    [clojure.spec.alpha :as s]
    [clojure.string :as str]
    [neodba.api :as api]
    [neodba.config :as cfg]
    [neodba.dba :as dba]
    [neodba.jsonrpc :as rpc]
    [neodba.lsp :as lsp]
    [neodba.utils.common :as u]
    [neodba.utils.logger :refer [log] :as logger]
    [neodba.utils.results :as r]
    [puget.printer :as puget]
    [rebel-readline.main :as rebel]
    [utils.nrepl :as nrepl]
    [utils.printing :as printing :refer [PP]]))


(defonce initialised? (atom false))

(when (not @initialised?)
  (reset! initialised? true)
  (printing/install-expound-printer)
  (nrepl/start-server)
  ;; Blocking call:
  (rebel/-main)
  (nrepl/stop-server)
  ;; HACK: rebel-readline causes process to hang and not quit without this:
  (System/exit 0))
