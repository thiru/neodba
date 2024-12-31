(ns neodba.jsonrpc
  "JSON-RPC public API."
  (:require
    [clojure.data.json :as json]
    [clojure.spec.alpha :as s]
    [neodba.jsonrpc-helpers :as h]
    [neodba.utils.common :as u]
    [neodba.utils.logger :refer [log]]
    [neodba.utils.results :as r]
    [specin.core :refer [apply-specs s< s>]]))

(set! *warn-on-reflection* true) ; for graalvm

(defn error-result
  "Create an error result map."
  {:args (s< :id number?
             :error (s/nilable map?))
   :ret (s> map?)}
  [id error]
  {:jsonrpc "2.0"
   :id id
   :error error})

(defn success-result
  "Create a success result map."
  {:args (s< :id number?
             :result (s/nilable map?))
   :ret (s> map?)}
  [id result]
  {:jsonrpc "2.0"
   :id id
   :result result})

(defn read-message
  "Read a JSON-RPC message from the client (from stdin)."
  {:ret (s> string?)}
  []
  (let [headers (h/read-headers)]
    (if (pos-int? (:content-length headers))
      (let [body (-> (h/read-body (:content-length headers)) json/read-str)]
        (log (r/r :debug "*Client* headers:"))
        (log (r/r :debug headers) :lang :json)
        (log (r/r :debug "*Client* body:"))
        (log (r/r :debug body) :lang :json)
        body)
      (throw (ex-info "Provided JSON-RPC headers are invalid. Unable to parse 'Content-Length'."
                      {:headers headers})))))

(defn send-message
  "Send a JSON-RPC message to the client (to stdout)."
  {:args (s< :msg (s/nilable map?))
   :ret (s> string?)}
  [msg]
  (let [json-str (json/write-str (or msg ""))
        content-length (count (.getBytes json-str "UTF-8"))
        rpc-msg (str "Content-Length: " content-length "\r\n\r\n" json-str)]
    (log (r/r :debug (str "*Server*:\n`Content-Length: " content-length "`")))
    (log (r/r :debug json-str) :lang :json)
    (print rpc-msg)
    (flush)
    rpc-msg))

(apply-specs)
