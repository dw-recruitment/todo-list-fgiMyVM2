(ns todoapp.database-norms
  (:require [datomic.api :as d]))

(def norms
  "Norms for conformity to ensure in the database."
  {:todoapp/item-schema
   {:txes
    [[{:db/id                 #db/id[:db.part/db]
       :db/ident              :item/text
       :db/valueType          :db.type/string
       :db/cardinality        :db.cardinality/one
       :db/doc                "The text content of a TODO item"
       :db.install/_attribute :db.part/db}

      {:db/id                 #db/id[:db.part/db]
       :db/ident              :item/status
       :db/valueType          :db.type/ref
       :db/cardinality        :db.cardinality/one
       :db/doc                "The status of a TODO item, as in :todo or :done"
       :db.install/_attribute :db.part/db}

      {:db/id                 #db/id[:db.part/db]
       :db/ident              :item/index
       :db/valueType          :db.type/long
       :db/cardinality        :db.cardinality/one
       :db/doc                "The 0-based index for where a TODO item is ordered"
       :db.install/_attribute :db.part/db}

      {:db/id    #db/id[:db.part/user]
       :db/ident :item.status/todo
       :db/doc   "Value of :item/status attribute indicating a TODO is not yet done"}

      {:db/id    #db/id[:db.part/user]
       :db/ident :item.status/done
       :db/doc   "Value of :item/status attribute indicating a TODO is done"}]]}

   :todoapp/add-item-fn
   {:depends
    [:todoapp/item-schema]
    :txes
    [[{:db/ident :add-item
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
                                   :item/index  next-index}])})}]]}

   :todoapp/list-schema
   {:depends
    [:todoapp/item-schema]
    :txes
    [[{:db/id                 #db/id[:db.part/db]
       :db/ident              :list/name
       :db/valueType          :db.type/string
       :db/cardinality        :db.cardinality/one
       :db/doc                "The name of a TODO list"
       :db.install/_attribute :db.part/db}

      {:db/id                 #db/id[:db.part/db]
       :db/ident              :list/items
       :db/isComponent        true
       :db/valueType          :db.type/ref
       :db/cardinality        :db.cardinality/many
       :db/doc                "All the items in a given TODO list"
       :db.install/_attribute :db.part/db}

      {:db/id                 #db/id[:db.part/db]
       :db/ident              :list/default?
       :db/valueType          :db.type/boolean
       :db/unique             :db.unique/identity
       :db/cardinality        :db.cardinality/one
       :db/doc                "Optional attribute meant for one list. The list which has :list/default?
     set to true should not be deleted and items with no list should be migrated to it."
       :db.install/_attribute :db.part/db}]

     [{:db/id         #db/id[:db.part/user]
       :list/name     "Default List"
       :list/default? true
       :db/doc        "Default list for old data entries that had no list or for users who
     do not want to manage multiple lists."}]]}

   :todoapp/list-fns
   {:depends
    [:todoapp/add-item-fn
     :todoapp/list-schema]
    :txes
    [[{:db/ident :reparent-orphan-items
       :db/doc   "Find all items that are not part of a list and add them to the default list.."
       :db/id    (d/tempid :db.part/user)
       :db/fn    (d/function
                   {:lang     :clojure
                    :requires '[[datomic.api :as d :refer [q]]]
                    :params   '[db]
                    :code     '(let [orphan-items
                                     (q '[:find ?e
                                          :where
                                          [?e :item/status]
                                          (not [_ :list/items ?e])]
                                        db)

                                     reparent-tx-data
                                     (mapv (fn [[id]]
                                             {:db/id      [:list/default? true]
                                              :list/items id})
                                           orphan-items)]
                                 (conj reparent-tx-data
                                       {:db/id  (d/tempid :db.part/tx)
                                        :db/doc "Moving all orphan items to the default list."}))})}
      {:db/ident :add-list
       :db/doc   "Create a new list with no items and provided name."
       :db/id    (d/tempid :db.part/user)
       :db/fn    (d/function
                   {:lang     :clojure
                    :requires '[[datomic.api :as d :refer [q]]]
                    :params   '[db name]
                    :code     '[{:db/id     (d/tempid :db.part/user -1)
                                 :list/name name}]})}
      {:db/ident :add-item-to-list
       :db/doc   "Create a new todo item at the end of the given list with the provided text and a status of :item.status/todo."
       :db/id    (d/tempid :db.part/user)
       :db/fn    (d/function
                   {:lang     :clojure
                    :requires '[[datomic.api :as d :refer [q]]]
                    :params   '[db list-id text]
                    :code     '(let [max-index (ffirst (q '[:find (max ?i)
                                                            :in $ ?list
                                                            :where
                                                            [?item :item/index ?i]
                                                            [?list :list/items ?item]]
                                                          db
                                                          list-id))
                                     next-index (if max-index
                                                  (inc max-index)
                                                  0)]
                                 [{:db/id       (d/tempid :db.part/user -1)
                                   :item/status :item.status/todo
                                   :item/text   text
                                   :item/index  next-index}
                                  {:db/id      list-id
                                   :list/items (d/tempid :db.part/user -1)}])})}]
     ;; Move orphans to default list and redefine :add-item in same transaction
     [[:reparent-orphan-items]
      {:db/ident :add-item
       :db/doc   "Create a new todo item at the end of the default list with the provided text and a status of :item.status/todo."
       :db/id    (d/tempid :db.part/user)
       :db/fn    (d/function
                   {:lang     :clojure
                    :params   '[db text]
                    :code     '[[:add-item-to-list [:list/default? true] text]]})}]]}})