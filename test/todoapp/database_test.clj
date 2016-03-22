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

(deftest add-todo
  (testing "First todo has index 0"
    (let [conn (d/connect uri)
          {:keys [db-after]} @(todo-db/add-item conn {:text "First item"})]
      (is (= #{[0]}
             (q '[:find ?i :where [_ :item/index ?i]] db-after)))))
  (testing "Second todo has index 1"
    (let [conn (d/connect uri)
          {:keys [db-after]} @(todo-db/add-item conn {:text "Second item"})]
      (is (= #{[0] [1]}
             (q '[:find ?i :where [_ :item/index ?i]] db-after))))))

(comment
  (run-tests)
  )
