(ns backend.handler
  (:require [plumbing.core :as p]
            [kekkonen.cqrs :refer :all]
            [kekkonen.core :as k]
            [kekkonen.ring :as kr]
            [schema.core :as s]
            [backend.static :as static]
            [backend.building-permit :as building-permit]
            [backend.session :as app-session]
            [metosin.ring.util.cache :as cache]
            [ring.middleware.session :as session]
            [chord.http-kit :refer [with-channel]]
            [backend.chord :as chord]))

(defn create-api [{:keys [state chord]}]
  (cqrs-api
    {:swagger {:info {:title "Building Permit application"
                      :description "a complex simulated real-life case example showcase project for http://kekkonen.io"}
               :securityDefinitions {:api_key {:type "apiKey", :name "x-apikey", :in "header"}}}
     :swagger-ui {:validator-url nil
                  :path "/api-docs"}
     :core {:handlers {(k/namespace {:name :building-permit
                                     :interceptors [app-session/load-current-user]
                                     :require-session true})
                       'backend.building-permit

                       (k/namespace {:name :session})
                       'backend.session

                       (k/namespace {:name :users
                                     :interceptors [app-session/load-current-user]
                                     :require-session true})
                       'backend.users}
            :user [[:requires-role app-session/requires-role]
                   [::building-permit/retrieve-permit building-permit/retrieve-permit]
                   [::building-permit/requires-state building-permit/requires-state]
                   [::building-permit/requires-claim building-permit/requires-claim]]
            :context {:state state
                      :chord chord}}}))

(defn create-ws [{:keys [chord]}]
  (fn [req]
    (if (= "/ws" (:uri req))
      (with-channel req ws-ch {:format :transit-json}
        (chord/join chord ws-ch)))))

(defn create [config system]
  (let [kekkonen (-> (if (:dev-mode? config)
                       ; recreate handler for each request
                       (fn [req] ((create-api system) req))
                       (create-api system))
                     (session/wrap-session))
        static (static/create-handler config)
        ws     (create-ws system)]
    (-> (kr/routes
          [static
           ws
           kekkonen])
        (cache/wrap-cache {:value cache/no-cache
                           :default? true}))))
