(ns backend.system
  (:require [com.stuartsierra.component :as component]
            [palikka.components.http-kit :as http-kit]
            [backend.handler :as handler]))

(def default-users
  {1 {:user-id 1
      :name "applicant-1"
      :role :applicant}
   2 {:user-id 2
      :name "applicant-2"
      :role :applicant}
   3 {:user-id 3
      :name "authority-1"
      :role :authority}
   4 {:user-id 4
      :name "authority-2"
      :role :authority}
   5 {:user-id 5
      :name "admin"
      :role :admin}})

(defn new-system [config]
  (component/map->SystemMap
    {:state {:permits (atom {})
             :users (atom default-users)}
     :http (component/using
             (http-kit/create (:http config) {:fn (partial handler/create config)})
             [:state])}))
