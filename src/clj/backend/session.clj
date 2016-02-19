(ns backend.session
  (:require [plumbing.core :as p]
            [kekkonen.cqrs :refer :all]
            [schema.core :as s]
            [schema-tools.core :as st]
            [backend.users :as users]))

(defn session-identity [req]
  (or (some-> req :headers (get "x-apikey") Integer/parseInt)
      (-> req :session :identity)))

(defn require-session [result]
  {:enter (p/fnk [request :as m]
            (if (session-identity request)
              m
              (if (= :error result)
                (failure! {:status :unauthorized}))))})

(def load-current-user
  {:enter (p/fnk [request
                  [:state users]
                  :as context]
            (if-let [current-user (get @users (session-identity request))]
              (assoc-in context [:entities :current-user] current-user)
              context))})

(defn requires-role [allowed-roles]
  (p/fnk [[:entities [:current-user role]]
          :as context]
    (println role (or (= :admin role)
                      (contains? allowed-roles role)))
    (if (or (= :admin role)
            (contains? allowed-roles role))
      context)))

(p/defnk ^:command login
  [[:state users]
   [:data username :- s/Str]]
  (let [users-by-name (into {} (map (juxt :name identity) (vals @users)))]
    (if-let [user (get users-by-name username)]
      (-> (success {:status :ok})
          (assoc-in [:session :identity] (:user-id user)))
      (failure {:status :no-user}))))

(p/defnk ^:command logout
  []
  (assoc-in (success) [:session] nil))

(p/defnk ^:query who-am-i
  {:interceptors [[require-session :error] load-current-user]
   :responses {:default {:schema users/User}}}
  [[:entities current-user]]
  (success current-user))
