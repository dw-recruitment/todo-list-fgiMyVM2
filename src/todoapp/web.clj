(ns todoapp.web
  (:require [clojure.pprint :refer [pprint]]
            [com.stuartsierra.component :as component]
            [compojure.core :refer [routes GET POST]]
            [datomic.api :as d :refer [db q]]
            [hiccup.element :refer [link-to]]
            [hiccup.page :refer [html5 include-css]]
            [hiccup.form :as form]
            [hiccup.util :refer [escape-html]]
            [ring.adapter.jetty :refer [run-jetty]]
            [ring.middleware.content-type :refer [wrap-content-type]]
            [ring.middleware.params :refer [wrap-params]]
            [ring.middleware.resource :refer [wrap-resource]]
            [ring.middleware.stacktrace :refer [wrap-stacktrace]]
            [ring.util.response :as response]
            [todoapp.database :as todo-db]))

(defn pretty-pre
  "Pretty print x to a string wrapped in a [:pre]."
  [x]
  [:pre (escape-html (with-out-str (pprint x)))])

(defn maybe-parse-long
  "Like Long/parseLong, but if s can't be parsed as a long,
  return nil instead of throwing an exception."
  [s]
  (when (string? s)
    (try (Long/parseLong s)
         (catch NumberFormatException _
           nil))))

(defn item-map->hiccup
  "Generate hiccup for one item"
  [{:keys [id text status]}]
  [:div.item-row-fc564
   [:span
    {:class (if (= :done status)
              "item-cell-d4622 default-content-322be item-text-completed-f96ab"
              "item-cell-d4622 default-content-322be")}
    (escape-html text)]
   (form/form-to {:class "item-cell-d4622 item-cell-one-col-1c259 update-form-f72f0"}
                 [:post "/update-item"]
                 [:input {:type  "hidden"
                          :name  "item-id"
                          :value (escape-html id)}]
                 [:input {:type  "hidden"
                          :name  "item-status"
                          ;; Opposite of current value since this value is for an update
                          :value (if (= :done status)
                                   "todo"
                                   "done")}]
                 [:button {:type  "submit"
                           :class "horizontal-fill-1d2e3"}
                  (if (= :done status)
                    "Undo"
                    "Complete")])
   (form/form-to {:class "item-cell-d4622 item-cell-one-col-1c259 delete-form-baf1a"}
                 [:post "/delete-item"]
                 [:input {:type  "hidden"
                          :name  "item-id"
                          :value (escape-html id)}]
                 [:button {:type "submit"
                           :class "horizontal-fill-1d2e3"}
                  "Delete"])])

(defn list-name->hiccup
  "Generate hiccup for one todo list in the list of todo lists"
  [db-val selected-id {:keys [id name default?]}]
  (let [target (if default?
                 (str "/")
                 (str "/list/" (escape-html id)))]
    [:div.item-row-fc564
     ;; Checks entity equality instead of (= id selected-id)
     ;; because selected-id might be a lookup ref
     (if (or (= (d/entity db-val selected-id)
                (d/entity db-val id)))
       [:span.item-cell-d4622.default-content-322be (escape-html name)]
       (link-to {:class "item-cell-d4622 show-hover-2110d default-content-322be"}
                target
                (escape-html name)))
     [:span.item-cell-d4622.item-cell-two-col-589dc]]))

(defn list-page
  "Generate hiccup for a full page with a todo list"
  [database list-id]
  {:pre [(or (nil? list-id)
             (todo-db/eid? list-id))]}
  (when list-id
    (let [db-val (db (:conn database))
          todo-list (todo-db/eid->list db-val list-id)
          lists (todo-db/get-lists db-val)]
      (html5
        [:head [:title "TODO List Manager"]
         (include-css "/styles.css")]
        [:body
         [:div
          [:h1 "TODO List Manager"]]
         [:div
          [:div
           [:h2.default-content-322be (escape-html (:name todo-list))]
           [:div.item-table-388e6
            (map item-map->hiccup (:items todo-list))]
           (form/form-to {:class "item-table-388e6"}
                         [:post "/new-item"]
                         [:div.item-row-fc564
                          [:span.item-cell-d4622
                           [:input {:name "item-text"
                                    :class "horizontal-fill-1d2e3"}]
                           [:input {:type  "hidden"
                                    :name  "list-id"
                                    :value (escape-html (:id todo-list))}]]
                          [:span.item-cell-d4622.item-cell-two-col-589dc
                           [:button {:type  "submit"
                                     :class "horizontal-fill-1d2e3"}
                            "Add New Item"]]])]
          [:div
           [:h2 "Lists"]
           [:div.item-table-388e6
            (map (partial list-name->hiccup db-val list-id) lists)]
           (form/form-to {:class "item-table-388e6"}
                         [:post "/new-list"]
                         [:span.item-cell-d4622
                          [:input {:name "list-name"
                                   :class "horizontal-fill-1d2e3"}]]
                         [:span.item-cell-d4622.item-cell-two-col-589dc
                          [:button {:type "submit"
                                    :class "horizontal-fill-1d2e3"}
                           "Add New List"]])]]
         [:div.footer-297dc (link-to "/about" "About TODO List Manager")]]))))

