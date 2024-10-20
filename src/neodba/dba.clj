(ns neodba.dba
  "Database access layer."
  (:require
    [clojure.string :as str]
    [next.jdbc :as jdbc]
    [next.jdbc.result-set :as jdbc-rs]
    [neodba.postgresql :as pg]
    [neodba.utils.common :as u]))

(set! *warn-on-reflection* true) ; for graalvm

(defn get-connection
  "Get a connection object for the given database spec and set the schema (if applicable)"
  ^java.sql.Connection [db-spec]
  (let [con (jdbc/get-connection db-spec)
        schema (:schema db-spec)]
    (when (not (str/blank? schema))
      (.setSchema con schema))
    con))

(defn execute-sql
  [db-spec sql]
  (if (str/blank? sql)
    []
    (with-open [con (get-connection db-spec)]
      (jdbc/execute! con
                     [sql]
                     {:builder-fn jdbc-rs/as-unqualified-maps}))))

(defn get-database-info
  [db-spec]
  (with-open [con (get-connection db-spec)]
    (let [md (.getMetaData con)]
      [{:name "database" :value (.getDatabaseProductName md)}
       {:name "version" :value (.getDatabaseProductVersion md)}
       {:name "url" :value (.getURL md)}
       {:name "catalog" :value (.getCatalog con)}
       {:name "schema" :value (.getSchema con)}])))

(defn get-catalogs
  [db-spec]
  (with-open [con (get-connection db-spec)]
    (-> (.getMetaData con)
        (.getCatalogs)
        (jdbc-rs/datafiable-result-set con {:builder-fn jdbc-rs/as-unqualified-lower-maps})
        (->> (map (fn [x] {:name (:table_cat x)}))))))

(defn get-schemas
  [db-spec]
  (with-open [con (get-connection db-spec)]
    (-> (.getMetaData con)
        (.getSchemas)
        (jdbc-rs/datafiable-result-set con {:builder-fn jdbc-rs/as-unqualified-lower-maps})
        (->> (map (fn [x] {:name (:table_schem x)}))))))

(defn get-tables
  [db-spec]
  (with-open [con (get-connection db-spec)]
    (-> (.getMetaData con)
        (.getTables nil (:schema db-spec) nil (into-array ["TABLE"]))
        (jdbc-rs/datafiable-result-set con {:builder-fn jdbc-rs/as-unqualified-lower-maps})
        (->> (map (fn [x] {:name (:table_name x)}))))))

(defn get-views
  [db-spec]
  (with-open [con (get-connection db-spec)]
    (-> (.getMetaData con)
        (.getTables nil (:schema db-spec) nil (into-array ["VIEW"]))
        (jdbc-rs/datafiable-result-set con {:builder-fn jdbc-rs/as-unqualified-lower-maps})
        (->> (map (fn [x] {:name (:table_name x)}))))))

(defn get-view-defn
  [db-spec view-name]
  (let [sql (condp = (:dbtype db-spec)
              "postgres"
              (pg/get-view-defn view-name)
              (throw (ex-info (str "Don't know how to get view definition for dbtype: " (:dbtype db-spec)) {})))]
    (with-open [con (get-connection db-spec)]
      (jdbc/execute! con
                     [sql]
                     {:builder-fn jdbc-rs/as-unqualified-maps}))))

(defn get-columns
  [db-spec table-name & {:keys [verbose?]}]
  (with-open [con (get-connection db-spec)]
    (-> (.getMetaData con)
        (.getColumns nil (:schema db-spec) table-name nil)
        (jdbc-rs/datafiable-result-set con {:builder-fn jdbc-rs/as-unqualified-lower-maps})
        (->> (map (fn [x]
                    (if verbose?
                      x
                      {:name (:column_name x)
                       :type (:type_name x)
                       :size (:column_size x)
                       :nullable? (:is_nullable x)})))))))

(defn get-function-columns
  [db-spec func-name]
  (with-open [con (get-connection db-spec)]
    (-> (.getMetaData con)
        (.getFunctionColumns nil (:schema db-spec) func-name nil)
        (jdbc-rs/datafiable-result-set con {:builder-fn jdbc-rs/as-unqualified-lower-maps})
        (->> (map (fn [x] {:name (:column_name x)
                           :type (:type_name x)}))))))

(defn get-functions
  [db-spec]
  (with-open [con (get-connection db-spec)]
    (-> (.getMetaData con)
        (.getFunctions nil (:schema db-spec) nil)
        (jdbc-rs/datafiable-result-set con {:builder-fn jdbc-rs/as-unqualified-lower-maps})
        (->> (map (fn [x] {:name (:function_name x)
                           :type (:function_type x)
                           :columns (->> (get-function-columns db-spec (:function_name x))
                                         (mapv #(str (:name %) " " (:type %)))
                                         (str/join ", "))}))))))

(defn get-function-defn
  [db-spec func-name]
  (let [sql (condp = (:dbtype db-spec)
              "postgres"
              (pg/get-function-defn func-name (:schema db-spec))
              (throw (ex-info (str "Don't know how to get function definition for dbtype: " (:dbtype db-spec)) {})))]
    (with-open [con (get-connection db-spec)]
      (jdbc/execute! con
                     [sql]
                     {:builder-fn jdbc-rs/as-unqualified-maps}))))

(defn get-procedure-columns
  [db-spec proc-name]
  (with-open [con (get-connection db-spec)]
    (-> (.getMetaData con)
        (.getFunctionColumns nil (:schema db-spec) proc-name nil)
        (jdbc-rs/datafiable-result-set con {:builder-fn jdbc-rs/as-unqualified-lower-maps})
        (->> (map (fn [x] {:name (:column_name x)
                           :type (:type_name x)}))))))

(defn get-procedures
  [db-spec]
  (with-open [con (get-connection db-spec)]
    (-> (.getMetaData con)
        (.getProcedures nil (:schema db-spec) nil)
        (jdbc-rs/datafiable-result-set con {:builder-fn jdbc-rs/as-unqualified-lower-maps})
        (->> (map (fn [x] {:name (:procedure_name x)
                           :type (:procedure_type x)
                           :columns (->> (get-procedure-columns db-spec (:procedure_name x))
                                         (mapv #(str (:name %) " " (:type %)))
                                         (str/join ", "))}))))))

