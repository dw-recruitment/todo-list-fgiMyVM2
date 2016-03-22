(ns todoapp.database
  (:require [clojure.java.io :as io]
            [datomic.api :refer [q db] :as d]))

(def uri "datomic:mem://todos")

(defn init
  []
  (d/create-database uri)
  (let [conn (d/connect uri)
        schema-tx (read-string (slurp (io/resource "db/schema.edn")))
        data-tx (read-string (slurp (io/resource "db/data.edn")))]
    @(d/transact conn schema-tx)
    @(d/transact conn data-tx)
    ))

(defn add-item
  [{:keys [connection]} {:keys [text]}]
  (d/transact connection [{:db/id       (d/tempid :db.part/user)
                           :item/status :item.status/todo
                           :item/text   text
                           ; index?
                           }]))

(comment
  (d/delete-database uri)
  (init)
  *data-readers*
  (def conn (d/connect uri))
  (q '[:find (pull ?e [*]) :where [?e :db/ident]] (db conn))
  (q '[:find (pull ?e [*]) :where [?e :item/status]] (db conn))
  (q '[:find (max ?i) :where [?e :item/index ?i]] (db conn))
  (add-item {:connection conn} {:text "new item"})
  )