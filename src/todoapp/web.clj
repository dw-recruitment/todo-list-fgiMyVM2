(ns todoapp.web
  (:require [compojure.core :refer [defroutes GET]]
            [hiccup.page :refer [html5]]
            [hiccup.element :refer [image]]
            [ring.adapter.jetty :refer [run-jetty]]
            [ring.middleware.content-type :refer [wrap-content-type]]
            [ring.middleware.resource :refer [wrap-resource]])
  (:gen-class))

(defn home-page
  []
  (html5
    [:head [:title "TODO List Manager"]]
    [:body
     [:h1 "TODO List Manager"]
     (image
       {:width "574" :height "51"}
       "/images/under-construction.gif"
       "Under Construction")]))

(defn about-page
  []
  (html5
    [:head [:title "About the TODO List Manager"]]
    [:body
     [:h1 "About the TODO List Manager"]
     [:p "You can use this application to manage a list of things you need to get done."]
     [:h2 "Planned Features:"]
     [:ul
      [:li "Displaying items"]
      [:li "Saving and restoring items"]
      [:li "Adding new items to the list"]
      [:li "Marking items as complete"]
      [:li "Deleting items"]
      [:li "Multiple lists"]]]))

(defroutes handler
           (GET "/" [] (home-page))
           (GET "/about" [] (about-page)))

(def app
  (-> handler
      (wrap-resource "public")
      (wrap-content-type)))

(defn -main
  [& args]
  (println "Starting server. Press ctrl+c to quit.")
  (run-jetty #'app {:port 3000 :join? true}))

(comment
  ;; Run in repl for development
  (defonce server (run-jetty #'app {:port 3000 :join? false}))
  )