(ns todoapp.database-test
  (:require [com.stuartsierra.component :as component]
            [clojure.test :refer :all]
            [clojure.test.check :as tc]
            [clojure.test.check.clojure-test :refer [defspec]]
            [clojure.test.check.generators :as gen]
            [clojure.test.check.properties :as prop]
            [datomic.api :as d :refer [q db]]
            [io.rkn.conformity :as conformity]
            [todoapp.database :as todo-db]
            [todoapp.database-norms :as database-norms]))

(def uri "datomic:mem://todos-test")

(defn test-fixture
  [f]
  (f)
  (d/delete-database uri))

(use-fixtures :each test-fixture)

(deftest add-and-get-list-test
  (let [database (component/start (todo-db/new-database uri))]
    (testing "Add a new list"
      (let [list-name "New List"
            {:keys [db-after list-id]} (todo-db/add-list database {:name list-name})]
        (is (= list-name (:name (todo-db/eid->list db-after list-id))))
        (testing "Add items to the new list"
          (let [item-text-1 "An item"
                item-text-2 "Other item"
                {item-id-1 :item-id} (todo-db/add-item database {:text    item-text-1
                                                                 :list-id list-id})
                {item-id-2 :item-id
                 :keys     [db-after]} (todo-db/add-item database {:text    item-text-2
                                                                   :list-id list-id})
                list (todo-db/eid->list db-after list-id)]
            (is (= 2 (count (:items list))))
            (testing "List has expected value"
              (is (= (select-keys list [:id :default? :name])
                     {:id       list-id
                      :default? false
                      :name     list-name}))
              (testing "Expected item values"
                (is (= (select-keys (-> list :items first)
                                    [:id :text :status :index])
                       {:id     item-id-1
                        :text   item-text-1
                        :status :todo
                        :index  0}))
                (is (= (select-keys (-> list :items second)
                                    [:id :text :status :index])
                       {:id     item-id-2
                        :text   item-text-2
                        :status :todo
                        :index  1})))
              (testing "eid->item gives same items as list->item"
                (is (= (:items list)
                       [(todo-db/eid->item db-after item-id-1)
                        (todo-db/eid->item db-after item-id-2)]))))))))))

(deftest add-item-test
  (let [database (component/start (todo-db/new-database uri))]
    (testing "First item has index 0"
      (let [{:keys [db-after item-id]} (todo-db/add-item database {:text "First item"})]
        (is (= 0 (:index (todo-db/eid->item db-after item-id))))))
    (testing "Second item has index 1"
      (let [{:keys [db-after item-id]} (todo-db/add-item database {:text "Second item"})]
        (is (= 1 (:index (todo-db/eid->item db-after item-id))))))))

(deftest complete-item-test
  (let [database (component/start (todo-db/new-database uri))
        {:keys [db-after item-id]} (todo-db/add-item database {:text "An item"})]
    (testing "New item has status :todo"
      (is (= :todo (:status (todo-db/eid->item db-after item-id)))))
    (testing "After status change item has status :done"
      (let [{:keys [db-after]} (todo-db/update-item database {:id     item-id
                                                              :status :done})]
        (is (= :done (:status (todo-db/eid->item db-after item-id))))))))

(deftest eid->item-test
  (let [database (component/start (todo-db/new-database uri))
        item-text "Write more tests"
        {:keys [db-after item-id]} (todo-db/add-item database {:text item-text})]
    (testing "eid->item returns expected map"
      (is (= (todo-db/eid->item db-after item-id)
             {:id     item-id
              :text   item-text
              :index  0
              :status :todo})))))

(deftest item-id->list-id-test
  (let [database (component/start (todo-db/new-database uri))
        list-ids (mapv (fn [n]
                         (:list-id (todo-db/add-list database {:name (str n)})))
                       (range 3))
        ids (doall
              (for [list-id list-ids
                    item-index (range 2)]
                (let [{:keys [item-id]} (todo-db/add-item database {:text    (str item-index)
                                                                    :list-id list-id})]
                  {:list-id list-id
                   :item-id item-id})))
        db-after (-> database :conn db)]
    (doseq [{:keys [list-id item-id]} ids]
      (is (= list-id (todo-db/item-id->list-id db-after item-id))))))

