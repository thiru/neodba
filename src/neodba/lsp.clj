(ns neodba.lsp
  "LSP server implementation."
  (:require
    [clojure.edn :as edn]
    [clojure.java.io :as io]
    [clojure.string :as str]
    [neodba.jsonrpc :as rpc]
    [neodba.utils.common :as u]
    [neodba.utils.logger :as logger :refer [log]]
    [neodba.utils.results :as r]))

(set! *warn-on-reflection* true) ; for graalvm

(def version (-> (slurp "VERSION") str/trim))

(defn init-logger
  "Initialise logger, sending output to a local file.
  We log to a file for the LSP server since we can't really use stdio as it is
  the primary method of communication."
  []
  (let [log-file (io/file "lsp-log.md")]
    (when (.exists log-file)
      (io/delete-file log-file))
    (logger/set-log-level :debug)
    (reset! logger/output-to log-file)))

(defn handle-initialise
  "Handle initialisation request."
  [id]
  (rpc/send-message (rpc/success-result id {:capabilities {:textDocumentSync 1
                                                           :hoverProvider true}
                                            :serverinfo {:name "neodba"
                                                         :version version}})))

(defn handle-request
  "Handle incoming JSON-RPC request."
  [msg]
  (let [{:strs [id method params]} msg]
    (case method
      "initialize"
      (handle-initialise id)

      ;; This is a notification that can be dropped
      "initialized"
      (log (r/r :info "Dropping initialised notification\n"))

      "shutdown"
      (do
        (log (r/r :info "Shutdown request received"))
        (rpc/send-message (rpc/success-result id nil)))

      "exit"
      (do
        (log (r/r :info "LSP server exiting"))
        (System/exit 0))

      "textDocument/hover"
      (do
        (log (r/r :info (str "Hover request sent: " params "\n")))
        (rpc/send-message (rpc/success-result id "TODO: hover")))

      "textDocument/didOpen"
      (log (r/r :info (str "Dropping 'document opened' notification: " params "\n")))

      "textDocument/didChange"
      (log (r/r :info (str "Dropping 'document changed' notification: " params "\n")))

      "textDocument/didClose"
      (log (r/r :info (str "Dropping 'document closed' notification: " params "\n")))

      (rpc/send-message (rpc/success-result id nil)))))

(defn start-read-loop
  "Read from stdin forever."
  []
  (log (r/r :info "LSP server started\n"))
  (loop []
    (let [msg (rpc/read-message)]
      (handle-request msg))
    (recur)))

(comment
  (-> (slurp "test/msg.edn")
      (edn/read-string)
      (handle-request)))
