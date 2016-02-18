(ns backend.users
  (:require [plumbing.core :as p]
            [kekkonen.cqrs :refer :all]
            [schema.core :as s]
            [schema-tools.core :as st]))

(s/defschema User
  {:user-id s/Int
   :name s/Str
   :role s/Keyword})

(p/defnk ^:query list-users
  {:requires-role #{:admin}}
  [[:state users]
   [:entities [:current-user role]]]
  (success (vals @users)))
