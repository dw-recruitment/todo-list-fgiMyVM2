(ns todoapp.web
  (:require [com.stuartsierra.component :as component]
            [compojure.core :refer [routes GET POST]]
            [datomic.api :as d :refer [db q]]
            [hiccup.page :refer [html5]]
            [hiccup.form :as form]
            [hiccup.util :refer [escape-html]]
            [ring.adapter.jetty :refer [run-jetty]]
            [ring.middleware.content-type :refer [wrap-content-type]]
            [ring.middleware.params :refer [wrap-params]]
            [ring.middleware.resource :refer [wrap-resource]]
            [ring.util.response :as response]
            [todoapp.database :as todo-db]))

(defn item-map->hiccup
  [{:keys [id text status]}]
  [:tr
   [:td (form/check-box
          {:disabled true}
          (str "checkbox-" id)
          (= :done status))]
   [:td (escape-html text)]])

(defn home-page
  [database]
  (html5
    [:head [:title "TODO List Manager"]]
    [:body
     [:h1 "TODO List Manager"]
     [:table
      (map item-map->hiccup
           (todo-db/get-items (db (:conn database))))
      [:tr
       [:td]
       [:td (form/form-to [:post "/"]
                          [:input {:name "new-item-text"}]
                          " "
                          [:button {:type "submit"}
                           "Add New Item"])]]]]))

(defn post-new-item
  [database item-text]
  @(todo-db/add-item database {:text item-text})
  ;; Redirects so user can refresh the page without resending POST data.
  ;; If this app is ever deployed on multiple load-balanced servers,
  ;; some coordination might be needed to make sure client sees the most
  ;; recent items.
  (response/redirect "/" :see-other))

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
      [:li "Adding new items to the list"]]
     [:h2 "Planned Features:"]
     [:ul
      [:li "Marking items as complete"]
      [:li "Deleting items"]
      [:li "Multiple lists"]]]))

(defn make-handler
  "Create a handler for ring requests that has access to the
  DatomicDatabase component `database`."
  [database]
  (-> (routes (GET "/" [] (home-page database))
              (POST "/" [new-item-text] (post-new-item database new-item-text))
              (GET "/about" [] (about-page))
              (GET "/test" [] "hello"))
      (wrap-resource "public")
      (wrap-content-type)
      (wrap-params)))

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

(defrecord WebServer [web-handler port server]
  component/Lifecycle
  (start [component]
    (let [server (run-jetty (:handler-fn web-handler) {:port port :join? false})]
      (assoc component :server server)))
  (stop [component]
    (.stop server)
    (assoc component :server nil)))

(defn new-web-server [port]
  (map->WebServer {:port port}))

(defprotocol IGetBoundPort
  (-get-bound-port [server]))

(extend-protocol IGetBoundPort
  org.eclipse.jetty.server.Server
  (-get-bound-port [server]
    (-> server .getConnectors first .getLocalPort))

  WebServer
  (-get-bound-port [web-server]
    (when-let [server (:server web-server)]
      (-get-bound-port server))))

(defn get-bound-port
  "Get the local port a running Jetty server is bound to.
  Call on a WebServer component to get the port of its :server.
  Note that this could be different than the WebServer :port
  specified in configuration (if :port is 0, the local port
  will be automatically assigned)."
  [server]
  (-get-bound-port server))