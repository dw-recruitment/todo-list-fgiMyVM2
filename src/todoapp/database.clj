(ns todoapp.database
  (:require [com.stuartsierra.component :as component]
            [clojure.java.io :as io]
            [datomic.api :as d :refer [q db]]
            [io.rkn.conformity :as conformity]))

(def function-norms
  {:todoapp/add-item-fn
   {:txes [[{:db/ident :add-item
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
            ]]}})

(defn init
  "Creates a new datomic database at uri and installs schema and database functions.
  Safe to call twice."
  [uri]
  (d/create-database uri)
  (let [schema-norms (conformity/read-resource "db/schema.edn")
        conn (d/connect uri)]
    (conformity/ensure-conforms conn schema-norms [:todoapp/item-schema])
    (conformity/ensure-conforms conn function-norms [:todoapp/add-item-fn])))

(defrecord DatomicDatabase [uri conn]
  component/Lifecycle
  (start [component]
    (init uri)
    (let [conn (d/connect uri)]
      (assoc component :conn conn)))
  (stop [component]
    (d/release conn)
    (assoc component :conn nil)))

(defn new-database [uri]
  (map->DatomicDatabase {:uri uri}))

(defn ensure-example-data
  "Insert some example TODOs into the database.
  Uses conformity to only insert them once."
  [{:keys [conn]}]
  (let [data-norms (conformity/read-resource "db/data.edn")]
    (conformity/ensure-conforms conn data-norms [:todoapp/example-data])))

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
  "Update item with new status.
  (set-item-status database item)

  database - DatomicDatabase component
  item - map with the following keys
    :id - entity id in the database
    :status - either :todo or :done"
  [{:keys [conn]} {:keys [id status]}]
  (if-let [status-enum (status-kw->enum status)]
    (d/transact conn [{:db/id       id
                       :item/status status-enum}])
    (throw (ex-info (str "Status must be either :todo or :done, got " (pr-str status)) {:status status}))))

(defn status
  "Get status of entity e"
  [e]
  (status-enum->kw (:item/status e)))

(defn eid->item
  [db-val eid]
  (let [pulled-map (d/pull db-val '[* {:item/status [:db/ident]}] eid)]
    {:id     (:db/id pulled-map)
     :text   (:item/text pulled-map)
     :status (-> pulled-map :item/status :db/ident status-enum->kw)
     :index  (:item/index pulled-map)}))

(defn get-items
  [db-val]
  (->> (q '[:find ?e
            :where [?e :item/status]]
          db-val)
       (map first)
       (map (partial eid->item db-val))
       (sort-by :index)))

(comment
  (def uri (:database-uri (read-string (slurp (io/resource "default-config.edn")))))
  (d/delete-database uri)
  (init uri)
  (def database (component/start (new-database uri)))
  @(ensure-example-data database)
  (q '[:find (pull ?e [* {:item/status [:db/ident]}]) :where [?e :item/status]] (db (:conn database)))
  @(add-item database {:text "new item"})
  (clojure.pprint/print-table [:id :text :status :index]
                              (get-items (db (:conn database))))
  )