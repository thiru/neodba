(ns neodba.utils.common
  "Common/generic utilities."
  (:require
    [clojure.java.io :as io]
    [clojure.pprint :as pp]
    [clojure.repl :as repl]
    [clojure.spec.alpha :as s]
    [clojure.string :as str]
    [neodba.utils.results :as r]
    [puget.printer :as puget]
    [specin.core :refer [apply-specs s< s>]]))


(set! *warn-on-reflection* true) ; for graalvm


(def tty? (delay (not (nil? (System/console)))))

(defn find-first
  "Get the first element in `coll` where `pred` returns truthy when applied to it."
  [pred coll]
  (some #(when (pred %) %) coll))


(defmacro spy
  "A simpler version of Timbre's spy which simply pretty-prints to stdout
  and returns the eval'd expression."
  [expr]
  `(let [evaled# ~expr]
     (print (str '~expr " => "))
     (puget/cprint evaled#)
     evaled#))


(defn read-stdin
  "Read text from stdin."
  {:ret (s> (s/nilable string?))}
  []
  ;; (r/print-msg (r/r :error "Reading from stdin...")) ; DEBUG
  (loop [input (read-line)
         acc []]
    (if input
      (recur (read-line) (conj acc input))
      (str/join "\n" acc))))


(defn exit!
  "Exit app with a success/failure exit code based on the given result.
  The result's message is also printed to stdout or stderr as appropriate."
  {:args (s< :result ::r/result)
   :ret (s> nil?)}
  [result]
  (when result
    (r/print-msg result)
    (System/exit (case (:level result)
                   (:success :trace :info :warn :debug) 0
                   1)))
  (System/exit 0))


(defn elide
  "Shorten the given string if necessary and add ellipsis to the end."
  {:args (s< :text (s/nilable string?)
             :max-length nat-int?)
   :ret (s> (s/nilable string?))}
  [text max-length]
  (cond
    (nil? text)
    nil

    (>= max-length (count text))
    text

    :else
    (str (subs text 0 max-length)
         "...")))


(defn fmt
  "A convenience function to create a formatted string (via `format`).

  The first argument is the format control string. If it's a list it will be
  concatenated together. This makes it easier to format long strings."
  {:args (s< :formatter (s/or :whole-string string?
                              :segmented-string (s/coll-of string?))
             :args (s/* any?))
   :ret (s> string?)}
  [formatter & args]
  (if (sequential? formatter)
    (apply format (str/join "" formatter) args)
    (apply format formatter args)))


(defn rm-rf
  "Recursively delete a directory (or file if given one instead).

  Based on: https://gist.github.com/olieidel/c551a911a4798312e4ef42a584677397"
  [^java.io.File file]
  (when (.isDirectory file)
    (run! rm-rf (.listFiles file)))
  (io/delete-file file))


(defn slurp-file
  "Read all contents of the given file."
  {:args (s< :file-path (s/nilable string?))
   :ret (s> (s/or :content string?
                  :error-result :r/result))}
  [file-path]
  (if (str/blank? file-path)
    (r/r :error "No file was provided")
    (let [file (io/file file-path)]
      (if (not (.exists file))
        (r/r :error
             (format "File '%s' was not found or inaccessible" file-path))
        (slurp file)))))


(defn parse-int
  "Exception-free integer parsing.

   Returns the parsed integer if successful, otherwise `fallback`."
  {:args (s< :input (s/nilable string?)
             :kwargs (s/keys* :opt-un []))
   :ret (s> int?)}
  [input & {:keys [fallback]
            :or {fallback 0}}]
  (try
   (Integer/parseInt input)
   (catch Exception _ fallback)))


(defn parse-float
  "Exception-free float parsing.

   Returns the parsed float if successful, otherwise `fallback`."
  {:args (s< :input (s/nilable string?)
             :kwargs (s/keys* :opt-un []))
   :ret (s> float?)}
  [input & {:keys [fallback]
            :or {fallback 0.0}}]
  (try
   (Float/parseFloat input)
   (catch Exception _ fallback)))


(defn as-markdown-table
  "Convert the given vector of maps as a markdown table."
  {:args (s< :maps (s/coll-of map?))
   :ret (s> string?)}
  [maps]
  (if (empty? maps)
    ""
    (-> (with-out-str (pp/print-table maps))
        ;; NOTE: remove empty line `pp/print-table` creates at the beggining
        (str/trim)
        (str/split-lines)
        (update 1 #(str/replace % #"\+" "|"))
        (->> (str/join "\n")))))


(defn pretty-demunge
  "Pretty-print function vars."
  {:ret (s> string?)}
  [fn-object]
  (let [dem-fn (repl/demunge (str fn-object))
        pretty (second (re-find #"(.*?\/.*?)[\-\-|@].*" dem-fn))]
    (if pretty pretty dem-fn)))

(apply-specs)
