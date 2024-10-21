(ns neodba.postgresql
  "Queries specific to PostgreSQL.")

(def queries
  {:get-procedure-defn
   (str
     "SELECT pg_get_functiondef(p.oid) "
     "FROM pg_proc p "
     "WHERE p.proname = '%s'")
   :get-function-defn
   (str
     "SELECT pg_get_functiondef(p.oid) "
     "FROM pg_proc p "
     "JOIN pg_namespace n ON p.pronamespace = n.oid "
     "WHERE p.proname = '%s' "
     "AND n.nspname = '%s'")
   :get-view-defn
   "SELECT pg_get_viewdef('%s', true)"})

(defn get-function-defn
  [func-name schema]
  (format (:get-function-defn queries) func-name schema))

(defn get-procedure-defn
  [proc-name schema]
  (format (:get-procedure-defn queries) proc-name schema))

(defn get-view-defn
  [view-name]
  (format (:get-view-defn queries) view-name))
