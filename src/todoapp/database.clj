(ns todoapp.database
  (:require [clojure.java.io :as io]
            [datomic.api :as d :refer [q db]]))

(def uri "datomic:mem://todos")

(def functions-tx
  [
   {:db/ident :add-item
    :db/doc   "Create a new todo item at the end of the list with the provided text and a status of :item.status/todo."
    :db/id    (d/tempid :db.part/user)
    :db/fn    (d/function
                {:lang     :clojure
                 :requires '[[datomic.api :as d :refer [q db]]]
                 :params   '[db text]
                 :code     '(let [max-index (ffirst (q '[:find (max ?i)
                                                         :where [?e :item/index ?i]]
                                                       db))
                                  next-index (if max-index
                                               (inc max-index)
                                               0)]
                              [{:db/id       (d/tempid :db.part/user)
                                :item/status :item.status/todo
                                :item/text   text
                                :item/index  next-index}])})}
   ])

(defn init
  "Creates a new datomic database at uri and installs schema and database functions."
  [uri]
  (d/create-database uri)
  (let [conn (d/connect uri)
        schema-tx (read-string (slurp (io/resource "db/schema.edn")))]
    @(d/transact conn schema-tx)
    @(d/transact conn functions-tx)))

(defn transact-dummy-data
  "Insert some example TODOs into the database"
  [conn]
  (let [data-tx (read-string (slurp (io/resource "db/data.edn")))]
    (d/transact conn data-tx)))

(defn add-item
  "Adds a new TODO item with status :todo at the end of the list."
  [conn {:keys [text]}]
  (d/transact conn [[:add-item text]]))

(def status-kw->enum
  "Helper for going from :todo :done keywords to the keywords used to enumerate statuses in the db"
  {:todo :item.status/todo
   :done :item.status/done})

(def status-enum->kw
  "Helper for going from keywords enumerating database states to :todo and :done"
  {:item.status/todo :todo
   :item.status/done :done})

(defn set-item-status
  "Update item with entity id e to have a status of either :todo or :done"
  [conn e status]
  (if-let [status-enum (status-kw->enum status)]
    (d/transact conn [{:db/id       e
                       :item/status status-enum}])
    (throw (ex-info "Status must be either :todo or :done" {:status status}))))

(defn status
  "Get status of entity e"
  [e]
  (status-enum->kw (:item/status e)))

(comment
  (d/delete-database uri)
  (init uri)
  (def conn (d/connect uri))
  @(transact-dummy-data conn)
  (q '[:find (pull ?e [*]) :where [?e :item/status]] (db conn))
  @(add-item conn {:text "new item"})
  )