(ns neodba.cli
  "Command-line interface abstraction."
  (:refer-clojure :exclude [defn])
  (:require
    [clojure.spec.alpha :as s]
    [clojure.string :as str]
    [neodba.utils.common :as u]
    [neodba.utils.log :as log :refer [log]]
    [neodba.utils.results :as r]
    [neodba.utils.specin :refer [defn]]
    [neodba.server :as server]))


(set! *warn-on-reflection* true) ; for graalvm


(s/def ::cli-args (s/coll-of string?))
(s/def ::global-opt #(str/starts-with? % "-"))
(s/def ::global-flag-opts (s/coll-of ::global-opt))
(s/def ::global-kvp-opts (s/coll-of vector?))
(s/def ::sub-cmd (s/nilable string?))
(s/def ::sub-cmd-args (s/coll-of string?))
(s/def ::cli-r (s/and ::r/result
                      (s/keys :opt-un [::cli-args
                                       ::global-flag-opts ::global-kvp-opts
                                       ::sub-cmd ::sub-cmd-args])))


(def global-cmd-opts
  "A set of arguments that take the form of global options (by convention) but behave like
  sub-commands."
  #{"-h" "--help" "--version"})

(def global-kvp-opts
  "A set of global options that require a value."
  #{"--log-level"})


(def version (-> (slurp "VERSION") str/trim))
(def help (-> (slurp "HELP") (format version)))


(defn check-args
  {:args (s/cat :cli-args ::cli-args)
   :ret ::cli-r}
  [cli-args]
  (if (or (empty? cli-args)
          (str/blank? (first cli-args)))
    (r/r :error "No sub-command specified. Try running: neodba --help"
         {:cli-args cli-args})
    (r/r :success "Some arguments found"
         {:cli-args cli-args})))

(defn extract-global-opts
  {:args (s/cat :_cli-r ::cli-r)
   :ret ::cli-r}
  [{:keys [cli-args] :as _cli-r}]
  (loop [rest-args cli-args
         curr-arg (first rest-args)
         flag-opts []
         kvp-opts []]
    (if (and curr-arg (str/starts-with? curr-arg "-"))
      (if (global-kvp-opts curr-arg)
        (let [k curr-arg
              v (first (rest rest-args))]
          (recur (rest (rest rest-args))
                 (first (rest (rest rest-args)))
                 flag-opts
                 (conj kvp-opts [k v])))
        (recur (rest rest-args)
               (first (rest rest-args))
               (conj flag-opts curr-arg)
               kvp-opts))
      (r/r :success (format "Extracted %d global option(s)" (+ (count flag-opts)
                                                               (count kvp-opts)))
           {:cli-args rest-args
            :global-flag-opts flag-opts
            :global-kvp-opts kvp-opts}))))

(defn extract-sub-cmd-and-args
  {:args (s/cat :_cli-r ::cli-r)
   :ret ::cli-r}
  [{:keys [global-flag-opts cli-args] :as cli-r}]
  (if-let [sub-cmd  (u/find-first global-cmd-opts global-flag-opts)]
    (assoc cli-r
          :level :success
          :message (format "Extracted global option, '%s' as sub-command" sub-cmd)
          :cli-args []
          :sub-cmd sub-cmd)
    (assoc cli-r
          :level :success
          :message (format "Extracted sub-command '%s'" (first cli-args))
          :cli-args []
          :sub-cmd (first cli-args)
          :sub-cmd-args (rest cli-args))))

(defn parse-cli-args
  "Parse the given CLI arguments."
  {:args (s/cat :cli-args ::cli-args)
   :ret ::cli-r}
  [cli-args]
  (r/while-success-> (check-args cli-args)
                     extract-global-opts
                     extract-sub-cmd-and-args))

(defn server-sub-cmd
  {:args (s/cat :_cli-r ::cli-r)
   :ret ::cli-r}
  [{:keys [sub-cmd-args] :as _cli-r}]
  (let [port (u/parse-int (first sub-cmd-args) :fallback nil)]
    (log (r/r :info (str "neodba v" version)))
    (server/start! port)
    (-> (Runtime/getRuntime)
        (.addShutdownHook (new Thread ^java.lang.Runnable server/stop!)))
    @(promise)
    (r/r :success "")))

(defn set-log-level
  {:args (s/cat :_cli-r ::cli-r)
   :ret ::cli-r}
  [{:keys [global-kvp-opts] :as cli-r}]
  (let [log-level-str (->> global-kvp-opts
                           (u/find-first (fn [[k _v]]
                                           (= k "--log-level")))
                           second)]
    (if log-level-str
      (merge cli-r
             (log/set-log-level (-> log-level-str
                                    str/lower-case
                                    keyword)))
      cli-r)))

(defn run-sub-cmd
  {:args (s/cat :cli-r ::cli-r)
   :ret ::cli-r}
  [{:keys [sub-cmd] :as cli-r}]
  (case sub-cmd
    ("-h" "--help")
    (r/r :success help)

    "--version"
    (r/r :success version)

    "server"
    (server-sub-cmd cli-r)

    nil
    (assoc cli-r
           :level :error
           :message "No sub-command provided. Try running: neodba --help")

    (assoc cli-r
           :level :error
           :message "Unrecognised sub-command. Try running: neodba --help")))

(defn run
  "Execute the command specified by the given arguments."
  {:args (s/cat :cli-args ::cli-args)
   :ret ::cli-r}
  [cli-args]
  (r/while-success-> (parse-cli-args cli-args)
                     set-log-level
                     (log :override-level :debug :pp-r-map? true)
                     run-sub-cmd))


(comment
  (parse-cli-args [])
  (parse-cli-args ["--version"])
  (parse-cli-args ["server"])
  (parse-cli-args ["--log-level" "info" "server" "8000"]))
