(ns todoapp.database
  (:require [com.stuartsierra.component :as component]
            [clojure.java.io :as io]
            [datomic.api :as d :refer [q db]]))

(defrecord DatomicDatabase [uri conn]
  component/Lifecycle
  (start [component]
    (let [conn (d/connect uri)]
      (assoc component :conn conn)))
  (stop [component]
    (d/release conn)
    (assoc component :conn nil)))

(defn new-database [uri]
  (map->DatomicDatabase {:uri uri}))

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
  [{:keys [conn]}]
  (let [data-tx (read-string (slurp (io/resource "db/data.edn")))]
    (d/transact conn data-tx)))

(defn add-item
  "Adds a new TODO item with status :todo at the end of the list."
  [{:keys [conn]} {:keys [text]}]
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
  [{:keys [conn]} e status]
  (if-let [status-enum (status-kw->enum status)]
    (d/transact conn [{:db/id       e
                       :item/status status-enum}])
    (throw (ex-info "Status must be either :todo or :done" {:status status}))))

(defn status
  "Get status of entity e"
  [e]
  (status-enum->kw (:item/status e)))

(defn get-items
  [db]
  (->> (q '[:find (pull ?e [* {:item/status [:db/ident]}])
            :where [?e :item/status]]
          db)
       (map first)
       (map (fn [m]
              {:id     (:db/id m)
               :text   (:item/text m)
               :status (-> m :item/status :db/ident status-enum->kw)
               :index  (:item/index m)}))
       (sort-by :index)))

(comment
  (def uri (:database-uri (read-string (slurp (io/resource "default-config.edn")))))
  (d/delete-database uri)
  (init uri)
  (def database (component/start (new-database uri)))
  @(transact-dummy-data database)
  (q '[:find (pull ?e [* {:item/status [:db/ident]}]) :where [?e :item/status]] (db (:conn database)))
  @(add-item database {:text "new item"})
  (get-items (db (:conn database)))
  )