(deftest default-list?-test
  (let [database (component/start (todo-db/new-database uri))
        {:keys [db-after list-id]} (todo-db/add-list database {:name "Not default list"})
        default-list-id (:db/id (d/entity db-after [:list/default? true]))]
    (testing "New list is not the default list"
      (is (not (todo-db/default-list? db-after list-id))))
    (testing "Correctly identify default list"
      (is (todo-db/default-list? db-after default-list-id)))))

(defspec get-lists-test
         20
         (prop/for-all
           [names (gen/vector gen/string)]
           (try
             (let [database (component/start (todo-db/new-database uri))]
               (doseq [name names]
                 (todo-db/add-list database {:name name}))
               (let [db-after (db (:conn database))
                     lists (todo-db/get-lists db-after)
                     ;; First retrieved list should always be the default list
                     first-list (first lists)
                     rest-lists (vec (rest lists))]
                 (and
                   ;; Every list is present, in order
                   (= names (mapv :name rest-lists))
                   ;; First list and only first list is default
                   (:default? first-list)
                   (every? (complement :default?) rest-lists))))
             (finally (d/delete-database uri)))))

(defn in-any-list?
  "Truthy if item-id is a value of :list/items for any entity in db-val."
  [db-val item-id]
  (seq (q '[:find ?item
            :in $ ?item
            :where
            [_ :list/items ?item]]
          db-val
          item-id)))

(deftest retract-item-test
  (let [database (component/start (todo-db/new-database uri))
        {:keys [list-id]} (todo-db/add-list database {:name "A list"})
        {:keys [item-id]} (todo-db/add-item database {:list-id list-id
                                                      :text    "Something to do"})
        {:keys [db-before db-after]} (todo-db/retract-item database item-id)]
    (testing "Item is referenced before delete but not after"
      (is (in-any-list? db-before item-id))
      (is (not (in-any-list? db-after item-id))))))

;; Set up a database with an older schema, add items,
;: then migrate to newer one.
(deftest migration-with-orphan-items-test
  (with-redefs [todo-db/init (fn [uri] (d/create-database uri))]
    (let [database (component/start (todo-db/new-database uri))]
      (conformity/ensure-conforms (:conn database)
                                  database-norms/norms
                                  [:todoapp/item-schema
                                   :todoapp/add-item-fn])
      ;; This is three separate transactions because multiple :add-item calls
      ;; in the same transaction will all assign the same :item/index
      @(d/transact (:conn database) [[:add-item "Item a"]])
      @(d/transact (:conn database) [[:add-item "Item b"]])
      (let [{:keys [db-after]} @(d/transact (:conn database) [[:add-item "Item c"]])]
        ;; Sanity check: there should not be any list schema installed at this point.
        (testing ":list/name attribute is not installed"
          (is (empty? (q '[:find ?attr
                           :in $ ?attr
                           :where [_ :db/ident ?attr]]
                         db-after
                         :list/name)))))
      (conformity/ensure-conforms (:conn database)
                                  database-norms/norms
                                  [:todoapp/list-schema
                                   :todoapp/list-fns])
      ;; Add an item with post-migration :add-item for good measure.
      @(d/transact (:conn database) [[:add-item "Item d"]])
      (testing "Items are members of the default list"
        (let [db-after (-> database :conn db)
              default-list-id (:db/id (d/entity db-after [:list/default? true]))
              default-list-items (:items (todo-db/eid->list db-after default-list-id))]
          (is (= ["Item a" "Item b" "Item c" "Item d"]
                 (mapv :text default-list-items))))))))

(comment
  (run-tests)
  )
