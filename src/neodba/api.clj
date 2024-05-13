(ns neodba.api
  "Primary user API."
  (:require
    [clojure.string :as str]
    [neodba.utils.common :as u]
    [neodba.utils.log :as log :refer [log]]
    [neodba.utils.results :as r]
    [neodba.dba :as dba]))


(set! *warn-on-reflection* true) ; for graalvm


(def prompt "$ ")


(defn execute-sql
  [sql]
  (if (str/blank? sql)
    (r/r :warn "No SQL provided")
    (do
      (when @u/tty?
        (log (r/r :info (str "Executing SQL: " sql))))
      (dba/print-rs (dba/execute sql))
      (r/r :success ""))))

(defn read-sql-from-stdin
  []
  (when @u/tty?
    (log (r/r :info "Reading SQL from stdin... (to exit press CTRL-D or type 'quit')")))
  (loop []
    (when @u/tty?
      (print prompt)
      (flush))
    (let [input (read-line)]
      (if (or (nil? input) (= "quit" input))
        (when @u/tty?
          (println "Exiting..."))
        (do
          (try
            (dba/print-rs
              (cond
                (= "(get-schemas)" input)
                (dba/get-schemas)

                (= "(get-tables)" input)
                (dba/get-tables)

                :else
                (dba/execute input)))
            (catch Exception ex
              (binding [*out* *err*]
                (println (.toString ex)))))
          (when @u/tty?
            (println))
          (recur)))))
  (r/r :success ""))
