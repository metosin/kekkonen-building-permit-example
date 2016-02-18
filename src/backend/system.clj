(ns backend.system
  (:require [com.stuartsierra.component :as component]
            [palikka.components.http-kit :as http-kit]
            [backend.handler :as handler]))

(def default-users
  {1 {:id 1
      :name "applicant"
      :role :applicant}
   2 {:id 2
      :name "authority"
      :role :authority}
   3 {:id 3
      :name "admin"
      :role :admin}})

(defn new-system [config]
  (component/map->SystemMap
    {:state {:permits (atom {})
             :users (atom default-users)}
     :http (component/using
             (http-kit/create
               (:http config)
               {:fn
                (if (:dev-mode? config)
                  ; re-create handler on every request
                  (fn [system] #((handler/create system) %))
                  handler/create)})
             [:state])}))
