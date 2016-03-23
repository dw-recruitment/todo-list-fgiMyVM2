(ns todoapp.web
  (:require [com.stuartsierra.component :as component]
            [compojure.core :refer [routes GET]]
            [datomic.api :as d :refer [db q]]
            [hiccup.page :refer [html5]]
            [hiccup.form :as form]
            [ring.adapter.jetty :refer [run-jetty]]
            [ring.middleware.content-type :refer [wrap-content-type]]
            [ring.middleware.resource :refer [wrap-resource]]
            [todoapp.database :as todo-db]))

(defn todo-map->hiccup
  [{:keys [id text status]}]
  [:tr
   [:td (form/check-box
          {:disabled true}
          (str "checkbox-" id)
          (= :done status))]
   [:td text]])

(defn home-page
  [database]
  (html5
    [:head [:title "TODO List Manager"]]
    [:body
     [:h1 "TODO List Manager"]
     [:table
      (map todo-map->hiccup
           (todo-db/get-items (db (:conn database))))]]))

(defn about-page
  []
  (html5
    [:head [:title "About the TODO List Manager"]]
    [:body
     [:h1 "About the TODO List Manager"]
     [:p "You can use this application to manage a list of things you need to get done."]
     [:h2 "Features:"]
     [:ul
      [:li "Displaying items"]
      [:li "Saving and restoring items"]
      ]
     [:h2 "Planned Features:"]
     [:ul
      [:li "Adding new items to the list"]
      [:li "Marking items as complete"]
      [:li "Deleting items"]
      [:li "Multiple lists"]]]))

(defn make-handler
  "Create a handler for ring requests that has access to the
  DatomicDatabase component `database`."
  [database]
  (-> (routes (GET "/" [] (home-page database))
              (GET "/about" [] (about-page))
              (GET "/test" [] "hello"))
      (wrap-resource "public")
      (wrap-content-type)))

(defn make-dynamic-handler
  "Calls make-handler on every request so new routes
  are available without restarting the app."
  [database]
  (fn [& args]
    (apply (make-handler database) args)))

(defrecord WebHandler [database handler-fn]
  component/Lifecycle
  (start [component]
    (assoc component :handler-fn (make-dynamic-handler database)))
  (stop [component]
    (assoc component :handler-fn nil)))

(defn new-web-handler
  []
  (map->WebHandler {}))

(defrecord WebServer [web-handler port database server]
  component/Lifecycle
  (start [component]
    (let [server (run-jetty (:handler-fn web-handler) {:port port :join? false})]
      (assoc component :server server)))
  (stop [component]
    (.stop server)
    (assoc component :server nil)))

(defn new-web-server [port]
  (map->WebServer {:port port}))