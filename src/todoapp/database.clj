(ns todoapp.database
  (:require [com.stuartsierra.component :as component]
            [datomic.api :as d :refer [q db]]
            [io.rkn.conformity :as conformity]
            [todoapp.database-norms :as database-norms])
  (:import [java.util List]))

(defn init
  "Creates a new datomic database at uri and installs schema and database functions.
  Safe to call more than once."
  [uri]
  (d/create-database uri)
  (let [conn (d/connect uri)]
    (conformity/ensure-conforms conn database-norms/norms)))

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
  "Insert some example items into the database.
  If run multiple times, will only do the insert once."
  [{:keys [conn]}]
  (let [data-norms (conformity/read-resource "db/data.edn")]
    (conformity/ensure-conforms conn data-norms [:todoapp/example-data])))

(defn eid?
  "Return true if eid is a number or a java.util.List
  since most Datomic functions accepting entity ID's
  also accept lookup refs as a List."
  [eid]
  (or (number? eid)
      (instance? List eid)))

(defn default-list?
  "db-val - Datomic database value
  list-id - entity id of list to check

  Returns true if list-id points to the default list in the db."
  [db-val list-id]
  (= (d/entity db-val list-id)
     (d/entity db-val [:list/default? true])))

(defn add-item
  "Adds a new TODO item with status :todo at the end of the list.

  (add-item database item)

  database - DatomicDatabase component
  item - map with keys:
    :text - text content of the new item
    :list-id - entity ID of the list the item should be added to
               (if omitted, item will be added to the default list)

  Returns a map with keys:
    :db-before - as from datomic.api/transact
    :db-after - as from datomic.api/transact
    :tx-data - as from datomic.api/transact
    :item-id - the entity id of the newly inserted item"
  [{:keys [conn]} {:keys [text
                          list-id]}]
  {:pre [(string? text) (or (nil? list-id)
                            (number? list-id))]}
  (let [list-id (or list-id [:list/default? true])
        {:keys [db-before db-after tx-data tempids]}
        @(d/transact conn [[:add-item-to-list list-id text]])]
    {:db-before db-before
     :db-after  db-after
     :tx-data   tx-data
     :item-id   (d/resolve-tempid db-after tempids (d/tempid :db.part/user -1))}))

(defn add-list
  "Adds a new TODO list with no items

  (add-list database list)

  database - DatomicDatabase component
  list - a map with keys:
    :name - name of the new list

  Returns a map with keys:
  :db-before - as from datomic.api/transact
  :db-after - as from datomic.api/transact
  :tx-data - as from datomic.api/transact
  :list-id - the entity id of the newly inserted list"
  [{:keys [conn]} {:keys [name]}]
  {:pre [(string? name)]}
  (let [{:keys [db-before db-after tx-data tempids]} @(d/transact conn [[:add-list name]])]
    {:db-before db-before
     :db-after  db-after
     :tx-data   tx-data
     :list-id   (d/resolve-tempid db-after tempids (d/tempid :db.part/user -1))}))

(def status-kw->enum
  "Helper for going from :todo :done keywords to the keywords used to enumerate statuses in the db"
  {:todo :item.status/todo
   :done :item.status/done})

(def status-enum->kw
  "Helper for going from keywords enumerating database states to :todo and :done"
  {:item.status/todo :todo
   :item.status/done :done})

(defn update-item
  "Update item with new status.

  (update-item database item)

  database - DatomicDatabase component
  item - map with the following keys
    :id - entity id in the database
    :status - either :todo or :done"
  [{:keys [conn]} {:keys [id status]}]
  {:pre [(number? id) (keyword? status)]}
  (if-let [status-enum (status-kw->enum status)]
    @(d/transact conn [{:db/id       id
                        :item/status status-enum}])
    (throw (ex-info (str "Status must be either :todo or :done, got " (pr-str status))
                    {:status status}))))

(defn eid->item
  "Return a map describing an item based on its entity ID,
  or nil if not found.

  (eid->item db-val eid)

  db-val - a Datomic database value
  eid - Entity ID or lookup ref of an item to look up

  Returned map keys:
    :id - Entity ID
    :text - Text of the item
    :status - :todo or :done
    :index - 0-based position in the list of items"
  [db-val eid]
  {:pre [(eid? eid)]}
  (when-let [pulled-map (d/pull db-val
                                '[:item/text {:item/status [:db/ident]} :item/index]
                                eid)]
    {:id     (:db/id (d/entity db-val eid))
     :text   (:item/text pulled-map)
     :status (-> pulled-map :item/status :db/ident status-enum->kw)
     :index  (:item/index pulled-map)}))

(defn eid->list
  "Return a map describing a todo list based on its entity ID,
  or nil if not found.

  (eid->list db-val eid)

  db-val - a Datomic database value
  eid - Entity ID or lookup ref or a list to look up

  Returned map keys:
    :id - Entity ID
    :default? - true if this is the default list, false otherwise
    :name - Name of the list
    :items - vector of items with keys as from eid->item"
  [db-val eid]
  {:pre [(eid? eid)]}
  (when-let [pulled-map (d/pull db-val
                                '[:list/name
                                  :list/default?
                                  {:list/items [:db/id]}]
                                eid)]
    (let [items (map (fn [{:keys [:db/id]}]
                       (eid->item db-val id))
                     (:list/items pulled-map))]
      {:id       (:db/id (d/entity db-val eid))
       :default? (boolean (:list/default? pulled-map))
       :name     (:list/name pulled-map)
       :items    (vec (sort-by :index items))})))

(defn get-lists
  "Get the names and ids of all lists.
  Does not include items.

  (get-list-names db-val)

  db-val - a Datomic database value

  Returns a vector of maps, each with keys:
    :id - list entity ID
    :default? - true if this is the default list, false otherwise
    :name - list name"
  [db-val]
  (->> (q '[:find ?eid ?name
            :where [?eid :list/name ?name]]
          db-val)
       (map (fn [[eid name]]
              {:id       eid
               :name     name
               :default? (boolean (:list/default? (d/entity db-val eid)))}))
       (sort-by :id)
       vec))

(defn item-id->list-id
  "(item-id->list-id db-val item-id)

  db-val - a Datomic database value
  item-id - entity id of an item

  Returns entity id of parent list, or nil if not found"
  [db-val item-id]
  {:pre [(number? item-id)]}
  (when-let [pulled (d/pull db-val '[{:list/_items [:db/id]}] item-id)]
    (-> pulled :list/_items :db/id)))

(defn retract-item
  [{:keys [conn]} item-id]
  {:pre [{number? item-id}]}
  @(d/transact conn [[:db.fn/retractEntity item-id]]))
