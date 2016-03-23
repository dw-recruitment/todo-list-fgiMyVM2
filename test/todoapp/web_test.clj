(ns todoapp.web-test
  (:require [com.stuartsierra.component :as component]
            [clj-http.client :as client]
            [clojure.test :refer :all]
            [todoapp.app :as app]
            [todoapp.web :as web]))

(deftest smoke-test
  (testing "Jetty starts and serves home page"
    (let [system (component/start
                   (app/todo-system
                     {:web-server-port 0
                      :database-uri    "datomic:mem://todos-test"}))
          port (-> system
                   :web-server
                   :server
                   (.getConnectors)
                   first
                   (.getLocalPort))
          response (client/get (str "http://localhost:" port))]
      (is (= 200 (:status response)))
      (component/stop system))))

(comment
  (run-tests)
  )