(ns backend.handler
  (:require [plumbing.core :as p]
            [kekkonen.cqrs :refer :all]
            [kekkonen.core :as k]
            [schema.core :as s]
            [backend.static :as static]
            [backend.building-permit :as building-permit]
            [backend.session :as app-session]
            [metosin.ring.util.cache :as cache]
            [ring.middleware.session :as session]))

(defn api [{:keys [state]}]
  (cqrs-api
    {:swagger {:info {:title "Building Permit application"
                      :description "a complex simulated real-life case example showcase project for http://kekkonen.io"}
               :securityDefinitions {:api_key {:type "apiKey", :name "x-apikey", :in "header"}}}
     :swagger-ui {:validator-url nil
                  :path "/api-docs"}
     :core {:handlers {(k/namespace {:name :building-permit
                                     :require-session true
                                     :load-current-user true})
                       'backend.building-permit

                       :session 'backend.session
                       (k/namespace {:name :users
                                     :require-session true
                                     :load-current-user true})
                       'backend.users}
            :user [[:require-session app-session/require-session]
                   [:load-current-user app-session/load-current-user]
                   [:requires-role app-session/requires-role]
                   [::building-permit/retrieve-permit building-permit/retrieve-permit]
                   [::building-permit/requires-state building-permit/requires-state]
                   [::building-permit/requires-claim building-permit/requires-claim]]
            :context {:state state}}}))

(defn create [config system]
  (let [kekkonen (-> (if (:dev-mode? config)
                       ; recreate handler for each request
                       (fn [req] ((api system) req))
                       (api system))
                     (session/wrap-session))
        static (static/create-handler config)]
    (-> (fn [req]
          (or (static req)
              (kekkonen req)))
        (cache/wrap-cache {:value cache/no-cache
                           :default? true}))))
