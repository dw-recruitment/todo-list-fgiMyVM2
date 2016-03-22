(ns todoapp.database-test
  (:require [clojure.test :refer :all]
            [datomic.api :as d :refer [q db]]
            [todoapp.database :as todo-db]))

(def uri "datomic:mem://todos-test")

(defn test-fixture
  [f]
  (todo-db/init uri)
  (f)
  (d/delete-database uri))

(use-fixtures :each test-fixture)

(deftest add-item-test
  (testing "First item has index 0"
    (let [conn (d/connect uri)
          {:keys [db-after]} @(todo-db/add-item conn {:text "First item"})]
      (is (= #{[0]}
             (q '[:find ?i :where [_ :item/index ?i]] db-after)))))
  (testing "Second item has index 1"
    (let [conn (d/connect uri)
          {:keys [db-after]} @(todo-db/add-item conn {:text "Second item"})]
      (is (= #{[0] [1]}
             (q '[:find ?i :where [_ :item/index ?i]] db-after))))))

(deftest complete-item-test
  (let [conn (d/connect uri)
        {:keys [tempids db-after]} @(todo-db/add-item conn {:text "An item"})
        eid (val (first tempids))]
    (testing "New item has status :todo"
      (is (= :todo (todo-db/status (d/entity db-after eid)))))
    (testing "After status change item has status :done"
      (let [{:keys [db-after]} @(todo-db/set-item-status conn eid :done)]
        (is (= :done (todo-db/status (d/entity db-after eid))))))))

(comment
  (run-tests)
  )
