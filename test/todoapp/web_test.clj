(ns todoapp.web-test
  (:require [com.stuartsierra.component :as component]
            [clj-http.client :as client]
            [clojure.string :as string]
            [clojure.test :refer :all]
            [datomic.api :as d :refer [db]]
            [todoapp.app :as app]
            [todoapp.database :as todo-db]
            [todoapp.web :as web]))

(def uri "datomic:mem://todos-test")

(defn test-fixture
  [f]
  (f)
  (d/delete-database uri))

(use-fixtures :each test-fixture)

(deftest smoke-test
  (testing "Jetty starts and serves home page"
    (let [system (component/start
                   (app/todo-system
                     {:web-server-port 0
                      :database-uri    uri}))
          port (web/get-bound-port (:web-server system))
          response (client/get (str "http://localhost:" port)
                               {:throw-exceptions false})]
      (is (= 200 (:status response)))
      (component/stop system))))

(deftest new-todo-test
  (testing "New item shows up after adding"
    (let [new-item-text "Remember the milk"
          system (component/start
                   (app/todo-system
                     {:web-server-port 0
                      :database-uri    uri}))
          port (web/get-bound-port (:web-server system))
          response (client/post (str "http://localhost:" port "/new-item")
                                {:form-params {:item-text new-item-text}
                                 :throw-exceptions false})]
      (is (= 200 (:status response)))
      (is (string/includes? (:body response) new-item-text))
      (component/stop system))))

(deftest complete-todo-test
  (testing "Item is complete in the database after update"
    (let [system (component/start
                   (app/todo-system
                     {:web-server-port 0
                      :database-uri    uri}))
          {:keys [db-after tempids]} @(todo-db/add-item (:database system)
                                                        {:text "Do something fun"})
          id (val (first tempids))
          port (web/get-bound-port (:web-server system))
          response (client/post (str "http://localhost:" port "/update-item")
                                {:form-params {:item-id     id
                                               :item-status "done"}
                                 :throw-exceptions false})]
      ;; After adding item but before POST
      (is (= :todo (:status (todo-db/eid->item db-after id))))
      ;; Latest database state, after POST
      (is (= :done (:status (todo-db/eid->item (-> system :database :conn db) id))))
      (component/stop system))))

(comment
  (run-tests)
  )