(ns neodba.utils.logger
  "A simple/naive logger."
  (:require
    [clojure.spec.alpha :as s]
    [clojure.string :as str]
    [neodba.utils.common :as u]
    [neodba.utils.results :as r]
    [puget.color.ansi :as ansi]
    [puget.printer :as puget]
    [specin.core :refer [apply-specs s< s>]]))


(set! *warn-on-reflection* true) ; for graalvm


(declare colourise)


(defonce ^{:doc "The numeric value of the current logging level."}
  log-level-num
  (atom (get r/levels :info)))

(defonce ^{:doc "Specifies where to output to. Should be `:console` or a (coercable) file."}
  output-to
  (atom :console))

(def formatted-levels
  "Pre-calculate the appearance of levels to save during runtime."
  (delay
    (if (and (= :console @output-to)
             @u/tty?)
      {:fatal   (colourise :fatal   "FATAL: ")
       :error   (colourise :error   "ERROR: ")
       :warn    (colourise :warn    "WARN: ")
       :success (colourise :success "SUCCESS: ")
       :info    (colourise :info    "INFO: ")
       :debug   (colourise :debug   "DEBUG: ")
       :trace   (colourise :trace   "TRACE: ")}
      {:fatal   "FATAL: "
       :error   "ERROR: "
       :warn    "WARN: "
       :success "SUCCESS: "
       :info    "INFO: "
       :debug   "DEBUG: "
       :trace   "TRACE: "})))

(defn get-log-level
  "Get the current logging level (keyword)."
  {:ret (s> ::r/level)}
  []
  (->> r/levels
       (u/find-first (fn [[_k v]]
                       (= v @log-level-num)))
       first))

(defn set-log-level
  "Set the current logging level."
  {:args (s< :level ::r/level)
   :ret (s> ::r/result)}
  [level]
  (let [level-num (get r/levels level)]
    (if (nil? level-num)
      (r/r :error (format "Invalid log level '%s' " (-> level name str/upper-case)))
      (do
        (reset! log-level-num level-num)
        (r/r :success (format "Log level set to '%s' " (-> level name str/upper-case)))))))

;(ansi/sgr "abc 123" :red)
(defn colourise
  "Colourise the given `text` according to `level`."
  {:args (s< :level ::r/level
             :text string?)
   :ret (s> string?)}
  [level text]
  (condp = level
    :fatal
    (ansi/sgr text :red :bold)

    :error
    (ansi/sgr text :red)

    :warn
    (ansi/sgr text :yellow)

    :success
    (ansi/sgr text :green)

    :info
    (ansi/sgr text :blue)

    :debug
    (ansi/sgr text :magenta)

    :trace
    (ansi/sgr text :cyan)

    :else
    (ansi/sgr text :white)))

(defn loggable?
  "Determine whether the given result should be logged.
  This is dependent on the current log level and if the message in the result is non-empty."
  {:args (s< :result ::r/result)
   :ret (s> boolean?)}
  [result]
  (and (not (empty? (or (:message result) "")))
       (<= (get r/levels (:level result) :error)
           @log-level-num)))

(defn log
  "Log the given result to stdout or stderr.

  WARN, ERROR and FATAL results are printed to stderr while all others are printed to stdout.

  No logging occurs if the given result is not considered `loggable?`.

  Optional arguments:
  * `exclude-level?`
    * When truthy the level name prefix is excluded
  * `force?`
    * When truthy the log is printed regardless of the current log level
    * I.e. ignoring `loggable?`
  * `override-level`
    * Specify a level which will override the level of the given `result`
    * This is useful when you don't want to change the level of the given `result` but do want to
      alter whether it is logged
  * `pp-r-map?`
    * When truthy the entire result map is pretty-printed on a separate line
    * The `:level` and `:message` keys are not reprinted

  The original result is returned to make certain contexts easier to work with such as injecting in
  the middle of a threading macro."
  {:args (s< :result ::r/result
             :kwargs (s/keys* :opt-un []))
   :ret (s> ::r/result)}
  [result & {:keys [exclude-level? force? override-level pp-r-map?]}]
  (let [r-to-log (if override-level
                   (assoc result :level override-level)
                   result)]
    (when (or force? (loggable? r-to-log))
      (let [msg (str (when (not exclude-level?)
                       (get @formatted-levels (:level r-to-log)))
                     (:message r-to-log)
                     (when pp-r-map?
                       (str "\n" (puget/cprint-str (dissoc r-to-log :level :message)))))]
        (if (= :console @output-to)
          (if (r/warned? r-to-log)
            (binding [*out* *err*]
              (println msg))
            (println msg))
          (spit @output-to
                (str msg "\n")
                :append true)))))
  result)

(defn print-all-levels
  [& {:keys [force?]}]
  (doseq [[level-name level-num] r/levels]
    (log (r/r level-name (format "message at level %s (%d)"
                                 (-> level-name name str/upper-case)
                                 level-num))
         :force? force?)))

(comment
  (log (r/r :warn "warn result logged at error") :override-level :error)
  (log (r/r :error "excluded level") :exclude-level? true)
  (log (r/r :error "map print test" {:a 1 :b 2}) :pp-r-map? true)
  (print-all-levels)
  (print-all-levels :force? true))

(apply-specs)
