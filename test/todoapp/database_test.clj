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
      (let [{:keys [db-after item-id]} (todo-db/add-item database {:text "First item"})]
        (is (= 0 (:index (todo-db/eid->item db-after item-id))))))
    (testing "Second item has index 1"
      (let [{:keys [db-after item-id]} (todo-db/add-item database {:text "Second item"})]
        (is (= 1 (:index (todo-db/eid->item db-after item-id))))))))

(deftest complete-item-test
  (let [database (component/start (todo-db/new-database uri))
        {:keys [db-after item-id]} (todo-db/add-item database {:text "An item"})]
    (testing "New item has status :todo"
      (is (= :todo (todo-db/status (d/entity db-after item-id)))))
    (testing "After status change item has status :done"
      (let [{:keys [db-after]} (todo-db/set-item-status database {:id item-id
                                                                   :status :done})]
        (is (= :done (todo-db/status (d/entity db-after item-id))))))))

(deftest eid->item-test
  (let [database (component/start (todo-db/new-database uri))
        item-text "Write more tests"
        {:keys [db-after item-id]} (todo-db/add-item database {:text item-text})]
    (testing "eid->item returns expected map"
      (is (= (todo-db/eid->item db-after item-id)
             {:id item-id
              :text item-text
              :index 0
              :status :todo})))))

(comment
  (run-tests)
  )
