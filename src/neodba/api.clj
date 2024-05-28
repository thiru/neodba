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
      (r/r :warn "No SQL provided")
      (let [sql (str/trim sql)]
        (when @u/tty?
          (log (r/r :info (u/elide (str "Executing SQL: " sql) 100))))
        (cond
          (= "(get-database-info)" sql)
          (dba/print-with-db-spec dba/get-database-info)

          (= "(get-catalogs)" sql)
          (dba/print-with-db-spec dba/get-catalogs)

          (= "(get-schemas)" sql)
          (dba/print-with-db-spec dba/get-schemas)

          (= "(get-tables)" sql)
          (dba/print-with-db-spec dba/get-tables)

          (= "(get-views)" sql)
          (dba/print-with-db-spec dba/get-views)

          (= "(get-functions)" sql)
          (dba/print-with-db-spec dba/get-functions)

          (= "(get-procedures)" sql)
          (dba/print-with-db-spec dba/get-procedures)

          (str/starts-with? sql "(get-columns")
          (let [match (re-find #"\(get-columns(\+)?\s+['\"]?(\w+)['\"]?\)" sql)
                verbose? (second match)
                table-name (nth match 2 nil)]
            (if table-name
              (dba/print-with-db-spec #(dba/get-columns % table-name :verbose? verbose?))
              (r/print-msg
                (r/r :error (str "Invalid metadata query: " sql)))))

          :else
          (dba/print-with-db-spec #(dba/execute-sql % sql)))))))

(defn execute-file
  [path]
  (if (str/blank? path)
    (r/r :error "No file provided")
    (let [sql (slurp path)]
      (if (str/blank? sql)
        (r/r :error (str "No SQL in file: " path))
        (execute-sql sql)))))

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


(comment
  (execute-sql "select * from artist limit 3"))
