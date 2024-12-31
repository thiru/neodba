(ns neodba.jsonrpc-helpers
  "Helper functions for `neodba.jsonrpc`."
  (:require
    [clojure.spec.alpha :as s]
    [clojure.string :as str]
    [neodba.utils.common :as u]
    [specin.core :refer [apply-specs s< s>]]))

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

(defn str->headers
  [header-str]
  (->> header-str
       (str/trim)
       (str/split-lines)
       (mapv #(str/split % #":\s*"))
       (reduce (fn [acc kvp]
                 (let [k (-> (first kvp)
                             str/lower-case
                             keyword)
                       v (if (= k :content-length)
                           (u/parse-int (second kvp) :fallback -1)
                           (second kvp))]
                   (assoc acc k v)))
               {})))

(defn read-headers
  "Read JSON-RPC headers from stdin."
  {:ret (s> (s/coll-of string?))}
  []
  ;; Assunimg 1K is sufficient space for the header
  (let [buffer (byte-array 1024)]
    (loop [bytes-read 0
           last-byte 0]
      (cond
        (= -1 last-byte)
        (-> buffer
            (bytes->str (dec bytes-read))
            (str->headers))

        (end-of-header? buffer bytes-read)
        (-> buffer
            (bytes->str bytes-read)
            (str->headers))

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
