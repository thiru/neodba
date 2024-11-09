(ns neodba.utils.results
  "Generic facilities around reporting and validation."
  (:require
    [clojure.spec.alpha :as s]
    [specin.core :refer [apply-specs s< s>]]))


(set! *warn-on-reflection* true) ; for graalvm


(def levels
  "A generic map of levels that can be used for logging, reporting, etc.

  Negative values represent some level of failure. Non-negative values are non-failures and may
  indicate success or simply be informational.

  Additionally the following order is indicative of logging verbosity, from less to more."
  {:fatal -2
   :error -1
   :warn 0
   :success 1
   :info 2
   :debug 3
   :trace 4})

(s/def ::level #(contains? levels %))


;; NOTE: Allow a message to be any type, since we can usually get a meaningful string
;; representation of most objects.
(s/def ::message any?)
(s/def ::result (s/keys :req-un [::level ::message]))


(defn r
  "Creates a map representing the result of some operation.

  I deliberately chose to use a very short function name because it will be used heavily throughout
  the codebase. Perhaps in limited cases the length of the name of a thing should be inversely
  proportional to its frequency of use. The other potential short name that might work is 'res',
  but it seems ambiguous in some environments (e.g. response for the web).

  * `level`
    * A value specifying the success/failure level
    * By convention, keys that map to:
      * _negative_ values are considered some level of failure
      * while _non-negative_ values are considered informational or successful
  * `message`
    * A message describing the result (usually a string)
  * `extra`
    * Additional key/value pairs to merge into the result map"
  {:args (s< :level ::level
             :message (s/? ::message)
             :extra (s/? map?))
   :ret (s> ::result)}
  ([level]
   (r level ""))
  ([level message]
   {:level level :message message})
  ([level message extra]
   (merge (r level message) extra)))

(defmulti success?
  "Determine whether a given object represents a successful outcome.

  An object is considered successful in all cases except the following:
  * `nil`
  * `false`
  * An instance of `Throwable`
  * A result map where the value of `:level` is a keyword defined in `levels` which maps to a
  negative number"
  class)

(defmethod success? nil nil-type [_]
  false)

(defmethod success? boolean boolean-type [bool]
  bool)

(defmethod success? Throwable throwable-type [_]
  false)

(defmethod success? clojure.lang.PersistentArrayMap map-type [maybe-r]
  (if (s/valid? ::result maybe-r)
    (pos-int? (get levels (:level maybe-r) (:error levels)))
    true))

(defmethod success? :default [_]
  true)

(defn failed?
  "Determine whether the given object represents a failure outcome.

  This is basically the opposite of `success?`."
  [obj]
  (not (success? obj)))

(defn warned?
  "Determine whether the given object represents a warning or failure outcome.

  This is basically the same as `failed?` except also returns true if `obj` is a result map where
  `:level` is `:warn`."
  [obj]
  (or (= :warn (:level obj))
      (failed? obj)))

(defn append-msg
  "Append `msg` to the end of the given result's message."
  [result msg]
  (assoc result :message (str (:message result) msg)))

(defn prepend-msg
  "Prepend `msg` to the beggining of the given result's message."
  [result msg]
  (assoc result :message (str msg (:message result))))

(defn print-msg
  "Prints the message of the given result to stdout or stderr accordingly. No printing is done if
  the message is empty. Warnings are printed to stderr."
  {:args (s< :result ::result)
   :ret (s> nil?)}
  [result] (when (not (empty? (or (:message result) "")))
             (if (or (failed? result)
                     (warned? result))
               (binding [*out* *err*]
                 (println (:message result)))
               (println (:message result)))))

(defmacro while-success->
  "Similar to `some->` except works on result maps. I.e. when first-r is successful, threads it
  into the first form (via `->`), and when that result is successful, through the next etc.
  Useful for short-circuiting on a failure result."
  [first-r & forms]
  (let [g (gensym)
        steps (map (fn [step] `(if (failed? ~g) ~g (-> ~g ~step)))
                   forms)]
    `(let [~g ~first-r
           ~@(interleave (repeat g) (butlast steps))]
       ~(if (empty? steps)
          g
          (last steps)))))

(defmacro while-success->>
  "Similar to `some->>` except works on result maps. I.e. when first-r is successful, threads it
  into the first form (via `->>`), and when that result is successful, through the next etc.
  Useful for short-circuiting on a failure result."
  {:added "1.5"}
  [expr & forms]
  (let [g (gensym)
        steps (map (fn [step] `(if (failed? ~g) ~g (->> ~g ~step)))
                   forms)]
    `(let [~g ~expr
           ~@(interleave (repeat g) (butlast steps))]
       ~(if (empty? steps)
          g
          (last steps)))))

(comment
  (prepend-msg (r :info "original") "prepended - "))

(apply-specs)
