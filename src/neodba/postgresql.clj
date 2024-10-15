(ns neodba.postgresql
  "Queries specific to PostgreSQL.")

(def queries
  {:get-function-defn
   (str
     "SELECT pg_get_functiondef(p.oid) "
     "FROM pg_proc p "
     "JOIN pg_namespace n ON p.pronamespace = n.oid "
     "WHERE p.proname = '%s' "
     "AND n.nspname = '%s'")})

(defn get-function-defn
  [func-name schema]
  (format (:get-function-defn queries) func-name schema))
