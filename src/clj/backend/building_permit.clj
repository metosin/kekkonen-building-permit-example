(ns backend.building-permit
  (:require [plumbing.core :as p]
            [kekkonen.cqrs :refer :all]
            [schema.core :as s]
            [schema-tools.core :as st]
            [backend.chord :as chord]))

(s/defschema Roles (s/enum :applicant :authority :admin))

(s/defschema States (s/enum :draft :open :submitted :approved :rejected))

(s/defschema Comment
  {:user s/Int
   :sent s/Inst
   :text s/Str})

(s/defschema BuildingPermit
  {:permit-id s/Int
   :state States
   :title s/Str
   :applicant-id s/Int
   :created s/Inst
   :authority-id (s/maybe s/Int)
   :comments [Comment]})

(s/defschema NewBuildingPermit
  (st/select-keys BuildingPermit [:title]))

(defn new-id [entities]
  (if-let [ids (seq (keys entities))]
    (inc (apply max ids))
    1))

(defn has-permission? [permit user]
  (let [{:keys [applicant-id authority-id]} permit
        {:keys [user-id role]} user]
    (case role
      :applicant (= user-id applicant-id)
      :authority true
      :admin true
      false)))

(defn is-interesting? [permit user]
  (let [{:keys [applicant-id authority-id]} permit
        {:keys [user-id role]} user]
    (case role
      :applicant (= user-id applicant-id)
      :authority (or (= user-id authority-id) (nil? authority-id))
      :admin true
      false)))

(defn enrich [state permit]
  (assoc permit
         :authority (get @(:users state) (:authority-id permit))
         :applicant (get @(:users state) (:applicant-id permit))))

(defn retrieve-permit [_]
  (p/fnk [[:state permits :as state]
          [:entities current-user]
          [:data permit-id :- s/Int]
          :as context]
    (if-let [permit (get @permits permit-id)]
      (if (has-permission? permit current-user)
        (assoc-in context [:entities :permit] permit)
        (failure! {:error :unauthorized}))
      (failure! {:error :no-permit}))))

(defn requires-state [allowed-states]
  (p/fnk [[:entities [:permit state]] :as context]
    (if (allowed-states state)
      context
      (failure! {:error :bad-request}))))

(defn requires-claim [v]
  (p/fnk [[:entities
           [:permit authority-id]
           [:current-user user-id]]
          :as context]
    (if (case v
          true (= authority-id user-id)
          :no (nil? authority-id))
      context
      (failure! {:error :unauthorized}))))

(defn update-state [chord permits {:keys [permit-id]} state]
  (swap! permits assoc-in [permit-id :state] state)
  ;; FIXME: Check permissions here?
  (chord/broadcast chord {:permit-id permit-id})
  {:status :ok})

(p/defnk ^:query get-permit
  "Retrieve a single building permit"
  {::retrieve-permit true
   :responses {:default {:schema BuildingPermit}}}
  [[:entities permit]
   state]
  (success (enrich state permit)))

(p/defnk ^:query my-permits
  "List building permits you have access to"
  {:responses {:default {:schema [BuildingPermit]}}}
  [[:state permits :as state]
   [:entities current-user]]
  (success (->> @permits
                vals
                (filter #(is-interesting? % current-user))
                (map #(enrich state %))
                vec)))

(p/defnk ^:command create-permit
  "Create building permit application"
  {:requires-role #{:applicant}}
  [[:state permits]
   [:entities current-user]
   chord
   data :- NewBuildingPermit]
  (let [permit-id (new-id @permits)
        permit (-> data
                   (assoc :state :draft
                          :permit-id permit-id
                          :applicant-id (:user-id current-user)
                          :created (java.util.Date.)
                          :authority-id nil
                          :comments [])
                   (->> (s/validate BuildingPermit)))]
    (chord/broadcast chord {:permit-id permit-id})
    (swap! permits assoc permit-id permit)
    (success {:permit-id permit-id})))

(p/defnk ^:command open
  "Ask authority for help"
  {:requires-role #{:applicant}
   ::retrieve-permit true
   ::requires-state #{:draft}}
  [[:state permits]
   chord
   [:entities permit]]
  (success (update-state chord permits permit :open)))

(p/defnk ^:command submit
  "Submit the permit for official review"
  {:requires-role #{:applicant}
   ::retrieve-permit true
   ::requires-state #{:open :draft}}
  [[:state permits]
   chord
   [:entities permit]]
  (success (update-state chord permits permit :submitted)))

(p/defnk ^:command claim
  "Claim this permit"
  {:requires-role #{:authority}
   ::requires-claim :no
   ::retrieve-permit true}
  [[:state permits]
   chord
   [:entities
    [:permit permit-id]
    [:current-user user-id]]]
  (swap! permits assoc-in [permit-id :authority-id] user-id)
  (chord/broadcast chord {:permit-id permit-id})
  (success {:status :ok}))

(p/defnk ^:command return-to-applicant
  "Ask the applicant to fix something"
  {:requires-role #{:authority}
   ::requires-claim true
   ::retrieve-permit true
   ::requires-state #{:submitted}}
  [[:state permits]
   chord
   [:entities permit]]
  (success (update-state chord permits permit :open)))

(p/defnk ^:command approve
  "Approve a permit"
  {:requires-role #{:authority}
   ::requires-claim true
   ::retrieve-permit true
   ::requires-state #{:submitted}}
  [[:state permits]
   chord
   [:entities permit]]
  (success (update-state chord permits permit :approved)))

(p/defnk ^:command reject
  "Reject a permit"
  {:requires-role #{:authority}
   ::requires-claim true
   ::retrieve-permit true
   ::requires-state #{:submitted}}
  [[:state permits]
   chord
   [:entities permit]]
  (success (update-state chord permits permit :rejected)))

(p/defnk ^:command add-comment
  "Add a comment to permit"
  {::retrieve-permit true
   ::requires-state (complement #{:approved :rejected})
   :requires-role #{:applicant :authority}}
  [[:data text :- s/Str]
   [:state permits]
   chord
   [:entities
    [:permit permit-id]
    [:current-user user-id]]]
  (let [new-comment {:user-id user-id
                     :sent (java.util.Date.)
                     :text text}]
    (swap! permits update-in [permit-id :comments] (fnil conj []) new-comment)
    (chord/broadcast chord {:permit-id permit-id})
    (success {:status :ok
              :comment new-comment})))
