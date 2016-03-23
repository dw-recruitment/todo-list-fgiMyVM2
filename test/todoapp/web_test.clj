(ns todoapp.web-test
  (:require [com.stuartsierra.component :as component]
            [clj-http.client :as client]
            [clojure.string :as string]
            [clojure.test :refer :all]
            [todoapp.app :as app]
            [todoapp.web :as web]))

(deftest smoke-test
  (testing "Jetty starts and serves home page"
    (let [system (component/start
                   (app/todo-system
                     {:web-server-port 0
                      :database-uri    "datomic:mem://todos-test"}))
          port (web/get-bound-port (:web-server system))
          response (client/get (str "http://localhost:" port))]
      (is (= 200 (:status response)))
      (component/stop system))))

(deftest new-todo-test
  (testing "New item shows up after adding"
    (let [new-item-text "Remember the milk"
          system (component/start
                   (app/todo-system
                     {:web-server-port 0
                      :database-uri    "datomic:mem://todos-test"}))
          port (web/get-bound-port (:web-server system))
          response (client/post (str "http://localhost:" port)
                                {:form-params {:new-item-text new-item-text}})]
      (is (= 200 (:status response)))
      (is (string/includes? (:body response) new-item-text))
      (component/stop system))))

(comment
  (run-tests)
  )