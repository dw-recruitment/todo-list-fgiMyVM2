(ns user
    (:require [com.stuartsierra.component :as component]
              [clojure.edn :as edn]
              [clojure.java.io :as io]
              [clojure.tools.namespace.repl :refer [refresh]]
              [datomic.api :as d :refer [q db]]
              [todoapp.app :as app]
              [todoapp.database :as todo-db]))

(defonce system nil)

(defn new-system
      [config]
      (alter-var-root #'system (constantly (app/todo-system config))))

(defn start-system
      []
      (alter-var-root #'system component/start))

(defn stop-system
      []
      (alter-var-root #'system component/stop))

(defn go
  []
  (new-system (edn/read-string (slurp (io/resource "default-config.edn"))))
  (start-system))

(defn reset
  []
  (stop-system)
  (refresh :after 'user/go))

(comment
  (go)
  system
  (reset)
  (stop-system)

  (def uri (:database-uri (read-string (slurp (io/resource "default-config.edn")))))
  (d/delete-database uri)
  (todo-db/init uri)
  (todo-db/ensure-example-data (:database system))
  (todo-db/add-item (:database system) {:text "new item"})

  ;; All items
  (q '[:find (pull ?e [* {:item/status [:db/ident]}]) :where [?e :item/status]]
     (-> system :database :conn db))
  ;; Items table
  (clojure.pprint/print-table [:id :text :status :index]
                              (todo-db/get-items (-> system :database :conn db)))
  )