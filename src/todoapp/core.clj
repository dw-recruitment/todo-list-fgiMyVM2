(ns todoapp.core
  (:require [clojure.java.io :as io]
            [compojure.core :refer [defroutes GET]]
            [ring.adapter.jetty :refer [run-jetty]]
            [ring.middleware.content-type :refer [wrap-content-type]]
            [ring.middleware.resource :refer [wrap-resource]])
  (:gen-class))

(defroutes handler
           (GET "/" [] (slurp (io/resource "index.html"))))

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