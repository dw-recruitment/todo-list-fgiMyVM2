(ns todoapp.database-test
  (:require [com.stuartsierra.component :as component]
            [clojure.test :refer :all]
            [datomic.api :as d :refer [q db]]
            [todoapp.database :as todo-db]))

(def uri "datomic:mem://todos-test")

(defn test-fixture
  [f]
  (f)
  (d/delete-database uri))

(use-fixtures :each test-fixture)

(deftest add-item-test
  (let [database (component/start (todo-db/new-database uri))]
    (testing "First item has index 0"
      (let [{:keys [db-after]} @(todo-db/add-item database {:text "First item"})]
        (is (= #{[0]}
               (q '[:find ?i :where [_ :item/index ?i]] db-after)))))
    (testing "Second item has index 1"
      (let [{:keys [db-after]} @(todo-db/add-item database {:text "Second item"})]
        (is (= #{[0] [1]}
               (q '[:find ?i :where [_ :item/index ?i]] db-after)))))))

(deftest complete-item-test
  (let [database (component/start (todo-db/new-database uri))
        {:keys [tempids db-after]} @(todo-db/add-item database {:text "An item"})
        eid (val (first tempids))]
    (testing "New item has status :todo"
      (is (= :todo (todo-db/status (d/entity db-after eid)))))
    (testing "After status change item has status :done"
      (let [{:keys [db-after]} @(todo-db/set-item-status database eid :done)]
        (is (= :done (todo-db/status (d/entity db-after eid))))))))

(comment
  (run-tests)
  )
