(ns backend.building-permit
  (:require [plumbing.core :as p]
            [kekkonen.cqrs :refer :all]
            [schema.core :as s]
            [schema-tools.core :as st]))

(s/defschema Roles
  (s/enum :applicant :authority :admin))

(s/defschema States
  (s/enum :draft :open :submitted :approved :rejected))

(def transitions
  {:draft #{:open :submitted}
   :open #{:submitted}
   :submitted #{:open :approved :rejected}
   :approved #{}
   :rejected #{}})

(s/defschema Comment
  {:user s/Int
   :sent s/Inst
   :text s/Str})

(s/defschema BuildingPermit
  {:id s/Int
   :state States
   :title s/Str
   :applicant s/Str
   :created s/Inst
   :authority (s/maybe s/Str)
   :comments [Comment]})

(s/defschema NewBuildingPermit
  (st/select-keys BuildingPermit [:title]))

(defn new-id [entities]
  (if-let [ids (seq (keys entities))]
    (inc (apply max ids))
    1))

(defn has-permission? [permit user]
  (let [{:keys [applicant authority]} permit
        {:keys [id role]} user]
    (case role
      :applicant (= id applicant)
      :authority (or (= id authority) (nil? authority))
      :admin true
      false)))

(defn retrieve-permit [_]
  (p/fnk [[:state permits]
          [:entities current-user]
          [:data id :- s/Int]
          :as m]
    (if-let [permit (get @permits id)]
      (if (has-permission? permit current-user)
        (assoc-in m [:entities :permit] permit)
        (failure! {:error :unauthorized}))
      (failure! {:error :no-permit}))))

(p/defnk ^:query get-permit
  "Retrieve a single building permit"
  {:load-current-user true
   ::retrieve-permit true
   :responses {:default {:schema BuildingPermit}}}
  [[:entities permit]]
  (success permit))

(p/defnk ^:query list-permits
  "List building permits you have access to"
  {:responses {:default {:schema [BuildingPermit]}}
   :load-current-user true}
  [[:state permits]
   [:entities current-user]]
  (success (->> @permits
                vals
                (filter #(has-permission? % current-user))
                vec)))

(p/defnk ^:command create-permit
  "Create building permit application"
  {:load-current-user true
   :requires-role #{:applicant}}
  [[:state permits]
   [:entities current-user]
   data :- NewBuildingPermit]
  (let [{:keys [id] :as permit}
        (assoc data
               :state :draft
               :id (new-id @permits)
               :applicant (:id current-user)
               :created (java.util.Date.))]
    (swap! permits assoc id permit)
    (success {:id id})))

(defn update-state [permits {:keys [id]} state]
  (swap! permits update-in [id :state] state)
  {:status :ok})

(p/defnk ^:command open
  "Ask authority for help"
  {::retrieve-permit true
   :requires-role #{:applicant}
   ::requires-state #{:draft}}
  [[:state permits]
   [:entities permit]]
  (success (update-state permits permit :open)))

(p/defnk ^:command submit
  "Submit the permit for official review"
  {:requires-role #{:applicant}
   ::requires-state #{:open :draft}
   ::retrieve-permit true}
  [[:state permits]
   [:entities permit]]
  (success (update-state permits permit :submitted)))

(p/defnk ^:command claim
  "Claim this permit"
  {:requires-role #{:authority}
   ::retrieve-permit true}
  [[:state permits]
   [:entities permit]]
  (success))

(p/defnk ^:command return-to-applicant
  "Ask the applicant to fix something"
  {:requires-role #{:authority}
   ::requires-state #{:submitted}
   ::retrieve-permit true}
  [[:state permits]
   [:entities permit]]
  (success (update-state permits permit :open)))

(p/defnk ^:command approve
  "Approve a permit"
  {:requires-role #{:authority}
   ::requires-state #{:open}
   ::retrieve-permit true}
  [[:state permits]
   [:entities permit]]
  (success (update-state permits permit :approved)))

(p/defnk ^:command reject
  "Reject a permit"
  {:requires-role #{:authority}
   ::requires-state #{:open}
   ::retrieve-permit true}
  [[:state permits]
   [:entities permit]]
  (success (update-state permits permit :rejected)))

(p/defnk ^:command add-comment
  "Add a comment to permit"
  {:load-current-user true
   ::retrieve-permit true
   :requires-role #{:applicant :authority}}
  [[:data text :- s/Str]
   [:state permits]
   [:entities
    [:permit id]
    [:current-user id]]]
  (let [comment {:user id
                 :sent (java.util.Date.)
                 :text text}]
    (swap! permits update-in [id :comments] (fnil conj []) comment)
    (success {:status :ok
              :comment comment})))

(p/defnk ^:command modify
  "Modify basic data of permit"
  {:requires-role #{:authority}}
  []
  (success))
