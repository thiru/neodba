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
  [input]
  (let [sql (if (sequential? input)
              (str/join " " input)
              input)]
    (if (str/blank? sql)
      (do
        (r/print-msg (r/r :warn "No SQL provided"))
        (r/r :succes ""))
      (let [sql (str/trim sql)]
        (when @u/tty?
          (log (r/r :info (str "Executing SQL: " sql))))
        (dba/print-rs
          (cond
            (= "(get-catalogs)" sql)
            (dba/get-catalogs)

            (= "(get-schemas)" sql)
            (dba/get-schemas)

            (= "(get-tables)" sql)
            (dba/get-tables)

            (= "(get-views)" sql)
            (dba/get-views)

            :else
            (dba/execute sql)))
        (r/r :success "")))))

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
            (execute-sql input)
            (catch Exception ex
              (binding [*out* *err*]
                (println (.toString ex)))))
          (when @u/tty?
            (println))
          (recur)))))
  (r/r :success ""))
