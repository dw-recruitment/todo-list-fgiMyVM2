(ns todoapp.web-test
  (:require [com.stuartsierra.component :as component]
            [clj-http.client :as client]
            [clojure.string :as string]
            [clojure.test :refer :all]
            [clojure.test.check :as tc]
            [clojure.test.check.clojure-test :refer [defspec]]
            [clojure.test.check.generators :as gen]
            [clojure.test.check.properties :as prop]
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


(defspec maybe-parse-long-parses
         1000
         (prop/for-all [i gen/int]
                       (= i (web/maybe-parse-long (str i)))))

(defspec maybe-parse-long-no-exceptions
         100
         (prop/for-all [s (gen/one-of [gen/string (gen/return nil) gen/any])]
                       (let [parsed (web/maybe-parse-long s)]
                         (or (nil? parsed)
                             (number? parsed)))))

(deftest smoke-test
  (testing "Jetty starts and serves pages"
    (let [port (web/get-bound-port (:web-server system))]
      (testing "Home page"
        (is (= 200 (:status (client/get (str "http://localhost:" port)
                                        {:throw-exceptions false})))))
      (testing "About page"
        (is (= 200 (:status (client/get (str "http://localhost:" port "/about")
                                        {:throw-exceptions false}))))))))

(deftest redirect-to-list-test
  (let [database (:database system)
        {:keys [list-id]} (todo-db/add-list database {:name "my list"})
        {default-list-item-id :item-id} (todo-db/add-item database {:text "celebrate"})
        {my-list-item-id :item-id
         db-val          :db-after} (todo-db/add-item database {:list-id list-id
                                                                :text    "have fun"})]
    (testing "Redirect to list page given list id"
      (is (= (str "/list/" list-id)
             (-> (web/redirect-to-list db-val {:list-id list-id})
                 (get-in [:headers "Location"])))))
    (testing "Redirect to list page given item id"
      (is (= (str "/list/" list-id)
             (-> (web/redirect-to-list db-val {:item-id my-list-item-id})
                 (get-in [:headers "Location"])))))
    (testing "Redirect to home page given item id from default list"
      (is (= "/"
             (-> (web/redirect-to-list db-val {:item-id default-list-item-id})
                 (get-in [:headers "Location"])))))
    (testing "Redirect to home page if list and item ID's are missing")
    (is (= "/"
           (-> (web/redirect-to-list db-val {})
               (get-in [:headers "Location"]))))))

(deftest new-item-test
  (let [new-item-text "Remember the milk"
        port (web/get-bound-port (:web-server system))]
    (testing "New item shows up after adding"
      (let [response (client/post (str "http://localhost:" port "/new-item")
                                  {:form-params      {:item-text new-item-text}
                                   :throw-exceptions false})]
        (is (= 200 (:status response)))
        (is (string/includes? (:body response) new-item-text))))
    (testing "Bad request when :item-text is omitted"
      (is (= 400 (:status (client/post (str "http://localhost:" port "/new-item")
                                       {:form-params      {}
                                        :throw-exceptions false})))))))

(deftest new-item-in-list-test
  (let [{:keys [list-id]} (todo-db/add-list (:database system) {:name "My List"})
        new-item-text "Remember the milk"
        port (web/get-bound-port (:web-server system))]
    (testing "New item shows up after adding to specific list"
      (let [response (client/post (str "http://localhost:" port "/new-item")
                                  {:form-params      {:item-text new-item-text
                                                      :list-id   list-id}
                                   :throw-exceptions false})]
        (is (= 200 (:status response)))
        (is (string/includes? (:body response) new-item-text))))
    (testing "Bad request when :list-id is provided but :item-text is omitted"
      (is (= 400 (:status (client/post (str "http://localhost:" port "/new-item")
                                       {:form-params      {:list-id list-id}
                                        :throw-exceptions false})))))))

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

(deftest new-list-test
  (let [port (web/get-bound-port (:web-server system))
        response (client/post (str "http://localhost:" port "/new-list")
                              {:form-params      {:list-name "My new list"}
                               :throw-exceptions false})]
    (is (= 200 (:status response)))
    (testing "/new-list redirects to new list, which exists in the database"
      (let [[_ list-id] (re-matches #"http://localhost:\d+/list/(\d+)"
                                    (peek (:trace-redirects response)))]
        (is list-id)
        (is (todo-db/eid->list (-> system :database :conn db) (Long/parseLong list-id)))))))

(deftest delete-item-test
  (testing
    (let [{db-after-add :db-after
           :keys        [item-id]} (todo-db/add-item (:database system)
                                                     {:text "Do something fun"})
          port (web/get-bound-port (:web-server system))]
      (client/post (str "http://localhost:" port "/delete-item")
                   {:form-params      {:item-id item-id}
                    :throw-exceptions false})

      (testing "Bad requests"
        (testing "missing item id"
          (is (= 400 (:status (client/post (str "http://localhost:" port "/delete-item")
                                           {:form-params      {}
                                            :throw-exceptions false})))))
        (testing "non-numeric item id"
          (is (= 400 (:status (client/post (str "http://localhost:" port "/delete-item")
                                           {:form-params      {:item-id "my item"}
                                            :throw-exceptions false}))))))

      ;; After adding item but before POST
      (is (todo-db/eid->item db-after-add item-id))
      ;; Latest database state, after POST
      (is (not (todo-db/eid->item (-> system :database :conn db) item-id)))
      (component/stop system))))

(comment
  (run-tests)
  )