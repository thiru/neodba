(ns neodba.api
  "Primary user API."
  (:require
    [clojure.edn :as edn]
    [clojure.string :as str]
    [clojure.spec.alpha :as s]
    [neodba.utils.common :as u]
    [neodba.utils.log :as log :refer [log]]
    [neodba.utils.results :as r]
    [neodba.utils.specin :refer [defn]]
    [neodba.dba :as dba]))


(set! *warn-on-reflection* true) ; for graalvm


(def prompt "$ ")


(defn parse-user-input
  {:args (s/cat :input (s/nilable string?))
   :ret ::r/result}
  [input]
  (let [input (str/trim (or input ""))]
    (cond
      (str/blank? input)
      (r/r :error "No SQL or command provided")

      (str/starts-with? input "(")
      (try
        (let [cmd (edn/read-string (str/lower-case input))]
          (if (not (list? cmd))
            (r/r :error (str "Invalid command: " input))
            (r/r :success "Custom command specified"
                 {:cmd (keyword (first cmd))
                  :cmd-args (rest cmd)})))
        (catch Exception _
          (r/r :error (str "Invalid command: " input))))

      :else
      (r/r :success "SQL provided" {:sql input}))))

(defn execute-sql
  [input]
  (let [input (if (sequential? input)
                (str/join " " input)
                input)
        parsed-input-r (parse-user-input input)]
    (if (r/failed? parsed-input-r)
      parsed-input-r
      (let [{:keys [sql cmd cmd-args]} parsed-input-r]
        (when @u/tty?
          (log (r/r :info (u/elide (str "Executing: " input) 100))))
        (cond
          sql
          (dba/print-with-config #(dba/execute-sql % sql))

          (= :get-database-info cmd)
          (dba/print-with-config dba/get-database-info)

          (= :get-catalogs cmd)
          (dba/print-with-config dba/get-catalogs)

          (= :get-schemas cmd)
          (dba/print-with-config dba/get-schemas)

          (= :get-tables cmd)
          (dba/print-with-config dba/get-tables)

          (= :get-views cmd)
          (let [output-fmt (-> cmd-args first keyword)]
            (dba/print-with-config dba/get-views
                                   :output-fmt (or output-fmt :markdown)))

          (= :get-view-defn cmd)
          (let [view-name (-> cmd-args first str)]
            (if view-name
              (dba/print-with-config #(dba/get-view-defn % view-name)
                                     :output-fmt :sql)
              (r/print-msg
                (r/r :error (str "Invalid function query: " sql)))))

          (= :get-functions cmd)
          (let [output-fmt (-> cmd-args first keyword)]
            (dba/print-with-config dba/get-functions
                                   :output-fmt (or output-fmt :markdown)))

          (= :get-procedures cmd)
          (let [output-fmt (-> cmd-args first keyword)]
            (dba/print-with-config dba/get-procedures
                                   :output-fmt (or output-fmt :markdown)))

          (= :get-function-defn cmd)
          (let [func-name (-> cmd-args first str)]
            (if func-name
              (dba/print-with-config #(dba/get-function-defn % func-name)
                                     :output-fmt :sql)
              (r/print-msg
                (r/r :error (str "Invalid function query: " sql)))))

          (= :get-columns cmd)
          (let [table-name (-> cmd-args first str)
                verbose? (-> cmd-args second boolean)]
            (if table-name
              (dba/print-with-config #(dba/get-columns % table-name :verbose? verbose?))
              (r/print-msg
                (r/r :error (str "Invalid metadata query: " sql)))))

          :else
          (r/r :error (str "Unknown command: " input)))))))

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