(defn redirect-to-list
  "Generate a redirect to a list based on the list id
  or the id of an item in the list.

  (redirect-to-list db-val m)

    db-val - Datomic database value
    m - a map with either :item-id or :list-id key"
  [db-val {:keys [item-id list-id]}]
  (let [list-id (or list-id
                    (when item-id
                      (todo-db/item-id->list-id db-val item-id)))
        target (if (or (not list-id)
                       (todo-db/default-list? db-val list-id))
                 "/"
                 (str "/list/" list-id))]
    (response/redirect target :see-other)))

(defn post-new-item
  "Handle a /new-item POST"
  [database {{list-id-raw "list-id"
              item-text   "item-text"} :params}]
  (let [list-id (maybe-parse-long list-id-raw)]
    (cond
      (and list-id-raw
           (not list-id))
      (-> (response/response "list-id should be numeric")
          (response/status 400))

      (not item-text)
      (-> (response/response "item-text is required")
          (response/status 400))

      :else
      (let [{:keys [db-after]}
            (todo-db/add-item database {:list-id list-id
                                        :text    item-text})]
        ;; Redirects so user can refresh the page without resending POST data.
        ;; If this app is ever deployed on multiple load-balanced servers,
        ;; some coordination might be needed to make sure client sees the most
        ;; recent items.
        (redirect-to-list db-after {:list-id list-id})))))

(defn post-new-list
  "Handle /new-list POST"
  [database list-name]
  (let [{:keys [db-after list-id]}
        (todo-db/add-list database {:name list-name})]
    (redirect-to-list db-after {:list-id list-id})))

(defn post-update-item
  "Handle /update-item POST"
  [database {{item-id-raw "item-id"
              item-status "item-status"} :params}]
  (let [id (maybe-parse-long item-id-raw)
        status (keyword item-status)]
    (if-not (and id status)
      (-> (response/response "item-id and item-status are required")
          (response/status 400))
      (let [{:keys [db-after]} (todo-db/update-item database {:id     id
                                                              :status status})]
        (redirect-to-list db-after {:item-id id})))))

(defn post-delete-item
  "Handle /delete-item POST"
  [database item-id]
  (if-not item-id
    (-> (response/response "item-id is required and should be numeric")
        (response/status 400))
    (let [{:keys [db-before]} (todo-db/retract-item database item-id)]
      (redirect-to-list db-before {:item-id item-id}))))

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
      [:li "Adding new items to the list"]
      [:li "Marking items as complete"]
      [:li "Deleting items"]]
     [:h2 "Planned Features:"]
     [:ul
      [:li "Multiple lists"]]]))

(defn make-handler
  "Create a handler for ring requests that has access to the
  DatomicDatabase component `database`."
  [database]
  (-> (routes (GET "/" [] (list-page database [:list/default? true]))
              (GET "/list/:list-id" [list-id] (list-page database (maybe-parse-long list-id)))
              (POST "/new-item" req (post-new-item database req))
              (POST "/update-item" req (post-update-item database req))
              (POST "/delete-item" [item-id] (post-delete-item database (maybe-parse-long item-id)))
              (POST "/new-list" [list-name] (post-new-list database list-name))
              (GET "/about" [] (about-page)))
      (wrap-resource "public")
      (wrap-content-type)
      (wrap-params)
      (wrap-stacktrace)))

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