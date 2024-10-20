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
    [neodba.config :as cfg]
    [neodba.dba :as dba]
    [neodba.writer :as writer]))

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
        parsed-input-r (parse-user-input input)
        config (cfg/read-config-file)
        db-spec (cfg/get-active-db-spec config)]
    (if (r/failed? parsed-input-r)
      parsed-input-r
      (let [{:keys [sql cmd cmd-args]} parsed-input-r]
        (when @u/tty?
          (log (r/r :info (u/elide (str "Executing: " input) 100))))
        (cond
          sql
          (-> (dba/execute-sql db-spec sql)
              (writer/print-sql-res config))

          (= :get-database-info cmd)
          (-> (dba/get-database-info db-spec)
              (writer/print-sql-res config))

          (= :get-catalogs cmd)
          (-> (dba/get-catalogs db-spec)
              (writer/print-sql-res config))

          (= :get-schemas cmd)
          (-> (dba/get-schemas db-spec)
              (writer/print-sql-res config))

          (= :get-tables cmd)
          (-> (dba/get-tables db-spec)
              (writer/print-sql-res config))

          (= :get-views cmd)
          (let [output-fmt (-> cmd-args first keyword)]
            (-> (dba/get-views db-spec)
                (writer/print-sql-res config :output-fmt (or output-fmt :markdown))))

          (= :get-view-defn cmd)
          (let [view-name (-> cmd-args first str)]
            (if view-name
              (-> (dba/get-view-defn db-spec view-name)
                  (writer/print-sql-res config :output-fmt :sql))
              (r/print-msg
                (r/r :error (str "Invalid view query: " sql)))))

          (= :get-functions cmd)
          (let [output-fmt (-> cmd-args first keyword)]
            (-> (dba/get-functions db-spec)
                (writer/print-sql-res config :output-fmt (or output-fmt :markdown))))

          (= :get-procedures cmd)
          (let [output-fmt (-> cmd-args first keyword)]
            (-> (dba/get-procedures db-spec)
                (writer/print-sql-res config :output-fmt (or output-fmt :markdown))))

          (= :get-function-defn cmd)
          (let [func-name (-> cmd-args first str)]
            (if func-name
              (-> (dba/get-function-defn db-spec func-name)
                  (writer/print-sql-res config :output-fmt :sql))
              (r/print-msg
                (r/r :error (str "Invalid function query: " sql)))))

          (= :get-columns cmd)
          (let [table-name (-> cmd-args first str)
                verbose? (-> cmd-args second boolean)]
            (if table-name
              (-> (dba/get-columns db-spec table-name :verbose? verbose?)
                  (writer/print-sql-res config :output-fmt :sql))
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
  ;; Sample databases taken from here: https://github.com/lerocha/chinook-database
  (def config (cfg/read-config-file))
  (def db-spec (cfg/get-active-db-spec config))
  (-> (dba/execute-sql db-spec "select * from artist limit 5") (writer/print-sql-res config))
  (-> (dba/execute-sql db-spec "bad-query"))
  (-> (dba/get-database-info db-spec))
  (-> (dba/get-catalogs db-spec))
  (-> (dba/get-schemas db-spec))
  (-> (dba/get-tables db-spec))
  (-> (dba/get-views db-spec))
  (-> (dba/get-functions db-spec))
  (-> (dba/get-function-columns db-spec "add"))
  (-> (dba/get-function-defn db-spec "add") (writer/print-sql-res config :output-fmt :sql))
  (-> (dba/get-procedures db-spec))
  (-> (dba/get-procedure-columns db-spec "insert_data"))
  (-> (dba/get-columns db-spec "artist"))
  (-> (dba/get-columns db-spec "artist" :verbose? true)))
