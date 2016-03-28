(ns user
  (:require [com.stuartsierra.component :as component]
            [clojure.edn :as edn]
            [clojure.java.io :as io]
            [clojure.string :as string]
            [clojure.tools.namespace.repl :refer [refresh]]
            [datomic.api :as d :refer [q db]]
            [todoapp.app :as app]
            [todoapp.database :as todo-db]
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
  (alter-var-root #'system (fn [s] (component/stop s) nil)))

(defn go
  []
  (new-system (edn/read-string (slurp (io/resource "default-config.edn"))))
  (start-system))

(defn restart
  []
  (stop-system)
  (refresh :after 'user/go))

(defn gen-hash
  "Generate a UUCSS \"hash\" (it's not really hashing anything)
  https://github.com/oakmac/snowflake-css/blob/master/00-scrap/notes.md"
  []
  (let [gen-hash* #(format "%05x" (rand-int 1048569))]
    (loop [hash (gen-hash*)]
      (if (and (re-find #"[a-z]" hash)
               (re-find #"[0-9]" hash))
        hash
        (recur (gen-hash*))))))

(comment
  (go)
  system
  (restart)
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
  (clojure.pprint/print-table
    [:list/_items :db/id :item/text :item/status :item/index]
    (->>
      (q '[:find (pull ?e [* {:item/status [:db/ident]} {:list/_items [:list/name :db/id]}])
           :where [?e :item/status]]
         (-> system :database :conn db))
      (map first)
      (sort-by (fn [x] (+ (:db/id (:list/_items x))
                          (:item/index x))))))

  (todo-db/add-list (-> system :database) {:name "Groceries"})

  ;; All lists (with items)
  (q '[:find (pull ?e [* {:list/items [* {:item/status [:db/ident]}]}])
       :where [?e :list/name]]
     (-> system :database :conn db))

  ;; Pull default list
  (todo-db/eid->list (-> system :database :conn db) [:list/default? true])

  ;; Get all lists (no items)
  (todo-db/get-lists (-> system :database :conn db))

  (symbol (gen-hash))

  )