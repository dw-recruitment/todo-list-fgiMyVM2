(ns todoapp.tasks.init-db
  (:require [clojure.edn :as edn]
            [clojure.java.io :as io]
            [com.stuartsierra.component :as component]
            [datomic.api :as d]
            [todoapp.database :as todo-db]))

(defn -main
  ([]
   (-main nil))
  ([config-path]
   (println "Reading config.")
   (let [{:keys [database-uri]} (edn/read-string
                                  (if config-path
                                    (slurp (io/file config-path))
                                    (slurp (io/resource "default-config.edn"))))
         database (todo-db/new-database database-uri)]
     (try
       (println (str "Installing schema to \"" database-uri "\"."))
       (let [database (component/start database)]
         (println "Schema loaded.")
         (todo-db/ensure-example-data database)
         (println "Example data loaded."))
       (finally
         (d/shutdown true))))))
