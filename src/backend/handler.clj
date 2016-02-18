(ns backend.handler
  (:require [plumbing.core :as p]
            [kekkonen.cqrs :refer :all]
            [schema.core :as s]
            [backend.building-permit :as building-permit]
            [backend.session :as app-session]
            [ring.middleware.session :as session]
            [ring.middleware.session.memory :as memory-store]))

(def session-store (memory-store/memory-store))

(p/defnk create [state]
  (-> (cqrs-api
        {:swagger {:info {:title "Building Permit application"
                          :description "a complex simulated real-life case example showcase project for http://kekkonen.io"}}
         :swagger-ui {:validator-url nil}
         :core {:handlers {:building-permit 'backend.building-permit
                           :session 'backend.session
                           :users 'backend.users}
                :user [[:require-session app-session/require-session]
                       [:load-current-user app-session/load-current-user]
                       [:requires-role app-session/requires-role]
                       [::building-permit/requires-state (constantly identity)]
                       [::building-permit/retrieve-permit building-permit/retrieve-permit]]
                :context {:state state}}})
      (session/wrap-session {:store session-store})))
