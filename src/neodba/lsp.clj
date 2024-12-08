(ns neodba.lsp
  "LSP server implementation."
  (:require
    [clojure.edn :as edn]
    [neodba.json-rpc :as rpc]
    [neodba.utils.common :as u]
    [neodba.utils.logging :as log :refer [log]]
    [neodba.utils.results :as r]))

(set! *warn-on-reflection* true) ; for graalvm

(defn handle-initialise
  "Handle initialisation request."
  [id]
  (rpc/send-message (rpc/success-result id {:capabilities {:textDocumentSync 1}})))

(defn handle-request
  "Handle incoming JSON-RPC request."
  [msg]
  (let [{:strs [id method params]} msg]
    (case method
      "initialize" (handle-initialise id)
      "textDocument/didOpen" (log (r/r :info (str "Document opened: " params "\n")))
      "textDocument/didChange" (log (r/r :info (str "Document changed: " params "\n")))
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
