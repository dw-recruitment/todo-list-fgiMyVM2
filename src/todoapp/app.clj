(ns todoapp.app
  (:require [com.stuartsierra.component :as component]
            [todoapp.database :refer [new-database]]
            [todoapp.web :refer [new-web-handler new-web-server]])
  (:gen-class))


(defn todo-system [config]
  (let [{:keys [database-uri web-server-port]} config]
    (component/system-map
      :database (new-database database-uri)
      :web-handler (component/using
                     (new-web-handler)
                     [:database])
      :web-server (component/using
                    (new-web-server web-server-port)
                    [:web-handler]))))