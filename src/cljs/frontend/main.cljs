(ns frontend.main
  (:require [reagent.core :as r]
            [frontend.app :as app]
            [reagent-dev-tools.core :as dev]
            [devtools.core :as devtools]
            [metosin.dates :as dates]))

(devtools/enable-feature! :sanity-hints)
(devtools/install!)

(defn date-str [x]
  (dates/format x {:pattern "d.M.yyyy HH:mm"}))

(def authority? #{:authority})
(def applicant? #{:applicant})

(defmulti render-view :routing/id)

(defn login-view []
  (let [username (r/atom "")]
    (fn []
      [:div.col-md-4.col-md-offset-4
       [:h1 "Hello, please log in"]
       [:form
        {:on-submit (fn [e]
                      (.preventDefault e)
                      (app/login! @username))}
        [:div.form-group
         [:input.form-control
          {:value @username
           :placeholder "username"
           :on-change #(reset! username (.. % -target -value))}]]
        [:button.btn.btn-primary.btn-block
         {:type "submit"}
         "Login"]]])))

(defn permits-view []
  (let [role @(r/track app/current-role)
        permits (vals @(r/track app/permits))]
    (js/console.log permits)
    [:div
     [:h1 "Your building permits"
      [:div.btn-group.pull-right
       (if (applicant? role)
         [:button.btn.btn-success
          {:type "button"
           :on-click #(app/navigate! :new-permit nil)}
          "New permit"])]]
     [:table.table.table-bordered
      [:thead
       [:tr
        [:th "Title"]
        (if (authority? role)
          [:th "Applicant"])
        (if (applicant? role)
          [:th "Handler"]
          [:th "Claimed"])
        [:th "Created"]]]
      [:tbody
       (for [{:keys [permit-id title applicant-id applicant authority-id authority created] :as permit} permits]
         [:tr
          {:key permit-id}
          [:td
           [:a {:on-click #(app/navigate! :permit {:id permit-id})
                :href "#"}
            title]]
          (if (authority? role)
            [:td (:name applicant)])
          (if (applicant? role)
            [:td (if authority-id
                   (:name authority)
                   "Not assigned yet")]
            [:td (if-not authority-id
                   [:button.btn.btn-success.btn-block
                    {:type "button"
                     :on-click #(app/claim-permit! permit-id)}
                    "Claim"]
                   "Claimed")])
          [:td (date-str created)]])]]]))

(defn new-permit-view []
  (let [data (r/atom {:title ""})]
    (fn []
      [:div
       [:h1 "New permit"]
       [:form
        {:on-submit (fn [e]
                      (.preventDefault e)
                      (app/create-permit! @data))}
        [:div.form-group
         [:label "Title"]
         [:input.form-control
          {:value (:title @data)
           :on-change #(swap! data assoc :title (.. % -target -value))}]]

        [:div.btn-group
         [:button.btn.btn-success
          {:type "submit"}
          "Create"]]]])))

(defn permit-view [_]
  (let [comment-text (r/atom "")]
    (fn [{:keys [id]}]
      (let [{:keys [title applicant authority created permit-id comments]}
            @(r/track app/permit-by-id id)]
        [:div
         [:h1 "Permit: " title]

         [:div.row
          [:dl.col-sm-4
           [:dt "Applicant"]
           [:dd (:name applicant)]

           [:dt "Authority person"]
           [:dd (:name authority)]

           [:dt "Created"]
           [:dd (date-str created)]]]

         [:h2 "Available actions"]

         [:h2 "Comments"]

         [:form
          {:on-submit (fn [e]
                        (.preventDefault e)
                        (app/add-comment {:id permit-id
                                          :text @comment-text}))}
          [:div.form-group
           [:textarea.form-control
            {:value @comment-text
             :on-change #(reset! comment-text (.. % -target -value))}]]
          [:button.btn.btn-primary.btn-block
           {:type "submit"}
           "Comment"]]

         (for [{:keys [sent text]} comments]
           [:div
            [:p text]
            [:span "Sent at " (str sent)]])]))))

(defmethod render-view :default [_]
  [:h1 "Unknown view"])

(defmethod render-view :permits [_]
  [permits-view])

(defmethod render-view :new-permit [_]
  [new-permit-view])

(defmethod render-view :permit [{:keys [id]}]
  [permit-view {:id id}])

(defn main-view []
  (let [view @(r/track app/view)
        session @(r/track app/session)]
    [:div
     [:nav.navbar.navbar-static-top.navbar-default
      [:div.container
       [:div.navbar-header
        [:a.navbar-brand
         {:href "#"
          :on-click  #(app/navigate! :permits)}
         "Building Permit Application"]]
       [:ul.nav.navbar-nav
        [:li
         [:a {:on-click #(app/navigate! :permits)
              :href "#"}
          "My permits"]]]
       (if (:user session)
         [:ul.nav.navbar-nav.navbar-right
          [:li
           [:a
            {:href "#"
             :on-click #(app/logout!)}
            "Logged in as " [:strong (:name (:user session))] ", log out"]]])]]
     [:div.container
      (let [session-status (:status session)]
        (if (= session-status :logged)
          (render-view view)
          (case session-status
            :not-logged [login-view]
            [:div "wat..."])))]]))

(defn init! []
  (app/load-session!)
  (r/render [main-view] (js/document.getElementById "app"))
  (if-let [el (js/document.getElementById "dev")]
    (r/render [dev/dev-tool {}] el)))

(init!)
