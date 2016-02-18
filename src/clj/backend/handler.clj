(ns backend.handler
  (:require [plumbing.core :as p]
            [kekkonen.cqrs :refer :all]
            [kekkonen.core :as k]
            [schema.core :as s]
            [backend.building-permit :as building-permit]
            [backend.session :as app-session]
            [ring.middleware.resource :as resource]
            [ring.middleware.content-type :as content-type]
            [ring.middleware.session :as session]
            [ring.middleware.session.memory :as memory-store]))

(def session-store (memory-store/memory-store))

(p/defnk create [state]
  (-> (cqrs-api
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
                :context {:state state}}})
      ;; FIXME: Exposes EVERYTHING on classpath
      (resource/wrap-resource "")
      (content-type/wrap-content-type)
      (session/wrap-session {:store session-store})))
