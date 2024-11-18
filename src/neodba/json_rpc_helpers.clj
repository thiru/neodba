(ns neodba.json-rpc-helpers
  "Helper functions for `neodba.json-rpc`."
  (:require
    [clojure.java.io :as io]
    [clojure.spec.alpha :as s]
    [clojure.string :as str]
    [neodba.utils.common :as u]
    [neodba.utils.logging :as logging]
    [specin.core :refer [apply-specs s< s>]]))

(defonce
  ^{:doc "Initialise logger, sending output to a custom file."
    :private true}
  _init-logger
  (let [log-file (io/file "lsp.log")]
    (when (.exists log-file)
      (io/delete-file log-file))
    (logging/set-log-level :debug)
    (reset! logging/output-to log-file)))

(defn bytes->str
  "Convert the given byte array to a UTF-8 string."
  {:args (s< :data bytes? :length pos-int?)
   :ret (s> string?)}
  [data length]
  (String. data 0 length "UTF-8"))

(defn end-of-header?
  "According to the spec, the header section ends when '\r\n\r\n' is encountered."
  {:args (s< :data bytes? :length pos-int?)
   :ret (s> boolean?)}
  [data length]
  (boolean
    (and (>= length 4)
         ;; 13 = \r and 10 = \n
         (= 13 (aget data (- length 4)))
         (= 10 (aget data (- length 3)))
         (= 13 (aget data (- length 2)))
         (= 10 (aget data (- length 1))))))

(defn bytes->headers
  {:args (s< :data bytes? :length pos-int?)
   :ret (s> (s/coll-of string?))}
  [data length]
  (-> (bytes->str data length)
      str/trim
      str/split-lines))

(defn read-headers
  "Read JSON-RPC headers from stdin."
  {:ret (s> (s/coll-of string?))}
  []
  (let [buffer (byte-array 1024)] ; 1K should be plenty for the header
    (loop [bytes-read 0
           last-byte 0]
      (cond
        (= -1 last-byte)
        (bytes->headers buffer (dec bytes-read))

        (end-of-header? buffer bytes-read)
        (-> (bytes->headers buffer bytes-read))

        :else
        (let [byte (.read System/in)]
          (aset-byte buffer bytes-read byte)
          (recur (inc bytes-read) byte))))))

(defn read-body
  "Read the specified number of bytes from stdin as a string."
  {:args (s< :num-bytes pos-int?)
   :ret (s> string?)}
  [num-bytes]
  (let [buffer (byte-array num-bytes)
        bytes-read (.read System/in buffer 0 num-bytes)]
    (if (= bytes-read -1)
      ""
      (bytes->str buffer num-bytes))))

(apply-specs)
