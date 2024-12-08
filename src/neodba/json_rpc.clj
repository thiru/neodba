(ns neodba.json-rpc
  "JSON-RPC public API."
  (:require
    [clojure.data.json :as json]
    [clojure.spec.alpha :as s]
    [clojure.string :as str]
    [neodba.json-rpc-helpers :as h]
    [neodba.utils.common :as u]
    [neodba.utils.logging :refer [log] :as logging]
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
        (log (r/r :debug (str "CLIENT:\n" headers "\n" body "\n")))
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
    (log (r/r :debug (str "SERVER:\n" rpc-msg "\n")))
    (print rpc-msg)
    (flush)
    rpc-msg))

(apply-specs)
