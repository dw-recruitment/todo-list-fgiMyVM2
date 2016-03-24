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

(def config {:web-server-port 0
             :database-uri    uri})

(def system nil)

(defn test-fixture
  [f]
  (alter-var-root #'system (constantly (component/start (app/todo-system config))))
  (f)
  (alter-var-root #'system (fn [system] (component/stop system) nil))
  (d/delete-database uri))

(use-fixtures :each test-fixture)

(deftest smoke-test
  (testing "Jetty starts and serves home page"
    (let [port (web/get-bound-port (:web-server system))
          response (client/get (str "http://localhost:" port)
                               {:throw-exceptions false})]
      (is (= 200 (:status response))))))

(deftest new-item-test
  (testing "New item shows up after adding"
    (let [new-item-text "Remember the milk"
          port (web/get-bound-port (:web-server system))
          response (client/post (str "http://localhost:" port "/new-item")
                                {:form-params      {:item-text new-item-text}
                                 :throw-exceptions false})]
      (is (= 200 (:status response)))
      (is (string/includes? (:body response) new-item-text)))))

(deftest complete-item-test
  (testing "Item is complete in the database after update"
    (let [{:keys [db-after item-id]} (todo-db/add-item (:database system)
                                                       {:text "Do something fun"})
          port (web/get-bound-port (:web-server system))
          response (client/post (str "http://localhost:" port "/update-item")
                                {:form-params      {:item-id     item-id
                                                    :item-status "done"}
                                 :throw-exceptions false})]
      (is (= 200 (:status response)))
      ;; After adding item but before POST
      (is (= :todo (:status (todo-db/eid->item db-after item-id))))
      ;; Latest database state, after POST
      (is (= :done (:status (todo-db/eid->item (-> system :database :conn db) item-id)))))))

(deftest delete-item-test
  (testing
    (let [{:keys [db-after item-id]} (todo-db/add-item (:database system)
                                                       {:text "Do something fun"})
          port (web/get-bound-port (:web-server system))
          response (client/post (str "http://localhost:" port "/delete-item")
                                {:form-params      {:item-id item-id}
                                 :throw-exceptions false})]
      ;; After adding item but before POST
      (is (todo-db/eid->item db-after item-id))
      ;; Latest database state, after POST
      (is (not (todo-db/eid->item (-> system :database :conn db) item-id)))
      (component/stop system))))

(comment
  (run-tests)
  )